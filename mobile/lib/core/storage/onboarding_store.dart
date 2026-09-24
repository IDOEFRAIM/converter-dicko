import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// Memoire locale : l'utilisateur a-t-il deja vu l'introduction de l'application
/// (voir `OnboardingPage`) ?
///
/// Reutilise `flutter_secure_storage`, deja present pour le jeton (aucune
/// nouvelle dependance) -- meme discipline que [CelebratedBadgesStore] : un
/// seul mecanisme de stockage cle/valeur dans l'app, meme pour une donnee non
/// sensible comme celle-ci.
class OnboardingStore {
  static const _key = 'converter.onboarding_seen';

  final FlutterSecureStorage _storage;

  const OnboardingStore({FlutterSecureStorage? storage}) : _storage = storage ?? const FlutterSecureStorage();

  Future<bool> hasSeenOnboarding() async {
    return await _storage.read(key: _key) == 'true';
  }

  Future<void> markSeen() async {
    await _storage.write(key: _key, value: 'true');
  }
}
