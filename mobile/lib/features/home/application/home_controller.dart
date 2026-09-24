import 'package:flutter/foundation.dart';

import '../../../core/errors/api_exception.dart';
import '../../../shared/utils/async_value.dart';
import '../../achievements/data/achievement_api.dart';
import '../../achievements/models/achievement_models.dart';
import '../../orders/data/order_api.dart';
import '../../orders/models/order_models.dart';
import '../../rates/data/rate_history_api.dart';
import '../../rates/models/rate_models.dart';
import '../../suppliers/data/supplier_api.dart';
import '../../suppliers/models/supplier_models.dart';

/// Etat de l'ecran Home : quatre sources independantes (taux, derniere
/// operation, fournisseurs, gains) — l'echec de l'une n'empeche jamais
/// l'affichage des autres (mission section 18/38 : jamais un ecran vide a
/// cause d'un seul appel en echec).
class HomeController extends ChangeNotifier {
  final RateHistoryApi _rateHistoryApi;
  final OrderApi _orderApi;
  final SupplierApi _supplierApi;
  final AchievementApi _achievementApi;

  HomeController({
    required RateHistoryApi rateHistoryApi,
    required OrderApi orderApi,
    required SupplierApi supplierApi,
    required AchievementApi achievementApi,
  })  : _rateHistoryApi = rateHistoryApi,
        _orderApi = orderApi,
        _supplierApi = supplierApi,
        _achievementApi = achievementApi;

  AsyncValue<PublicRateHistoryEntry?> latestRate = const AsyncValue.loading();
  AsyncValue<OrderHistoryEntry?> lastOperation = const AsyncValue.loading();
  AsyncValue<List<SupplierSummary>> suppliers = const AsyncValue.loading();
  AsyncValue<AchievementSummary> achievements = const AsyncValue.loading();

  Future<void> loadAll() {
    return Future.wait([loadRate(), loadLastOperation(), loadSuppliers(), loadAchievements()]);
  }

  Future<void> loadAchievements() async {
    achievements = const AsyncValue.loading();
    notifyListeners();
    try {
      achievements = AsyncValue.data(await _achievementApi.summary());
    } on ApiException catch (error) {
      achievements = AsyncValue.error(error.message);
    }
    notifyListeners();
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
      final page = await _orderApi.history(size: 1);
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
