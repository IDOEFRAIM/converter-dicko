import '../../../core/network/api_client.dart';
import '../../../shared/models/page_response.dart';
import '../models/rate_models.dart';

/// Acces a `GET /api/v1/rates/history` (public) et `/api/v1/rate-alerts`
/// (JWT requis) — meme regroupement que `RateHistoryService` cote Angular.
class RateHistoryApi {
  final ApiClient _client;

  RateHistoryApi(this._client);

  Future<PageResponse<PublicRateHistoryEntry>> history({int page = 0, int size = 30}) async {
    final body = await _client.get('/v1/rates/history', queryParameters: {'page': page, 'size': size});
    return PageResponse.fromJson(body['data'] as Map<String, dynamic>, PublicRateHistoryEntry.fromJson);
  }

  Future<PageResponse<RateAlert>> listAlerts({int page = 0, int size = 50, RateAlertStatus? status}) async {
    final query = <String, dynamic>{'page': page, 'size': size};
    if (status != null) {
      query['status'] = status.code;
    }
    final body = await _client.get('/v1/rate-alerts', queryParameters: query);
    return PageResponse.fromJson(body['data'] as Map<String, dynamic>, RateAlert.fromJson);
  }

  Future<RateAlert> createAlert(CreateRateAlertRequest request) async {
    final body = await _client.post('/v1/rate-alerts', data: request.toJson());
    return RateAlert.fromJson(body['data'] as Map<String, dynamic>);
  }

  Future<RateAlert> cancelAlert(String id) async {
    final body = await _client.post('/v1/rate-alerts/$id/cancel');
    return RateAlert.fromJson(body['data'] as Map<String, dynamic>);
  }
}
