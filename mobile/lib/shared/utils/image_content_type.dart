import 'dart:typed_data';

import 'package:dio/dio.dart';

/// Devine le VRAI type d'un fichier image a partir de ses octets (signature
/// binaire), jamais de son nom -- miroir exact de `FileValidator.detectContentType`
/// cote backend, qui rejette (`InvalidFileException`) tout Content-Type declare
/// qui ne correspond pas aux octets reels.
///
/// Necessaire car `image_picker` (voir `imageQuality`) reencode parfois une
/// image en JPEG tout en conservant le nom/l'extension d'origine (ex. un
/// screenshot PNG choisi dans la galerie peut ressortir en JPEG sous un nom
/// `.png`) -- se fier au nom de fichier pour le Content-Type (comportement par
/// defaut de `MultipartFile.fromFile`, voir `lookupMediaType`) fait alors
/// echouer la validation cote serveur avec "format non valide", meme si le
/// fichier est en realite un JPEG parfaitement valide (retour client : "il
/// veut juste png").
DioMediaType? sniffImageContentType(Uint8List bytes) {
  if (_startsWith(bytes, [0xFF, 0xD8, 0xFF])) {
    return DioMediaType('image', 'jpeg');
  }
  if (_startsWith(bytes, [0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A])) {
    return DioMediaType('image', 'png');
  }
  if (bytes.length >= 12 &&
      _startsWith(bytes, [0x52, 0x49, 0x46, 0x46]) &&
      _matchesAt(bytes, 8, [0x57, 0x45, 0x42, 0x50])) {
    return DioMediaType('image', 'webp');
  }
  if (_startsWith(bytes, [0x25, 0x50, 0x44, 0x46])) {
    return DioMediaType('application', 'pdf');
  }
  return null;
}

bool _startsWith(Uint8List bytes, List<int> magic) => _matchesAt(bytes, 0, magic);

bool _matchesAt(Uint8List bytes, int offset, List<int> magic) {
  if (bytes.length < offset + magic.length) return false;
  for (var i = 0; i < magic.length; i++) {
    if (bytes[offset + i] != magic[i]) return false;
  }
  return true;
}
