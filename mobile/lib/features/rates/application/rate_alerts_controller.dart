import 'package:flutter/foundation.dart';

import '../../../core/errors/api_exception.dart';
import '../data/rate_history_api.dart';
import '../models/rate_models.dart';

/// Etat de l'ecran "Mes alertes" (mission section 30) : liste (actives vs
/// terminees), creation, annulation. L'ecart affiche sur une alerte active
/// compare deux valeurs deja publiques (cible, taux client courant) — jamais
/// un declenchement anticipe, la decision reste au scheduler backend.
class RateAlertsController extends ChangeNotifier {
  final RateHistoryApi _api;

  RateAlertsController(this._api);

  bool loading = true;
  String? errorMessage;
  List<RateAlert> alerts = const [];
  String? currentRate;

  bool creating = false;
  String? createErrorMessage;

  List<RateAlert> get activeAlerts => alerts.where((a) => a.status == RateAlertStatus.active).toList(growable: false);

  List<RateAlert> get closedAlerts => alerts.where((a) => a.status != RateAlertStatus.active).toList(growable: false);

  double? gapFor(RateAlert alert) {
    final rate = currentRate;
    if (rate == null) return null;
    final current = double.parse(rate);
    if (current == 0) return null;
    final target = double.parse(alert.targetRate);
    return ((target - current) / current) * 100;
  }

  Future<void> load() async {
    loading = true;
    notifyListeners();
    try {
      final alertsPage = await _api.listAlerts(size: 50);
      alerts = alertsPage.content;
      errorMessage = null;
    } on ApiException catch (error) {
      errorMessage = error.message;
    }
    try {
      final ratePage = await _api.history(size: 1);
      currentRate = ratePage.content.isEmpty ? null : ratePage.content.first.customerRate;
    } on ApiException {
      // Purement informatif (ecart affiche) : un echec ici ne bloque jamais
      // l'affichage des alertes elles-memes.
      currentRate = null;
    }
    loading = false;
    notifyListeners();
  }

  Future<bool> create({required String targetRate, DateTime? expiresAt}) async {
    if (creating) return false;
    creating = true;
    createErrorMessage = null;
    notifyListeners();
    try {
      await _api.createAlert(CreateRateAlertRequest(targetRate: targetRate, expiresAt: expiresAt));
      creating = false;
      await load();
      return true;
    } on ApiException catch (error) {
      createErrorMessage = error.message;
      creating = false;
      notifyListeners();
      return false;
    }
  }

  Future<bool> cancel(RateAlert alert) async {
    try {
      final updated = await _api.cancelAlert(alert.id);
      alerts = alerts.map((a) => a.id == updated.id ? updated : a).toList(growable: false);
      notifyListeners();
      return true;
    } on ApiException catch (error) {
      errorMessage = error.message;
      notifyListeners();
      return false;
    }
  }
}
