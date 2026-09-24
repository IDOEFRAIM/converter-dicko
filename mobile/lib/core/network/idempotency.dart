import 'dart:convert';
import 'dart:math';

/// Genere une nouvelle cle d'idempotence (UUID v4) — miroir de
/// `crypto.randomUUID()` cote Angular. Pas de dependance `uuid` ajoutee pour
/// un seul appel `Random.secure()` (mission section 43).
String newIdempotencyKey() {
  final random = Random.secure();
  final bytes = List<int>.generate(16, (_) => random.nextInt(256));
  bytes[6] = (bytes[6] & 0x0f) | 0x40; // version 4
  bytes[8] = (bytes[8] & 0x3f) | 0x80; // variant 10xx
  String hex(int start, int length) =>
      bytes.sublist(start, start + length).map((b) => b.toRadixString(16).padLeft(2, '0')).join();
  return '${hex(0, 4)}-${hex(4, 2)}-${hex(6, 2)}-${hex(8, 2)}-${hex(10, 6)}';
}

/// Empreinte stable d'un corps de requete, insensible a l'ordre des cles —
/// miroir exact de `fingerprint()` cote Angular (`idempotency.util.ts`).
String _fingerprint(Object? payload) {
  Object? normalize(Object? value) {
    if (value is Map) {
      final sortedKeys = value.keys.map((k) => k.toString()).toList()..sort();
      return {for (final key in sortedKeys) key: normalize(value[key])};
    }
    if (value is List) {
      return value.map(normalize).toList(growable: false);
    }
    return value;
  }

  return jsonEncode(normalize(payload));
}

/// Suivi d'une tentative d'operation idempotente cote page/formulaire — UNE
/// instance par action mutante (creation d'ordre, paiement, pay-again...).
///
/// Miroir exact de `IdempotencyAttempt` cote Angular : la MEME cle est
/// reutilisee tant que l'utilisateur rejoue exactement le meme payload
/// (retry apres un echec reseau/timeout) ; une cle differente est generee
/// des que le payload change (nouvelle intention) ; [complete] est appele
/// apres un succes confirme pour repartir sur une cle neuve au prochain envoi
/// (mission section 23).
class IdempotencyAttempt {
  String? _key;
  String? _lastFingerprint;

  /// Cle a envoyer pour CET envoi.
  String keyFor(Object? payload) {
    final current = _fingerprint(payload);
    if (_key == null || current != _lastFingerprint) {
      _key = newIdempotencyKey();
      _lastFingerprint = current;
    }
    return _key!;
  }

  /// A appeler apres un succes confirme : le prochain envoi est une nouvelle
  /// intention. Ne JAMAIS appeler sur un echec — un retry doit rejouer la
  /// meme cle (voir mission section 27 sur les echecs reseau).
  void complete() {
    _key = null;
    _lastFingerprint = null;
  }
}
