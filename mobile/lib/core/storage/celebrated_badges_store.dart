import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// Memoire locale des notifications `BADGE_UNLOCKED` deja celebrees par un
/// overlay plein ecran.
///
/// C'est le garde-fou reel contre une celebration rejouee : le marquage "lu"
/// cote serveur peut echouer (reseau), or une celebration ne doit JAMAIS
/// reapparaitre pour le meme evenement. Volontairement borne aux 30 derniers
/// ids — une celebration n'a de sens que sur un franchissement recent, la
/// liste ne grossit pas indefiniment.
///
/// Reutilise `flutter_secure_storage`, deja present pour le jeton (aucune
/// nouvelle dependance) ; ces ids ne sont pas sensibles, le choix est
/// uniquement "un seul mecanisme de stockage cle/valeur dans l'app".
class CelebratedBadgesStore {
  static const _key = 'converter.celebrated_badge_ids';
  static const _maxIds = 30;

  final FlutterSecureStorage _storage;

  const CelebratedBadgesStore({FlutterSecureStorage? storage})
      : _storage = storage ?? const FlutterSecureStorage();

  Future<Set<String>> read() async {
    final raw = await _storage.read(key: _key);
    if (raw == null || raw.isEmpty) return <String>{};
    return raw.split(',').where((id) => id.isNotEmpty).toSet();
  }

  Future<void> add(String id) async {
    final current = await read();
    if (current.contains(id)) return;
    final next = <String>[...current, id];
    final trimmed = next.length > _maxIds ? next.sublist(next.length - _maxIds) : next;
    await _storage.write(key: _key, value: trimmed.join(','));
  }

  Future<void> clear() => _storage.delete(key: _key);
}
