import 'dart:io';
import 'dart:typed_data';
import 'dart:ui';

import 'package:open_filex/open_filex.dart';
import 'package:path_provider/path_provider.dart';
import 'package:share_plus/share_plus.dart';

/// Materialise des octets (justificatif PDF telecharge du backend, ou PDF
/// genere localement — voir `pro_activity_report.dart`, mission
/// "differenciation marketing") dans le repertoire temporaire de l'app puis
/// ouvre la feuille de partage/ouverture systeme (mission section 28).
///
/// [sharePositionOrigin] ancre la popover sur iPad au bouton presse (sinon
/// repli sur le centre de l'ecran, sans crash sur les versions recentes de
/// share_plus) — passer `(box.localToGlobal(Offset.zero) & box.size)` depuis
/// l'ecran appelant si disponible.
Future<void> saveAndShareBytes({
  required List<int> bytes,
  required String fileName,
  Rect? sharePositionOrigin,
}) async {
  final directory = await getTemporaryDirectory();
  final file = File('${directory.path}/$fileName');
  await file.writeAsBytes(Uint8List.fromList(bytes), flush: true);
  await SharePlus.instance.share(
    ShareParams(files: [XFile(file.path)], sharePositionOrigin: sharePositionOrigin),
  );
}

/// Materialise des octets PDF puis les OUVRE dans le lecteur du systeme —
/// comportement attendu d'un "telechargement" : le document s'affiche.
///
/// A utiliser pour le recu et la facture proforma : la feuille de partage
/// ([saveAndShareBytes]) y mettait en avant WhatsApp / la liste de contacts,
/// ce qui laissait croire a une invitation (ruee collective) plutot qu'a un
/// telechargement (retour utilisateur, sept. 2026).
///
/// Repli sur la feuille de partage uniquement si aucune application ne sait
/// ouvrir un PDF (l'utilisateur peut alors l'enregistrer dans Fichiers/Drive).
/// Toute autre issue leve : l'ecran appelant a deja un `catch` qui affiche un
/// message — le telechargement reseau avait reussi, on ne laisse jamais l'echec
/// d'ouverture passer pour "rien ne s'est produit".
Future<void> saveAndOpenBytes({
  required List<int> bytes,
  required String fileName,
  Rect? sharePositionOrigin,
}) async {
  final directory = await getTemporaryDirectory();
  final file = File('${directory.path}/$fileName');
  await file.writeAsBytes(Uint8List.fromList(bytes), flush: true);

  final result = await OpenFilex.open(file.path, type: 'application/pdf');
  switch (result.type) {
    case ResultType.done:
      return;
    case ResultType.noAppToOpen:
      await SharePlus.instance.share(
        ShareParams(files: [XFile(file.path)], sharePositionOrigin: sharePositionOrigin),
      );
      return;
    case ResultType.fileNotFound:
    case ResultType.permissionDenied:
    case ResultType.error:
      throw StateError('open_filex ${result.type}: ${result.message}');
  }
}
