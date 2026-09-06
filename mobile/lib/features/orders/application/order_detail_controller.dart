import 'package:flutter/foundation.dart';

import '../../../core/errors/api_exception.dart';
import '../../../core/network/api_client.dart';
import '../data/order_api.dart';
import '../models/order_models.dart';

class OrderDetailController extends ChangeNotifier {
  final OrderApi _orderApi;
  final String orderId;

  OrderDetailController({required OrderApi orderApi, required this.orderId}) : _orderApi = orderApi;

  bool loading = true;
  String? errorMessage;
  OrderDetail? order;

  bool cancelling = false;
  bool downloadingReceipt = false;
  BinaryDownload? lastReceipt;

  Future<void> load() async {
    loading = true;
    errorMessage = null;
    notifyListeners();
    try {
      order = await _orderApi.get(orderId);
    } on ApiException catch (error) {
      errorMessage = error.message;
    }
    loading = false;
    notifyListeners();
  }

  Future<bool> cancel(String reason) async {
    final current = order;
    if (current == null || cancelling) return false;
    cancelling = true;
    notifyListeners();
    try {
      order = await _orderApi.cancel(current.id, reason);
      cancelling = false;
      notifyListeners();
      return true;
    } on ApiException catch (error) {
      errorMessage = error.message;
      cancelling = false;
      notifyListeners();
      return false;
    }
  }

  Future<BinaryDownload?> downloadReceipt() async {
    final current = order;
    if (current == null || downloadingReceipt) return null;
    downloadingReceipt = true;
    errorMessage = null;
    notifyListeners();
    try {
      final download = await _orderApi.downloadReceipt(current.id);
      lastReceipt = download;
      downloadingReceipt = false;
      notifyListeners();
      return download;
    } on ApiException catch (error) {
      errorMessage = error.message;
      downloadingReceipt = false;
      notifyListeners();
      return null;
    }
  }
}
