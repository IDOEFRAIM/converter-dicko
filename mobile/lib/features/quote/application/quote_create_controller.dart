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
  ///
  /// Retour beta-testeur sept. 2026 : "on est a l'order, on appuie sur continuer et on revient
  /// en arriere, on nous dit que ce devis n'est plus modifiable... l'utilisateur est bloque, ca
  /// le decourage". Cause exacte : `_continueToOrder` (quote_create_page.dart) POUSSE la route de
  /// creation d'ordre (`context.push`) sans jamais quitter cette page -- un retour arriere
  /// retombe donc sur CE MEME controleur, avec un [quote] deja passe a ACCEPTED. Re-appuyer sur
  /// "Continuer" re-appelait `/accept` sur un devis deja accepte, que le backend refuse a raison
  /// (`Quote.requireActive`) avec `INVALID_QUOTE_STATE` -- un vrai blocage pour l'utilisateur, sur
  /// une situation qui n'est pourtant PAS une erreur : la suite du parcours (creer l'ordre) reste
  /// entierement valide avec ce meme devis.
  Future<Quote?> accept() async {
    final current = quote;
    if (current == null || accepting) {
      return null;
    }
    // Deja accepte : rien a re-envoyer, on renvoie directement le devis existant plutot que de
    // bloquer l'utilisateur sur une erreur qui n'en est pas une.
    if (current.status == QuoteStatus.accepted) {
      return current;
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
      // Meme situation, decouverte cote serveur cette fois (ex. le premier accept() a reussi
      // mais la reponse s'est perdue en reseau, [quote] etant alors reste ACTIVE localement) : on
      // revalide l'etat reel du devis plutot que d'afficher tel quel un message technique qui
      // bloquerait l'utilisateur sans explication ni suite possible.
      if (error.code == 'INVALID_QUOTE_STATE') {
        try {
          final fresh = await _quoteApi.get(current.id);
          quote = fresh;
          if (fresh.status == QuoteStatus.accepted) {
            accepting = false;
            notifyListeners();
            return fresh;
          }
        } catch (_) {
          // Ignore : on retombe sur le message d'erreur original ci-dessous.
        }
      }
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
