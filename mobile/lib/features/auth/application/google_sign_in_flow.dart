import '../data/auth_repository.dart';
import '../data/google_auth_client.dart';

/// Issue d'une tentative "Continuer avec Google", pour que l'ecran appelant
/// (login ou inscription — les deux proposent le meme bouton) sache quoi
/// faire ensuite. Jamais construit ailleurs qu'ici : le SDK Google et
/// l'appel reseau restent une seule etape atomique du point de vue de l'UI.
sealed class GoogleSignInFlowResult {
  const GoogleSignInFlowResult();
}

/// L'utilisateur a ferme le selecteur de compte sans en choisir un — pas une
/// erreur, l'ecran appelant ne doit rien afficher.
class GoogleSignInCancelled extends GoogleSignInFlowResult {
  const GoogleSignInCancelled();
}

/// Un compte existait deja pour ce compte Google : [AuthRepository] a deja
/// ouvert la session, la redirection vers l'accueil est geree par le
/// routeur (meme principe que [AuthRepository.login]).
class GoogleSignInLoggedIn extends GoogleSignInFlowResult {
  const GoogleSignInLoggedIn();
}

/// Aucun compte associe : l'ecran appelant doit naviguer vers l'ecran
/// "votre numero" en transmettant CET objet (le jeton Google doit etre
/// reutilise tel quel, jamais redemande au SDK une seconde fois).
class GoogleSignInNeedsPhone extends GoogleSignInFlowResult {
  final String idToken;
  final String? email;
  final String? suggestedFirstName;
  final String? suggestedLastName;

  const GoogleSignInNeedsPhone({
    required this.idToken,
    this.email,
    this.suggestedFirstName,
    this.suggestedLastName,
  });
}

/// Point d'entree unique du bouton "Continuer avec Google" — utilise a
/// l'identique depuis l'ecran de connexion et celui d'inscription.
Future<GoogleSignInFlowResult> runGoogleSignIn({
  required GoogleAuthClient googleAuthClient,
  required AuthRepository authRepository,
}) async {
  final idToken = await googleAuthClient.signIn();
  if (idToken == null) {
    return const GoogleSignInCancelled();
  }

  final result = await authRepository.googleSignIn(idToken);
  if (result.accountExists) {
    return const GoogleSignInLoggedIn();
  }
  return GoogleSignInNeedsPhone(
    idToken: idToken,
    email: result.email,
    suggestedFirstName: result.suggestedFirstName,
    suggestedLastName: result.suggestedLastName,
  );
}
