import '../../../core/network/api_client.dart';
import '../../../shared/models/page_response.dart';
import '../models/rate_models.dart';

/// Acces a `GET /api/v1/rates/history` — historique public du taux client.
/// Aucune authentification requise cote backend, mais l'app envoie tout de
/// meme le jeton s'il existe (sans consequence, l'endpoint l'ignore).
class RateHistoryApi {
  final ApiClient _client;

  RateHistoryApi(this._client);

  Future<PageResponse<PublicRateHistoryEntry>> history({int page = 0, int size = 30}) async {
    final body = await _client.get('/v1/rates/history', queryParameters: {'page': page, 'size': size});
    return PageResponse.fromJson(body['data'] as Map<String, dynamic>, PublicRateHistoryEntry.fromJson);
  }
}
