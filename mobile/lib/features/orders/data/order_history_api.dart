import '../../../core/network/api_client.dart';
import '../../../shared/models/page_response.dart';
import '../models/order_models.dart';

/// Acces a `GET /api/v1/orders/history` — utilise notamment par le Home pour
/// la section "Derniere operation" (une seule page, taille 1).
class OrderHistoryApi {
  final ApiClient _client;

  OrderHistoryApi(this._client);

  Future<PageResponse<OrderHistoryEntry>> history({int page = 0, int size = 20}) async {
    final body = await _client.get('/v1/orders/history', queryParameters: {'page': page, 'size': size});
    return PageResponse.fromJson(body['data'] as Map<String, dynamic>, OrderHistoryEntry.fromJson);
  }
}
