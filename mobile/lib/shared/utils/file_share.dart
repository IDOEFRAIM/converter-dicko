import 'dart:io';
import 'dart:typed_data';
import 'dart:ui';

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
