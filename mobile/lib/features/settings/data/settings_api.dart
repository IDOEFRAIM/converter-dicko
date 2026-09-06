import '../../../core/network/api_client.dart';
import '../models/public_settings.dart';

/// `GET /api/settings/public` — mis en cache pour toute la session (un seul
/// appel reseau, les valeurs changent rarement), miroir du `shareReplay`
/// Angular.
class SettingsApi {
  final ApiClient _client;
  Future<PublicSettings>? _cached;

  SettingsApi(this._client);

  Future<PublicSettings> publicSettings() {
    return _cached ??= _fetch();
  }

  Future<PublicSettings> _fetch() async {
    final body = await _client.get('/settings/public');
    return PublicSettings.fromJson(body['data'] as Map<String, dynamic>);
  }
}
