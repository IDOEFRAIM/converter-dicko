import 'package:flutter/foundation.dart';

import '../../../core/errors/api_exception.dart';
import '../data/order_api.dart';
import '../models/tracking_models.dart';

class OrderTrackingController extends ChangeNotifier {
  final OrderApi _orderApi;
  final String orderId;

  OrderTrackingController({required OrderApi orderApi, required this.orderId}) : _orderApi = orderApi;

  bool loading = true;
  String? errorMessage;
  OrderTracking? tracking;
  DateTime? lastUpdatedAt;

  Future<void> load() async {
    loading = true;
    notifyListeners();
    try {
      tracking = await _orderApi.tracking(orderId);
      lastUpdatedAt = DateTime.now();
      errorMessage = null;
    } on ApiException catch (error) {
      errorMessage = error.message;
    }
    loading = false;
    notifyListeners();
  }

  /// Vrai uniquement pour le dernier evenement d'un ordre pas encore
  /// termine — jamais un evenement negatif (mission section 25).
  bool isCurrent(TrackingEvent event, int index) {
    final current = tracking;
    if (current == null) return false;
    final isLast = index == current.timeline.length - 1;
    return isLast && !current.isTerminal && !event.code.isNegative;
  }
}
