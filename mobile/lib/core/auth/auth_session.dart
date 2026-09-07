import 'package:flutter/foundation.dart';

import 'package:mobile/core/storage/secure_token_storage.dart';
import '../../shared/models/current_user.dart';
import '../theme/experience_theme.dart';

/// Etat de session courant : jeton JWT + utilisateur connecte.
///
/// Source unique consultee par la couche reseau (en-tete Authorization), par
/// le routeur (garde de redirection /login) et par l'UI (nom affiche,
/// visibilite des sections admin). Le jeton est mis en cache en memoire pour
/// un acces synchrone (l'intercepteur HTTP ne doit pas attendre une lecture
/// du stockage securise a chaque requete) et reste la seule copie source de
/// verite avec [SecureTokenStorage] — jamais duplique ailleurs.
class AuthSession extends ChangeNotifier {
  final SecureTokenStorage _storage;

  String? _token;
  CurrentUser? _currentUser;
  bool _initialized = false;

  AuthSession({SecureTokenStorage? storage}) : _storage = storage ?? const SecureTokenStorage();

  String? get token => _token;
  CurrentUser? get currentUser => _currentUser;
  bool get isAuthenticated => _currentUser != null;

  /// PRO tant qu'aucun utilisateur n'est connu (splash/login) — jamais de
  /// bascule visuelle avant qu'un compte reel n'ait choisi un habillage.
  ExperienceProfile get experienceProfile => _currentUser?.experienceProfile ?? ExperienceProfile.pro;

  ExperienceGradient get experienceGradient => ExperiencePalette.gradientFor(experienceProfile);

  /// Vrai une fois que la tentative de restauration de session au demarrage
  /// (jeton persiste -> `GET /api/auth/me`) est terminee, succes ou echec.
  bool get initialized => _initialized;

  /// A appeler une seule fois au demarrage : charge un jeton deja persiste
  /// (s'il existe) pour permettre l'appel `GET /api/auth/me` de restauration.
  /// Ne marque PAS la session comme authentifiee — seul un `CurrentUser` recu
  /// du backend le fait (voir [completeBootstrap]).
  Future<String?> loadPersistedToken() async {
    _token = await _storage.readToken();
    return _token;
  }

  /// Fin de la restauration de session (succes ou echec) — debloque les
  /// gardes de routing qui attendent [initialized].
  void completeBootstrap({CurrentUser? user}) {
    _currentUser = user;
    _initialized = true;
    notifyListeners();
  }

  /// Connexion/inscription reussie : persiste le jeton et memorise l'utilisateur.
  Future<void> setSession({required String token, required CurrentUser user}) async {
    _token = token;
    _currentUser = user;
    _initialized = true;
    await _storage.writeToken(token);
    notifyListeners();
  }

  void updateCurrentUser(CurrentUser user) {
    _currentUser = user;
    notifyListeners();
  }

  /// Deconnexion explicite ou reaction a un 401 — purge tout, y compris le
  /// stockage securise.
  Future<void> clear() async {
    _token = null;
    _currentUser = null;
    await _storage.clear();
    notifyListeners();
  }
}