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
      'front': await MultipartFile.fromFile(frontPath, filename: 'front.jpg'),
      if (backPath != null) 'back': await MultipartFile.fromFile(backPath, filename: 'back.jpg'),
      'selfie': await MultipartFile.fromFile(selfiePath, filename: 'selfie.jpg'),
    });
    final body = await _client.postMultipart('/v1/kyc/submissions', form);
    return KycSubmission.fromJson(body['data'] as Map<String, dynamic>);
  }
}
