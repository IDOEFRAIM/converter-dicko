import 'package:flutter/foundation.dart';

import '../../../core/errors/api_exception.dart';
import '../data/preferred_rate_api.dart';
import '../models/preferred_rate_models.dart';

/// Etat de l'ecran "Taux preferentiel" : creation d'une demande, liste
/// (en attente / en cours d'echange vs terminees), annulation. Le montant
/// est immobilise sur le Wallet des la creation cote backend — voir
/// `PreferredRateService.create` ; cet ecran n'a jamais a le savoir/verifier
/// lui-meme, il se contente d'afficher l'erreur backend (ex. solde
/// insuffisant) si la creation echoue.
class PreferredRateController extends ChangeNotifier {
  final PreferredRateApi _api;

  PreferredRateController(this._api);

  bool loading = true;
  String? errorMessage;
  List<PreferredRate> requests = const [];

  bool creating = false;
  String? createErrorMessage;

  final Set<String> _cancelling = {};

  bool isCancelling(String id) => _cancelling.contains(id);

  List<PreferredRate> get activeRequests => requests
      .where((r) => r.phase == PreferredRatePhase.waiting || r.phase == PreferredRatePhase.exchangeInProgress)
      .toList(growable: false);

  List<PreferredRate> get closedRequests => requests
      .where((r) => r.phase != PreferredRatePhase.waiting && r.phase != PreferredRatePhase.exchangeInProgress)
      .toList(growable: false);

  Future<void> load() async {
    loading = true;
    notifyListeners();
    try {
      final page = await _api.listMine(size: 50);
      requests = page.content;
      errorMessage = null;
    } on ApiException catch (error) {
      errorMessage = error.message;
    }
    loading = false;
    notifyListeners();
  }

  Future<bool> create({required String amountXof, required String targetRate}) async {
    if (creating) return false;
    creating = true;
    createErrorMessage = null;
    notifyListeners();
    try {
      await _api.create(CreatePreferredRateRequest(amountXof: amountXof, targetRate: targetRate));
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

  Future<bool> cancel(PreferredRate request) async {
    if (_cancelling.contains(request.id)) return false;
    _cancelling.add(request.id);
    notifyListeners();
    try {
      final updated = await _api.cancel(request.id);
      requests = requests.map((r) => r.id == updated.id ? updated : r).toList(growable: false);
      _cancelling.remove(request.id);
      notifyListeners();
      return true;
    } on ApiException catch (error) {
      errorMessage = error.message;
      _cancelling.remove(request.id);
      notifyListeners();
      return false;
    }
  }
}
