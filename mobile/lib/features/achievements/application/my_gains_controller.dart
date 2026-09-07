import 'package:flutter/foundation.dart';

import '../../../core/errors/api_exception.dart';
import '../../orders/data/order_api.dart';
import '../../orders/models/order_models.dart';
import '../data/achievement_api.dart';
import '../models/achievement_models.dart';

/// Etat de "Mes gains" : le resume (volume/badge/XP) et la liste des
/// transferts termines, deux sources independantes — l'echec de l'une
/// n'empeche jamais l'affichage de l'autre (meme principe que HomeController,
/// mission section 38).
class MyGainsController extends ChangeNotifier {
  final AchievementApi _achievementApi;
  final OrderApi _orderApi;

  MyGainsController({required AchievementApi achievementApi, required OrderApi orderApi})
      : _achievementApi = achievementApi,
        _orderApi = orderApi;

  bool loadingSummary = true;
  AchievementSummary? summary;
  String? summaryErrorMessage;

  bool loadingHistory = true;
  List<OrderHistoryEntry> completedTransfers = const [];
  String? historyErrorMessage;

  Future<void> load() => Future.wait([loadSummary(), loadHistory()]);

  Future<void> loadSummary() async {
    loadingSummary = true;
    notifyListeners();
    try {
      summary = await _achievementApi.summary();
      summaryErrorMessage = null;
    } on ApiException catch (error) {
      summaryErrorMessage = error.message;
    }
    loadingSummary = false;
    notifyListeners();
  }

  Future<void> loadHistory() async {
    loadingHistory = true;
    notifyListeners();
    try {
      final page = await _orderApi.history(status: 'COMPLETED', size: 20);
      completedTransfers = page.content;
      historyErrorMessage = null;
    } on ApiException catch (error) {
      historyErrorMessage = error.message;
    }
    loadingHistory = false;
    notifyListeners();
  }
}
