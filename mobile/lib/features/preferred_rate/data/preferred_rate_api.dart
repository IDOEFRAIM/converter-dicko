import '../../../core/network/api_client.dart';
import '../../../shared/models/page_response.dart';
import '../models/preferred_rate_models.dart';

/// Acces a `/api/v1/preferred-rates` (creation/consultation/annulation d'une
/// demande de taux preferentiel). Le declenchement automatique et la
/// progression de l'echange restent exclusivement pilotes par le backend
/// (`PreferredRateScheduler`) — cette API n'expose jamais d'action pour les
/// simuler ou les forcer.
class PreferredRateApi {
  final ApiClient _client;

  PreferredRateApi(this._client);

  Future<PreferredRate> create(CreatePreferredRateRequest request) async {
    final body = await _client.post('/v1/preferred-rates', data: request.toJson());
    return PreferredRate.fromJson(body['data'] as Map<String, dynamic>);
  }

  Future<PageResponse<PreferredRate>> listMine({int page = 0, int size = 50}) async {
    final body = await _client.get('/v1/preferred-rates', queryParameters: {'page': page, 'size': size});
    return PageResponse.fromJson(body['data'] as Map<String, dynamic>, PreferredRate.fromJson);
  }

  Future<PreferredRate> get(String id) async {
    final body = await _client.get('/v1/preferred-rates/$id');
    return PreferredRate.fromJson(body['data'] as Map<String, dynamic>);
  }

  Future<PreferredRate> cancel(String id) async {
    final body = await _client.post('/v1/preferred-rates/$id/cancel');
    return PreferredRate.fromJson(body['data'] as Map<String, dynamic>);
  }
}
