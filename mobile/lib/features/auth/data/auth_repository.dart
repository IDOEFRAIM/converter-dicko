import '../../../core/auth/auth_session.dart';
import '../../../core/network/api_client.dart';
import '../../../shared/models/current_user.dart';
import '../models/auth_models.dart';

/// Acces aux endpoints `/api/auth/*` + mise a jour de [AuthSession].
///
/// Miroir de `AuthService` cote Angular : la verite sur l'utilisateur et son
/// role vient exclusivement des reponses backend (`AuthResult.user`,
/// `GET /auth/me`), jamais decodee du JWT cote client.
class AuthRepository {
  final ApiClient _client;
  final AuthSession _session;

  AuthRepository({required ApiClient client, required AuthSession session})
      : _client = client,
        _session = session;

  Future<CurrentUser> login(LoginRequest request) async {
    final body = await _client.post('/auth/login', data: request.toJson());
    final result = AuthResult.fromJson(body['data'] as Map<String, dynamic>);
    await _session.setSession(token: result.accessToken, user: result.user);
    return result.user;
  }

  /// Inscription : ne connecte PAS automatiquement (miroir du backend/Angular
  /// — l'ecran d'inscription redirige ensuite vers l'ecran de connexion).
  Future<void> register(RegisterRequest request) async {
    await _client.post('/auth/register', data: request.toJson());
  }

  /// Premiere etape de "Continuer avec Google" : [idToken] vient du SDK
  /// natif (voir `GoogleAuthClient`), jamais construit ici. Si un compte est
  /// deja associe, la session est ouverte immediatement (meme effet que
  /// [login]) ; sinon l'ecran appelant doit collecter le numero de telephone
  /// et appeler [completeGoogleSignUp] avec le MEME jeton.
  Future<GoogleSignInResult> googleSignIn(String idToken) async {
    final body = await _client.post('/auth/google', data: {'idToken': idToken});
    final result = GoogleSignInResult.fromJson(body['data'] as Map<String, dynamic>);
    if (result.accountExists && result.auth != null) {
      await _session.setSession(token: result.auth!.accessToken, user: result.auth!.user);
    }
    return result;
  }

  Future<CurrentUser> completeGoogleSignUp(CompleteGoogleSignUpRequest request) async {
    final body = await _client.post('/auth/google/complete', data: request.toJson());
    final result = AuthResult.fromJson(body['data'] as Map<String, dynamic>);
    await _session.setSession(token: result.accessToken, user: result.user);
    return result.user;
  }

  /// Restaure la session a partir d'un jeton deja persiste (lancement de
  /// l'app). En cas d'echec (jeton expire/invalide), purge la session locale.
  Future<CurrentUser?> restoreSession() async {
    final token = await _session.loadPersistedToken();
    if (token == null) {
      return null;
    }
    try {
      final body = await _client.get('/auth/me');
      final user = CurrentUser.fromJson(body['data'] as Map<String, dynamic>);
      return user;
    } catch (_) {
      await _session.clear();
      return null;
    }
  }

  Future<void> logout() => _session.clear();

  /// Suppression de compte en libre-service (retour client : "est-ce que
  /// l'utilisateur a la possibilite de supprimer ses donnees ?"). Le backend
  /// anonymise le compte (transferts deja effectues conserves pour les
  /// obligations legales) et refuse si un transfert est encore en cours ou
  /// pour un compte administrateur (voir `ErrorCode.ACCOUNT_DELETION_BLOCKED`) —
  /// l'ecran appelant affiche alors `ApiException.message` tel quel.
  /// [currentPassword] est ignore par le backend pour un compte cree via
  /// Google (voir `CurrentUser.hasPassword`).
  Future<void> deleteAccount({String? currentPassword}) async {
    await _client.delete('/auth/me', data: {'currentPassword': currentPassword});
    await _session.clear();
  }
}
