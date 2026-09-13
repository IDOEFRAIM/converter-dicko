import '../../../core/network/api_client.dart';
import '../models/support_models.dart';

/// Acces a `/api/v1/support/**` -- mon fil de messagerie uniquement.
class SupportApi {
  final ApiClient _client;

  SupportApi(this._client);

  Future<SupportThread> myThread() async {
    final body = await _client.get('/v1/support/thread');
    return SupportThread.fromJson(body['data'] as Map<String, dynamic>);
  }

  Future<SupportMessage> send(String messageBody) async {
    final body = await _client.post('/v1/support/messages', data: {'body': messageBody});
    return SupportMessage.fromJson(body['data'] as Map<String, dynamic>);
  }
}
