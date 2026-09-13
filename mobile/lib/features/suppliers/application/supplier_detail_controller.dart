import 'package:flutter/foundation.dart';

import '../../../core/errors/api_exception.dart';
import '../../../shared/utils/file_share.dart';
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
  bool openingQrCode = false;

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

  /// Telecharge le code QR reellement enregistre et l'ouvre dans le lecteur
  /// systeme — jamais un simple lien statique, l'image vient du backend a
  /// chaque consultation.
  Future<void> viewQrCode() async {
    if (openingQrCode) return;
    openingQrCode = true;
    errorMessage = null;
    notifyListeners();
    try {
      final download = await _supplierApi.downloadQrCode(supplierId);
      await saveAndOpenBytes(bytes: download.bytes, fileName: 'code-qr-$supplierId.jpg', mimeType: download.contentType);
    } on ApiException catch (error) {
      errorMessage = error.message;
    } catch (_) {
      // Ecriture disque ou ouverture systeme (aucun lecteur, permission
      // refusee...) : le telechargement reseau avait pourtant reussi, jamais
      // laisser ce cas paraitre comme un simple "rien ne s'est passe".
      errorMessage = "Impossible d'ouvrir le code QR. Reessayez.";
    }
    openingQrCode = false;
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
