import 'dart:io';

import 'package:dio/dio.dart';

import '../../../core/network/api_client.dart';
import '../../../shared/utils/image_content_type.dart';
import '../models/payment_models.dart';

/// Acces a `/api/v1/orders/{orderId}/payments` (declaration) et
/// `/api/v1/payments/{id}/proofs` (preuve) — mission section 26.
class PaymentApi {
  final ApiClient _client;

  PaymentApi(this._client);

  Future<Payment> submit(
    String orderId,
    SubmitPaymentRequest request, {
    String? idempotencyKey,
  }) async {
    final body = await _client.post(
      '/v1/orders/$orderId/payments',
      data: request.toJson(),
      extraHeaders: idempotencyKey == null ? null : {'Idempotency-Key': idempotencyKey},
    );
    return Payment.fromJson(body['data'] as Map<String, dynamic>);
  }

  Future<PaymentProof> uploadProof(String paymentId, String filePath, String fileName) async {
    // Content-Type devine des OCTETS reels, jamais du nom de fichier -- meme
    // discipline que SupplierApi.uploadQrCode (voir sniffImageContentType).
    final bytes = await File(filePath).readAsBytes();
    final formData = FormData.fromMap({
      'file': MultipartFile.fromBytes(bytes, filename: fileName, contentType: sniffImageContentType(bytes)),
    });
    final body = await _client.postMultipart('/v1/payments/$paymentId/proofs', formData);
    return PaymentProof.fromJson(body['data'] as Map<String, dynamic>);
  }
}
