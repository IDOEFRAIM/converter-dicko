import '../../../core/network/api_client.dart';
import '../../../shared/models/page_response.dart';
import '../models/order_models.dart';
import '../models/tracking_models.dart';

/// Acces a `/api/v1/orders/**` — creation, consultation, annulation, suivi,
/// justificatif. Aucune feature ne doit appeler [ApiClient] directement pour
/// ces chemins (mission section 13).
class OrderApi {
  final ApiClient _client;

  OrderApi(this._client);

  Future<PageResponse<OrderHistoryEntry>> history({
    int page = 0,
    int size = 20,
    String? status,
    String? supplierId,
  }) async {
    final query = <String, dynamic>{'page': page, 'size': size};
    if (status != null) query['status'] = status;
    if (supplierId != null) query['supplierId'] = supplierId;
    final body = await _client.get('/v1/orders/history', queryParameters: query);
    return PageResponse.fromJson(body['data'] as Map<String, dynamic>, OrderHistoryEntry.fromJson);
  }

  Future<OrderFeasibility> checkFeasibility(String quoteId) async {
    final body = await _client.get('/v1/orders/feasibility', queryParameters: {'quoteId': quoteId});
    return OrderFeasibility.fromJson(body['data'] as Map<String, dynamic>);
  }

  /// [idempotencyKey] : un rejeu (double tap, timeout) avec la meme cle et le
  /// meme corps ne cree jamais un second ordre (mission section 23).
  Future<OrderDetail> create(CreateOrderRequest request, {String? idempotencyKey}) async {
    final body = await _client.post(
      '/v1/orders',
      data: request.toJson(),
      extraHeaders: idempotencyKey == null ? null : {'Idempotency-Key': idempotencyKey},
    );
    return OrderDetail.fromJson(body['data'] as Map<String, dynamic>);
  }

  Future<OrderDetail> get(String id) async {
    final body = await _client.get('/v1/orders/$id');
    return OrderDetail.fromJson(body['data'] as Map<String, dynamic>);
  }

  Future<OrderDetail> cancel(String id, String reason) async {
    final body = await _client.post('/v1/orders/$id/cancel', data: {'reason': reason});
    return OrderDetail.fromJson(body['data'] as Map<String, dynamic>);
  }

  Future<OrderTracking> tracking(String id) async {
    final body = await _client.get('/v1/orders/$id/tracking');
    return OrderTracking.fromJson(body['data'] as Map<String, dynamic>);
  }

  /// Justificatif PDF (uniquement pour un ordre COMPLETED) — octets bruts +
  /// type MIME reel, jamais reconstruit cote mobile (mission section 28).
  Future<BinaryDownload> downloadReceipt(String id) {
    return _client.downloadBytes('/v1/orders/$id/receipt');
  }

  /// Facture proforma PDF (remarque produit #3) — disponible quel que soit le
  /// statut, uniquement pour un ordre vers un fournisseur enregistre.
  Future<BinaryDownload> downloadProforma(String id) {
    return _client.downloadBytes('/v1/orders/$id/proforma');
  }
}
