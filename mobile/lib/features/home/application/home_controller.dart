import 'package:flutter/foundation.dart';

import '../../../core/errors/api_exception.dart';
import '../../../shared/utils/async_value.dart';
import '../../orders/data/order_history_api.dart';
import '../../orders/models/order_models.dart';
import '../../rates/data/rate_history_api.dart';
import '../../rates/models/rate_models.dart';
import '../../suppliers/data/supplier_api.dart';
import '../../suppliers/models/supplier_models.dart';

/// Etat de l'ecran Home : trois sources independantes (taux, derniere
/// operation, fournisseurs) — l'echec de l'une n'empeche jamais l'affichage
/// des deux autres (mission section 18/38 : jamais un ecran vide a cause
/// d'un seul appel en echec).
class HomeController extends ChangeNotifier {
  final RateHistoryApi _rateHistoryApi;
  final OrderHistoryApi _orderHistoryApi;
  final SupplierApi _supplierApi;

  HomeController({
    required RateHistoryApi rateHistoryApi,
    required OrderHistoryApi orderHistoryApi,
    required SupplierApi supplierApi,
  })  : _rateHistoryApi = rateHistoryApi,
        _orderHistoryApi = orderHistoryApi,
        _supplierApi = supplierApi;

  AsyncValue<PublicRateHistoryEntry?> latestRate = const AsyncValue.loading();
  AsyncValue<OrderHistoryEntry?> lastOperation = const AsyncValue.loading();
  AsyncValue<List<SupplierSummary>> suppliers = const AsyncValue.loading();

  Future<void> loadAll() {
    return Future.wait([loadRate(), loadLastOperation(), loadSuppliers()]);
  }

  Future<void> loadRate() async {
    latestRate = const AsyncValue.loading();
    notifyListeners();
    try {
      final page = await _rateHistoryApi.history(size: 1);
      latestRate = AsyncValue.data(page.content.isEmpty ? null : page.content.first);
    } on ApiException catch (error) {
      latestRate = AsyncValue.error(error.message);
    }
    notifyListeners();
  }

  Future<void> loadLastOperation() async {
    lastOperation = const AsyncValue.loading();
    notifyListeners();
    try {
      final page = await _orderHistoryApi.history(size: 1);
      lastOperation = AsyncValue.data(page.content.isEmpty ? null : page.content.first);
    } on ApiException catch (error) {
      lastOperation = AsyncValue.error(error.message);
    }
    notifyListeners();
  }

  Future<void> loadSuppliers() async {
    suppliers = const AsyncValue.loading();
    notifyListeners();
    try {
      final page = await _supplierApi.list(size: 5);
      suppliers = AsyncValue.data(page.content);
    } on ApiException catch (error) {
      suppliers = AsyncValue.error(error.message);
    }
    notifyListeners();
  }
}
