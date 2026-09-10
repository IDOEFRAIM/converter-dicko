import 'dart:convert';
import 'dart:io';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:path_provider/path_provider.dart';

/// Livre memoire **local** (remarque produit #5) : un selfie souvenir par
/// transfert termine, stocke uniquement sur cet appareil.
///
/// - L'index (`orderId` -> chemin absolu du fichier) vit dans
///   `flutter_secure_storage`, une seule cle JSON — meme mecanisme cle/valeur
///   que [CelebratedBadgesStore] / [ProObjectiveStore].
/// - Les images sont copiees dans `<documents>/memories/<orderId>.jpg`.
///
/// Rien n'est jamais envoye au backend : c'est un journal intime d'appareil.
class MemoryBookStore {
  static const _key = 'converter.memory_book';

  final FlutterSecureStorage _storage;

  const MemoryBookStore({FlutterSecureStorage? storage})
      : _storage = storage ?? const FlutterSecureStorage();

  /// L'index complet `orderId -> chemin de fichier` (vide si aucun, ou si
  /// l'index est illisible).
  Future<Map<String, String>> readAll() async {
    final raw = await _storage.read(key: _key);
    if (raw == null || raw.isEmpty) return <String, String>{};
    try {
      final decoded = jsonDecode(raw) as Map<String, dynamic>;
      return decoded.map((key, value) => MapEntry(key, value as String));
    } catch (_) {
      return <String, String>{};
    }
  }

  Future<String?> pathFor(String orderId) async => (await readAll())[orderId];

  /// Copie [sourcePath] (fichier temporaire du picker) dans le dossier
  /// documents, met a jour l'index, renvoie le chemin persistant.
  Future<String> put(String orderId, String sourcePath) async {
    final base = await getApplicationDocumentsDirectory();
    final dir = Directory('${base.path}/memories');
    if (!await dir.exists()) {
      await dir.create(recursive: true);
    }
    final dest = '${dir.path}/$orderId.jpg';
    await File(sourcePath).copy(dest);

    final map = await readAll();
    map[orderId] = dest;
    await _storage.write(key: _key, value: jsonEncode(map));
    return dest;
  }

  Future<void> remove(String orderId) async {
    final map = await readAll();
    final path = map.remove(orderId);
    if (path != null) {
      try {
        final file = File(path);
        if (await file.exists()) {
          await file.delete();
        }
      } catch (_) {
        // Fichier deja absent (cache nettoye...) : l'entree d'index disparait
        // quand meme, c'est le seul etat qui compte.
      }
    }
    await _storage.write(key: _key, value: jsonEncode(map));
  }
}
