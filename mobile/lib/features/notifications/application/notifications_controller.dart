import 'package:flutter/foundation.dart';

import '../../../core/errors/api_exception.dart';
import '../data/notification_api.dart';
import '../models/notification_models.dart';

class NotificationsController extends ChangeNotifier {
  final NotificationApi _api;

  NotificationsController(this._api);

  bool loading = true;
  String? errorMessage;
  List<AppNotification> notifications = const [];

  Future<void> load() async {
    loading = true;
    notifyListeners();
    try {
      final page = await _api.list(size: 50);
      notifications = page.content;
      errorMessage = null;
    } on ApiException catch (error) {
      errorMessage = error.message;
    }
    loading = false;
    notifyListeners();
  }

  Future<void> markRead(AppNotification notification) async {
    if (notification.isRead) return;
    try {
      final updated = await _api.markRead(notification.id);
      notifications = notifications.map((n) => n.id == updated.id ? updated : n).toList(growable: false);
      notifyListeners();
    } on ApiException {
      // Non bloquant : une notification reste simplement non lue si le
      // marquage echoue, l'utilisateur peut reessayer en la rouvrant.
    }
  }
}
