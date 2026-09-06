import 'package:dio/dio.dart';

import '../../../core/network/api_client.dart';
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
    final formData = FormData.fromMap({'file': await MultipartFile.fromFile(filePath, filename: fileName)});
    final body = await _client.postMultipart('/v1/payments/$paymentId/proofs', formData);
    return PaymentProof.fromJson(body['data'] as Map<String, dynamic>);
  }
}
