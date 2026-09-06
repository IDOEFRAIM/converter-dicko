import 'package:flutter/foundation.dart';

import '../../../core/errors/api_exception.dart';
import '../data/supplier_api.dart';
import '../models/supplier_models.dart';

class SupplierDetailController extends ChangeNotifier {
  final SupplierApi _supplierApi;
  final String supplierId;

  SupplierDetailController({required SupplierApi supplierApi, required this.supplierId}) : _supplierApi = supplierApi;

  bool loading = true;
  String? errorMessage;
  SupplierDetail? supplier;
  bool actionInProgress = false;

  Future<void> load() async {
    loading = true;
    notifyListeners();
    try {
      supplier = await _supplierApi.get(supplierId);
      errorMessage = null;
    } on ApiException catch (error) {
      errorMessage = error.message;
    }
    loading = false;
    notifyListeners();
  }

  Future<void> toggleFavorite() async {
    final current = supplier;
    if (current == null || actionInProgress) return;
    actionInProgress = true;
    notifyListeners();
    try {
      supplier = await _supplierApi.setFavorite(supplierId, !current.favorite);
    } on ApiException catch (error) {
      errorMessage = error.message;
    }
    actionInProgress = false;
    notifyListeners();
  }

  Future<bool> deactivate() async {
    if (actionInProgress) return false;
    actionInProgress = true;
    notifyListeners();
    try {
      supplier = await _supplierApi.deactivate(supplierId);
      actionInProgress = false;
      notifyListeners();
      return true;
    } on ApiException catch (error) {
      errorMessage = error.message;
      actionInProgress = false;
      notifyListeners();
      return false;
    }
  }
}
