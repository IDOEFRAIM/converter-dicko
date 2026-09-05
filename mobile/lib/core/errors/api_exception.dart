/// Detail d'une violation de validation, champ par champ — miroir de
/// `ErrorResponse.FieldViolation` cote backend.
class FieldViolation {
  final String field;
  final String message;

  const FieldViolation({required this.field, required this.message});

  factory FieldViolation.fromJson(Map<String, dynamic> json) {
    return FieldViolation(
      field: json['field'] as String? ?? '',
      message: json['message'] as String? ?? '',
    );
  }
}

/// Exception unique pour toute erreur d'appel API — les widgets ne doivent
/// jamais parser un corps de reponse JSON eux-memes (voir mission section 36).
///
/// Miroir de l'enveloppe backend `ErrorResponse` (voir
/// `com.converter.common.api.ErrorResponse`) : `{ code, message, status, ... }`.
/// [code] est la seule valeur stable a tester (ex. `INVALID_PAYMENT_STATE`,
/// `KYC_VERIFICATION_REQUIRED`) — [message] est un texte humain qui peut changer.
class ApiException implements Exception {
  /// Code d'erreur stable renvoye par le backend (ex. `ORDER_NOT_FOUND`), ou
  /// null si l'erreur n'a pas pu etre associee a un code (panne reseau...).
  final String? code;

  /// Message destine a l'utilisateur, deja pret a afficher.
  final String message;

  /// Statut HTTP reel, ou 0 si la requete n'a jamais atteint le serveur
  /// (pas de reseau, DNS, timeout) — jamais un "echec definitif" pour une
  /// action idempotente (voir mission section 27).
  final int statusCode;

  final List<FieldViolation> violations;

  /// Identifiant de correlation avec les journaux serveur (support/debug).
  final String? traceId;

  const ApiException({
    required this.message,
    required this.statusCode,
    this.code,
    this.violations = const [],
    this.traceId,
  });

  /// Vrai si la requete n'a jamais atteint le serveur (panne reseau, DNS,
  /// timeout, tunnel ngrok coupe...) — jamais interprete comme un echec
  /// definitif d'une operation idempotente.
  bool get isNetworkFailure => statusCode == 0;

  bool get isUnauthorized => statusCode == 401;

  bool get isConflict => statusCode == 409;

  bool get isNotFound => statusCode == 404;

  factory ApiException.network() {
    return const ApiException(
      message: 'Connexion au serveur impossible. Verifiez votre reseau.',
      statusCode: 0,
    );
  }

  factory ApiException.unexpected() {
    return const ApiException(
      message: 'Une erreur inattendue est survenue.',
      statusCode: -1,
    );
  }

  factory ApiException.fromResponseBody(int statusCode, dynamic body, {String? fallbackMessage}) {
    if (body is Map<String, dynamic>) {
      final message = body['message'] as String?;
      final violationsJson = body['violations'] as List<dynamic>?;
      return ApiException(
        code: body['code'] as String?,
        message: message ?? fallbackMessage ?? _fallbackFor(statusCode),
        statusCode: statusCode,
        traceId: body['traceId'] as String?,
        violations: violationsJson == null
            ? const []
            : violationsJson
                .whereType<Map<String, dynamic>>()
                .map(FieldViolation.fromJson)
                .toList(growable: false),
      );
    }
    return ApiException(
      message: fallbackMessage ?? _fallbackFor(statusCode),
      statusCode: statusCode,
    );
  }

  static String _fallbackFor(int statusCode) {
    if (statusCode == 404) {
      return 'Ressource introuvable ou inaccessible.';
    }
    if (statusCode >= 500) {
      return 'Une erreur interne est survenue. Reessayez dans quelques instants.';
    }
    return 'Une erreur inattendue est survenue.';
  }

  @override
  String toString() => 'ApiException(code: $code, status: $statusCode, message: $message)';
}
