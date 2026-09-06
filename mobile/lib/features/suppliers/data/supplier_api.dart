import '../../../core/network/api_client.dart';
import '../../../shared/models/page_response.dart';
import '../../orders/models/order_models.dart';
import '../models/supplier_models.dart';

/// Acces a `/api/v1/suppliers/**` — carnet de fournisseurs/beneficiaires
/// reutilisables et "payer a nouveau" (mission section 21/22).
class SupplierApi {
  final ApiClient _client;

  SupplierApi(this._client);

  Future<PageResponse<SupplierSummary>> list({int page = 0, int size = 20, String? status}) async {
    final query = <String, dynamic>{'page': page, 'size': size};
    if (status != null) {
      query['status'] = status;
    }
    final body = await _client.get('/v1/suppliers', queryParameters: query);
    return PageResponse.fromJson(body['data'] as Map<String, dynamic>, SupplierSummary.fromJson);
  }

  Future<PageResponse<SupplierSummary>> listFavorites({int page = 0, int size = 20}) async {
    final body = await _client.get('/v1/suppliers/favorites', queryParameters: {'page': page, 'size': size});
    return PageResponse.fromJson(body['data'] as Map<String, dynamic>, SupplierSummary.fromJson);
  }

  Future<SupplierDetail> get(String id) async {
    final body = await _client.get('/v1/suppliers/$id');
    return SupplierDetail.fromJson(body['data'] as Map<String, dynamic>);
  }

  Future<SupplierDetail> create(SupplierRequest request) async {
    final body = await _client.post('/v1/suppliers', data: request.toJson());
    return SupplierDetail.fromJson(body['data'] as Map<String, dynamic>);
  }

  Future<SupplierDetail> update(String id, SupplierRequest request) async {
    final body = await _client.put('/v1/suppliers/$id', data: request.toJson());
    return SupplierDetail.fromJson(body['data'] as Map<String, dynamic>);
  }

  Future<SupplierDetail> setFavorite(String id, bool favorite) async {
    final action = favorite ? 'favorite' : 'unfavorite';
    final body = await _client.post('/v1/suppliers/$id/$action');
    return SupplierDetail.fromJson(body['data'] as Map<String, dynamic>);
  }

  /// Desactivation logique uniquement — jamais une suppression reelle, les
  /// ordres deja crees avec ce fournisseur ne sont jamais affectes.
  Future<SupplierDetail> deactivate(String id) async {
    final body = await _client.post('/v1/suppliers/$id/deactivate');
    return SupplierDetail.fromJson(body['data'] as Map<String, dynamic>);
  }

  /// Cree un NOUVEAU devis (pricing courant) puis un NOUVEL ordre — ne clone
  /// jamais un ordre precedent (mission section 22).
  Future<OrderDetail> payAgain(String id, PayAgainRequest request, {String? idempotencyKey}) async {
    final body = await _client.post(
      '/v1/suppliers/$id/pay-again',
      data: request.toJson(),
      extraHeaders: idempotencyKey == null ? null : {'Idempotency-Key': idempotencyKey},
    );
    return OrderDetail.fromJson(body['data'] as Map<String, dynamic>);
  }
}
