import '../../../core/network/api_client.dart';
import '../../../shared/models/page_response.dart';
import '../models/notification_models.dart';

/// Acces a `/api/v1/notifications/**` — mission section 31.
class NotificationApi {
  final ApiClient _client;

  NotificationApi(this._client);

  Future<PageResponse<AppNotification>> list({int page = 0, int size = 30}) async {
    final body = await _client.get('/v1/notifications', queryParameters: {'page': page, 'size': size});
    return PageResponse.fromJson(body['data'] as Map<String, dynamic>, AppNotification.fromJson);
  }

  Future<int> unreadCount() async {
    final body = await _client.get('/v1/notifications/unread-count');
    final data = body['data'] as Map<String, dynamic>;
    return data['unreadCount'] as int? ?? 0;
  }

  Future<AppNotification> markRead(String id) async {
    final body = await _client.post('/v1/notifications/$id/read');
    return AppNotification.fromJson(body['data'] as Map<String, dynamic>);
  }
}
