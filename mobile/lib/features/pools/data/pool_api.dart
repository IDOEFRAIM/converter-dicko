import '../../../core/network/api_client.dart';
import '../../../shared/models/page_response.dart';
import '../models/pool_models.dart';

/// Acces a `/api/v1/pools` — creation, invitation par code court,
/// consultation (ouverte a tout compte authentifie, meme avant de rejoindre),
/// participants, "mes Ruees", annulation. La contribution elle-meme passe par
/// `OrderApi.create` (champ `poolId`), jamais un endpoint de cette classe.
class PoolApi {
  final ApiClient _client;

  PoolApi(this._client);

  Future<Pool> create(CreatePoolRequest request) async {
    final body = await _client.post('/v1/pools', data: request.toJson());
    return Pool.fromJson(body['data'] as Map<String, dynamic>);
  }

  Future<Pool> get(String id) async {
    final body = await _client.get('/v1/pools/$id');
    return Pool.fromJson(body['data'] as Map<String, dynamic>);
  }

  Future<Pool> getByCode(String code) async {
    final body = await _client.get('/v1/pools/by-code/$code');
    return Pool.fromJson(body['data'] as Map<String, dynamic>);
  }

  Future<List<PoolParticipant>> participants(String id) async {
    final body = await _client.get('/v1/pools/$id/participants');
    return (body['data'] as List<dynamic>)
        .map((e) => PoolParticipant.fromJson(e as Map<String, dynamic>))
        .toList(growable: false);
  }

  Future<PageResponse<Pool>> mine({int page = 0, int size = 20}) async {
    final body = await _client.get('/v1/pools/mine', queryParameters: {'page': page, 'size': size});
    return PageResponse.fromJson(body['data'] as Map<String, dynamic>, Pool.fromJson);
  }

  Future<Pool> join(String id) async {
    final body = await _client.post('/v1/pools/$id/join');
    return Pool.fromJson(body['data'] as Map<String, dynamic>);
  }

  Future<Pool> cancel(String id) async {
    final body = await _client.post('/v1/pools/$id/cancel');
    return Pool.fromJson(body['data'] as Map<String, dynamic>);
  }
}
