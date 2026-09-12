import 'package:flutter/services.dart' show PlatformException;
import 'package:google_sign_in/google_sign_in.dart';

/// Fine couche autour du SDK Google Sign-In : le SEUL point du code qui
/// connait le paquet `google_sign_in` — [AuthRepository]/les ecrans ne
/// manipulent qu'un jeton d'identite (`String`), jamais le SDK directement
/// (mission section 13 : une seule couche connait chaque dependance externe).
///
/// [serverClientId] doit etre le MEME Client ID Web OAuth que
/// `GOOGLE_OAUTH_CLIENT_ID` cote backend (voir `GoogleAuthProperties`) —
/// c'est ce qui permet d'obtenir un jeton d'identite verifiable par le
/// serveur plutot qu'un simple jeton d'acces local. Voir DEPLOY.md pour la
/// configuration native (Android : SHA-1 enregistre ; iOS : GIDClientID +
/// schema d'URL dans Info.plist), impossible a verifier dans cet
/// environnement (ni Android Studio/Xcode).
class GoogleAuthClient {
  final GoogleSignIn _googleSignIn;

  // `clientId` est ignore par les SDK natifs Android/iOS (qui lisent leur
  // propre config : SHA-1 enregistre / GIDClientID) mais OBLIGATOIRE pour
  // l'initialisation du plugin web (`google_sign_in_web`), qui echoue sinon
  // avec `appClientId != null` des le premier tap. Meme Client ID Web des
  // deux cotes : rien a distinguer, un seul id a fournir.
  GoogleAuthClient({required String serverClientId})
      : _googleSignIn = GoogleSignIn(
          clientId: serverClientId,
          serverClientId: serverClientId,
          scopes: const ['email'],
        );

  /// Ouvre le selecteur de compte natif puis renvoie le jeton d'identite (ID
  /// token) a transmettre au backend pour verification — `null` si
  /// l'utilisateur annule (jamais une exception pour une simple annulation,
  /// geste volontaire et frequent, pas une erreur).
  Future<String?> signIn() async {
    try {
      final account = await _googleSignIn.signIn();
      if (account == null) {
        return null;
      }
      final authentication = await account.authentication;
      return authentication.idToken;
    } on PlatformException catch (error) {
      // Certaines plateformes/versions du SDK remontent l'annulation comme
      // une PlatformException plutot qu'un `signIn()` qui renvoie `null` —
      // meme discipline que la capture photo KYC (mission section 38) :
      // jamais laisser une exception de plateforme remonter telle quelle.
      // Code litteral (pas de constante nommee) : stable a travers les
      // versions du paquet `google_sign_in` (documente sans etre garanti
      // expose comme constante publique selon la version).
      if (error.code == 'sign_in_canceled' || error.code == 'sign_in_cancelled') {
        return null;
      }
      rethrow;
    }
  }

  /// Deconnecte le compte Google local (pas le compte Converter) — a
  /// utiliser en complement de `AuthSession.clear()` si l'utilisateur doit
  /// pouvoir choisir un autre compte Google au prochain "Continuer avec
  /// Google" (sinon le SDK reselectionne silencieusement le meme compte).
  Future<void> signOut() => _googleSignIn.signOut();
}
