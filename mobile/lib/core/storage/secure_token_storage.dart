import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// Point d'acces unique au jeton JWT persiste, dans le Keychain (iOS) ou le
/// Keystore/EncryptedSharedPreferences (Android) — jamais dans une constante,
/// un fichier texte ou une preference non chiffree (mission section 15).
///
/// Miroir du role de `TokenStorageService` cote Angular, avec un stockage
/// natif securise a la place du `localStorage` (acceptable pour un SPA web,
/// pas pour du code embarque sur l'appareil de l'utilisateur).
class SecureTokenStorage {
  static const _tokenKey = 'converter.access_token';

  final FlutterSecureStorage _storage;

  const SecureTokenStorage({FlutterSecureStorage? storage})
      : _storage = storage ?? const FlutterSecureStorage();

  Future<String?> readToken() => _storage.read(key: _tokenKey);

  Future<void> writeToken(String token) => _storage.write(key: _tokenKey, value: token);

  Future<void> clear() => _storage.delete(key: _tokenKey);
}
