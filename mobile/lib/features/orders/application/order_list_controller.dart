import 'package:flutter/foundation.dart';

import '../../../core/errors/api_exception.dart';
import '../data/order_api.dart';
import '../models/order_models.dart';

class OrderListController extends ChangeNotifier {
  final OrderApi _orderApi;

  OrderListController(this._orderApi);

  bool loading = true;
  String? errorMessage;
  List<OrderHistoryEntry> orders = [];

  Future<void> load() async {
    loading = true;
    notifyListeners();
    try {
      final page = await _orderApi.history(size: 30);
      orders = page.content;
      errorMessage = null;
    } on ApiException catch (error) {
      errorMessage = error.message;
    }
    loading = false;
    notifyListeners();
  }
}
