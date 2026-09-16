import 'package:flutter/foundation.dart';

import 'package:mobile/core/storage/secure_token_storage.dart';
import '../../shared/models/current_user.dart';
import '../storage/onboarding_store.dart';
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
  final OnboardingStore _onboardingStore;

  String? _token;
  CurrentUser? _currentUser;
  bool _initialized = false;
  bool _onboardingSeen = false;

  AuthSession({SecureTokenStorage? storage, OnboardingStore? onboardingStore})
      : _storage = storage ?? const SecureTokenStorage(),
        _onboardingStore = onboardingStore ?? const OnboardingStore();

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

  /// Vrai si l'introduction de l'application (voir `OnboardingPage`) a deja ete
  /// vue -- consulte par le routeur pour ne l'imposer qu'une seule fois, avant
  /// la toute premiere connexion/inscription (retour client sept. 2026 :
  /// "y'a pas vraiment de systeme pour expliquer comment ca fonctionne").
  bool get onboardingSeen => _onboardingSeen;

  /// A appeler une seule fois au demarrage : charge un jeton deja persiste
  /// (s'il existe) pour permettre l'appel `GET /api/auth/me` de restauration.
  /// Ne marque PAS la session comme authentifiee — seul un `CurrentUser` recu
  /// du backend le fait (voir [completeBootstrap]).
  Future<String?> loadPersistedToken() async {
    _token = await _storage.readToken();
    return _token;
  }

  /// A appeler une seule fois au demarrage, avant [completeBootstrap] --
  /// determine si le routeur doit imposer `OnboardingPage` avant `/login`.
  Future<void> loadOnboardingSeen() async {
    _onboardingSeen = await _onboardingStore.hasSeenOnboarding();
  }

  /// Fin de l'introduction (dernier slide ou "Passer") : persiste le choix
  /// avant de notifier (meme ordre que [setSession]) -- si l'ecriture
  /// echouait apres coup, le routeur aurait deja fait suivre vers /login
  /// alors que l'introduction reapparaitrait au prochain demarrage.
  Future<void> markOnboardingSeen() async {
    _onboardingSeen = true;
    await _onboardingStore.markSeen();
    notifyListeners();
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

  /// Deconnexion explicite ou reaction a un 401 — purge tout, y compris le
  /// stockage securise.
  Future<void> clear() async {
    _token = null;
    _currentUser = null;
    await _storage.clear();
    notifyListeners();
  }
}