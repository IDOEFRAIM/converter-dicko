import 'package:flutter/foundation.dart';

import '../../../core/errors/api_exception.dart';
import '../data/quote_api.dart';
import '../models/quote_models.dart';

/// Etat de l'ecran de creation de devis : saisie du montant XOF envoye ->
/// devis actif -> acceptation. Aucun calcul financier ici, uniquement des
/// appels backend et l'affichage tel quel du resultat (mission section 20).
class QuoteCreateController extends ChangeNotifier {
  final QuoteApi _quoteApi;

  QuoteCreateController(this._quoteApi);

  bool creating = false;
  bool accepting = false;
  String? errorMessage;
  Quote? quote;

  Future<void> createForAmountXof(String amountXof) async {
    creating = true;
    errorMessage = null;
    notifyListeners();
    try {
      quote = await _quoteApi.create(CreateQuoteRequest.sendXof(amountXof));
    } on ApiException catch (error) {
      errorMessage = error.message;
    }
    creating = false;
    notifyListeners();
  }

  /// Retourne le devis accepte en cas de succes, ou null si l'acceptation a
  /// echoue (le message est deja pose dans [errorMessage]).
  Future<Quote?> accept() async {
    final current = quote;
    if (current == null || accepting) {
      return null;
    }
    accepting = true;
    errorMessage = null;
    notifyListeners();
    try {
      quote = await _quoteApi.accept(current.id);
      accepting = false;
      notifyListeners();
      return quote;
    } on ApiException catch (error) {
      errorMessage = error.message;
      accepting = false;
      notifyListeners();
      return null;
    }
  }

  void reset() {
    quote = null;
    errorMessage = null;
    creating = false;
    accepting = false;
    notifyListeners();
  }
}
