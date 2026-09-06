import 'package:flutter/foundation.dart';

import '../../../core/errors/api_exception.dart';
import '../../orders/data/order_api.dart';
import '../../orders/models/order_models.dart';
import '../data/business_api.dart';
import '../models/business_models.dart';

/// Etat de l'espace professionnel (mission section 32/33). L'existence d'un
/// [BusinessProfile] EST la distinction Personnel/Professionnel — aucun champ
/// `accountType` independant n'est jamais introduit cote mobile.
class BusinessController extends ChangeNotifier {
  final BusinessApi _businessApi;
  final OrderApi _orderApi;

  BusinessController({required BusinessApi businessApi, required OrderApi orderApi})
      : _businessApi = businessApi,
        _orderApi = orderApi;

  bool loading = true;
  String? errorMessage;
  BusinessProfile? profile;
  BusinessPaymentSummary? summary;
  List<OrderHistoryEntry> recentOrders = const [];

  bool savingProfile = false;
  String? saveErrorMessage;

  Future<void> load() async {
    loading = true;
    notifyListeners();
    try {
      profile = await _businessApi.getProfile();
      errorMessage = null;
      if (profile != null) {
        await _loadSummaryAndActivity();
      }
    } on ApiException catch (error) {
      errorMessage = error.message;
    }
    loading = false;
    notifyListeners();
  }

  Future<void> _loadSummaryAndActivity() async {
    try {
      summary = await _businessApi.paymentsSummary();
    } on ApiException {
      summary = null;
    }
    try {
      final page = await _orderApi.history(size: 10);
      recentOrders = page.content;
    } on ApiException {
      recentOrders = const [];
    }
  }

  Future<bool> saveProfile(UpsertBusinessProfileRequest request) async {
    if (savingProfile) return false;
    savingProfile = true;
    saveErrorMessage = null;
    notifyListeners();
    try {
      profile = await _businessApi.upsertProfile(request);
      savingProfile = false;
      await _loadSummaryAndActivity();
      notifyListeners();
      return true;
    } on ApiException catch (error) {
      saveErrorMessage = error.message;
      savingProfile = false;
      notifyListeners();
      return false;
    }
  }
}
