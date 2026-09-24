import '../../../core/network/api_client.dart';
import '../models/achievement_models.dart';

/// Acces a `GET /api/v1/me/achievements` — jamais reserve a un sous-ensemble
/// de comptes (contrairement au reporting Business) : tout compte
/// authentifie y a acces, memes gains nuls.
class AchievementApi {
  final ApiClient _client;

  AchievementApi(this._client);

  Future<AchievementSummary> summary() async {
    final body = await _client.get('/v1/me/achievements');
    return AchievementSummary.fromJson(body['data'] as Map<String, dynamic>);
  }
}
