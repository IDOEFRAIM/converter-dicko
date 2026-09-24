import 'dart:io';

import 'package:dio/dio.dart';

import '../../../core/network/api_client.dart';
import '../../../shared/utils/image_content_type.dart';
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
      'front': await _part(frontPath, 'front'),
      if (backPath != null) 'back': await _part(backPath, 'back'),
      'selfie': await _part(selfiePath, 'selfie'),
    });
    final body = await _client.postMultipart('/v1/kyc/submissions', form);
    return KycSubmission.fromJson(body['data'] as Map<String, dynamic>);
  }

  /// Content-Type devine des OCTETS reels, jamais du nom de fichier -- meme
  /// discipline que SupplierApi.uploadQrCode (voir sniffImageContentType).
  /// Remplace l'ancienne approche "conserver l'extension d'origine", fragile
  /// des que `image_picker` reencode l'image sans renommer le fichier.
  static Future<MultipartFile> _part(String path, String prefix) async {
    final bytes = await File(path).readAsBytes();
    final contentType = sniffImageContentType(bytes);
    final ext = contentType?.subtype == 'png' ? '.png' : '.jpg';
    return MultipartFile.fromBytes(bytes, filename: '$prefix$ext', contentType: contentType);
  }
}
