import 'package:dio/dio.dart';

import '../../../core/network/api_client.dart';
import '../models/kyc_models.dart';

/// Acces a `/api/v1/kyc/**` (remarque produit #6).
class KycApi {
  final ApiClient _client;

  KycApi(this._client);

  /// Dernier dossier de l'utilisateur, ou `null` si aucun n'a jamais ete soumis.
  Future<KycSubmission?> mySubmission() async {
    final body = await _client.get('/v1/kyc/submissions/me');
    final data = body['data'];
    if (data == null) return null;
    return KycSubmission.fromJson(data as Map<String, dynamic>);
  }

  Future<KycSubmission> submit({
    required KycDocumentType documentType,
    required String frontPath,
    String? backPath,
    required String selfiePath,
  }) async {
    final form = FormData.fromMap({
      'documentType': documentType.code,
      'front': await MultipartFile.fromFile(frontPath, filename: _fileName(frontPath, 'front')),
      if (backPath != null)
        'back': await MultipartFile.fromFile(backPath, filename: _fileName(backPath, 'back')),
      'selfie': await MultipartFile.fromFile(selfiePath, filename: _fileName(selfiePath, 'selfie')),
    });
    final body = await _client.postMultipart('/v1/kyc/submissions', form);
    return KycSubmission.fromJson(body['data'] as Map<String, dynamic>);
  }

  /// Conserve l'extension REELLE du fichier choisi : dio en deduit le
  /// `Content-Type`, que le backend confronte aux magic bytes. Un `.jpg` force
  /// sur un PNG ferait echouer la validation (`InvalidFileException`).
  static String _fileName(String path, String prefix) {
    final slash = path.lastIndexOf(RegExp(r'[/\\]'));
    final dot = path.lastIndexOf('.');
    final ext = dot > slash && dot >= 0 ? path.substring(dot).toLowerCase() : '.jpg';
    return '$prefix$ext';
  }
}
