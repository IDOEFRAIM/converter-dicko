import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// Objectif de volume mensuel que le PRO se fixe lui-meme — la seule "cible"
/// honnete du cadran de la console (langage de design "Le Comptoir", Lot D).
/// Le backend n'impose aucun objectif : on n'en invente donc pas un, c'est
/// l'utilisateur qui saisit le sien. Un entier XOF (le XOF n'a pas de
/// sous-unite), stocke localement.
///
/// Reutilise `flutter_secure_storage` comme `CelebratedBadgesStore` : aucune
/// nouvelle dependance, un seul mecanisme cle/valeur dans l'app. La valeur
/// n'est pas sensible — c'est une preference d'affichage.
class ProObjectiveStore {
  static const _key = 'converter.pro_monthly_objective_xof';

  final FlutterSecureStorage _storage;

  const ProObjectiveStore({FlutterSecureStorage? storage})
      : _storage = storage ?? const FlutterSecureStorage();

  /// L'objectif enregistre, ou `null` si aucun (ou valeur illisible/non
  /// positive — traitee comme "aucun objectif").
  Future<int?> read() async {
    final raw = await _storage.read(key: _key);
    if (raw == null || raw.isEmpty) return null;
    final value = int.tryParse(raw);
    if (value == null || value <= 0) return null;
    return value;
  }

  /// Enregistre un objectif strictement positif. Une valeur `<= 0` efface
  /// l'objectif (equivaut a "aucun objectif").
  Future<void> write(int objectiveXof) {
    if (objectiveXof <= 0) return clear();
    return _storage.write(key: _key, value: objectiveXof.toString());
  }

  Future<void> clear() => _storage.delete(key: _key);
}
