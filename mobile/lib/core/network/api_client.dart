import 'dart:async';
import 'dart:convert';

import 'package:dio/dio.dart';

import '../auth/auth_session.dart';
import '../config/app_config.dart';
import '../errors/api_exception.dart';

/// Couche HTTP centralisee — UNE seule instance Dio pour toute
/// l'application (mission section 13). Aucune feature ne doit instancier
/// son propre client HTTP.
///
/// Responsabilites, miroir des intercepteurs Angular (`auth.interceptor.ts`,
/// `error.interceptor.ts`) :
/// - prefixe toutes les requetes avec [AppConfig.apiBaseUrl] ;
/// - attache l'en-tete `Authorization: Bearer <token>` si une session existe ;
/// - normalise toute erreur en [ApiException] (jamais une `DioException` ou
///   un `Map` JSON brut ne doit atteindre un widget) ;
/// - sur un 401 provenant de l'API (jeton absent/expire/invalide), purge la
///   session locale — le routeur reagit alors de lui-meme (`refreshListenable`)
///   sans que cette couche ne connaisse la navigation.
class ApiClient {
  final Dio _dio;
  final AuthSession _authSession;

  ApiClient({required AppConfig config, required AuthSession authSession})
      : _authSession = authSession,
        _dio = Dio(
          BaseOptions(
            baseUrl: '${config.apiBaseUrl}/api',
            connectTimeout: const Duration(seconds: 15),
            receiveTimeout: const Duration(seconds: 20),
            sendTimeout: const Duration(seconds: 30),
          ),
        ) {
    _dio.interceptors.add(
      InterceptorsWrapper(
        onRequest: (options, handler) {
          final token = _authSession.token;
          if (token != null) {
            options.headers['Authorization'] = 'Bearer $token';
          }
          handler.next(options);
        },
        onError: (error, handler) {
          if (error.response?.statusCode == 401) {
            // Fire-and-forget : la session est purgee, le routeur ecoute
            // AuthSession et redirige vers /login de lui-meme.
            unawaited(_authSession.clear());
          }
          handler.next(error);
        },
      ),
    );
  }

  Future<Map<String, dynamic>> get(
    String path, {
    Map<String, dynamic>? queryParameters,
  }) {
    return _send(() => _dio.get<Map<String, dynamic>>(path, queryParameters: queryParameters));
  }

  Future<Map<String, dynamic>> post(
    String path, {
    Object? data,
    Map<String, String>? extraHeaders,
  }) {
    return _send(
      () => _dio.post<Map<String, dynamic>>(
        path,
        data: data,
        options: extraHeaders == null ? null : Options(headers: extraHeaders),
      ),
    );
  }

  Future<Map<String, dynamic>> put(String path, {Object? data}) {
    return _send(() => _dio.put<Map<String, dynamic>>(path, data: data));
  }

  Future<Map<String, dynamic>> patch(String path, {Object? data}) {
    return _send(() => _dio.patch<Map<String, dynamic>>(path, data: data));
  }

  /// Envoi multipart (preuve de paiement/reglement). [formData] doit deja
  /// contenir un `MultipartFile` sous la cle attendue par le backend (`file`).
  Future<Map<String, dynamic>> postMultipart(String path, FormData formData) {
    return _send(() => _dio.post<Map<String, dynamic>>(path, data: formData));
  }

  /// Telecharge un contenu binaire (PDF de justificatif, preuve image) sous
  /// forme d'octets bruts, avec son Content-Type reel — jamais reconstruit
  /// cote client (mission sections 28/26).
  Future<BinaryDownload> downloadBytes(String path) async {
    try {
      final response = await _dio.get<List<int>>(
        path,
        options: Options(responseType: ResponseType.bytes),
      );
      final contentType = response.headers.value('content-type') ?? 'application/octet-stream';
      return BinaryDownload(bytes: response.data ?? const [], contentType: contentType);
    } on DioException catch (error) {
      // Un telechargement demande `responseType: bytes` : le corps d'erreur JSON
      // ({code, message}) arrive donc en octets et `_toApiException` ne saurait
      // pas le lire — on le decode ici pour remonter le VRAI message serveur.
      final status = error.response?.statusCode ?? -1;
      final decoded = _tryDecodeErrorBody(error.response?.data);
      if (decoded != null && decoded['code'] != null) {
        // Erreur metier structuree du backend (ex. "La facture proforma n'est
        // disponible que pour un paiement fournisseur...", "Ordre introuvable...").
        throw ApiException.fromResponseBody(status, decoded);
      }
      if (status == 404) {
        // 404 sans code metier = l'URL n'a aucun handler : l'endpoint n'existe
        // pas sur le serveur actuel (backend a redeployer).
        throw const ApiException(
          message: "Ce service n'est pas disponible sur le serveur actuel. "
              'Le backend doit etre mis a jour.',
          statusCode: 404,
        );
      }
      throw _toApiException(error);
    }
  }

  static Map<String, dynamic>? _tryDecodeErrorBody(Object? data) {
    if (data == null) return null;
    if (data is Map<String, dynamic>) return data;
    try {
      final raw = data is List<int> ? utf8.decode(data) : (data is String ? data : null);
      if (raw == null || raw.isEmpty) return null;
      final parsed = jsonDecode(raw);
      return parsed is Map<String, dynamic> ? parsed : null;
    } catch (_) {
      return null;
    }
  }

  Future<Map<String, dynamic>> _send(Future<Response<Map<String, dynamic>>> Function() request) async {
    try {
      final response = await request();
      return response.data ?? const {};
    } on DioException catch (error) {
      throw _toApiException(error);
    }
  }

  ApiException _toApiException(DioException error) {
    switch (error.type) {
      case DioExceptionType.cancel:
        return ApiException.unexpected();
      case DioExceptionType.badResponse:
        final statusCode = error.response?.statusCode ?? -1;
        final body = error.response?.data;
        final map = _tryDecodeErrorBody(body);
        if (statusCode == 404 && (map == null || map['code'] == null)) {
          // 404 sans code metier = aucun handler pour cette URL : l'endpoint
          // n'existe pas sur le serveur actuel (backend a redeployer).
          return const ApiException(
            message: "Ce service n'est pas disponible sur le serveur actuel. "
                'Le backend doit etre mis a jour.',
            statusCode: 404,
          );
        }
        return ApiException.fromResponseBody(statusCode, body);
      case DioExceptionType.connectionTimeout:
      case DioExceptionType.sendTimeout:
      case DioExceptionType.receiveTimeout:
      case DioExceptionType.connectionError:
      case DioExceptionType.badCertificate:
      case DioExceptionType.unknown:
      default:
        // Toute autre variante (timeout de transformation, panne DNS, socket
        // ferme, tunnel ngrok coupe, ou une valeur future ajoutee au paquet
        // dio) : jamais un echec definitif d'une operation idempotente, voir
        // mission section 27 — traite comme un statut reseau "0".
        return ApiException.network();
    }
  }
}

/// Contenu binaire telecharge avec son type MIME reel.
class BinaryDownload {
  final List<int> bytes;
  final String contentType;

  const BinaryDownload({required this.bytes, required this.contentType});
}
