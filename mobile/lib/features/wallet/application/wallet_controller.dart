import 'package:flutter/foundation.dart';

import '../../../core/errors/api_exception.dart';
import '../../preferred_rate/data/preferred_rate_api.dart';
import '../../preferred_rate/models/preferred_rate_models.dart';
import '../data/wallet_api.dart';
import '../models/wallet_models.dart';

/// Etat de l'ecran "Portefeuille" : solde (disponible / reserve / total) et
/// historique des mouvements. Lecture seule — le rechargement du solde n'est
/// pas ouvert cote client (parite avec le frontend web).
///
/// Un debit ([WalletTransactionType.debit]) declenche par un taux preferentiel
/// dont l'echange est encore en cours est signale a part ([engagedReferenceIds])
/// : le montant est deja sorti du solde mais l'operation n'est pas terminee —
/// meme nuance que le web (`wallet.page.ts#engagedLabelFor`).
class WalletController extends ChangeNotifier {
  final WalletApi _api;
  final PreferredRateApi _preferredRateApi;

  WalletController(this._api, this._preferredRateApi);

  bool loading = true;
  String? errorMessage;
  Wallet? wallet;
  List<WalletTransaction> transactions = const [];
  Set<String> engagedReferenceIds = const {};

  Future<void> load() async {
    loading = true;
    notifyListeners();
    try {
      wallet = await _api.snapshot();
      transactions = (await _api.transactions(size: 30)).content;
      errorMessage = null;
    } on ApiException catch (error) {
      errorMessage = error.message;
    }

    try {
      final prefs = await _preferredRateApi.listMine(size: 20);
      engagedReferenceIds = prefs.content
          .where((request) => request.phase == PreferredRatePhase.exchangeInProgress)
          .map((request) => request.id)
          .toSet();
    } on ApiException catch (_) {
      // Best-effort : l'historique reste lisible sans cette nuance.
      engagedReferenceIds = const {};
    }

    loading = false;
    notifyListeners();
  }

  bool isEngaged(WalletTransaction tx) =>
      tx.type == WalletTransactionType.debit &&
      tx.referenceId != null &&
      engagedReferenceIds.contains(tx.referenceId);
}
