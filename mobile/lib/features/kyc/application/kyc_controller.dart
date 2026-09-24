import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart' show PlatformException;
import 'package:image_picker/image_picker.dart';

import '../../../core/errors/api_exception.dart';
import '../data/kyc_api.dart';
import '../models/kyc_models.dart';

/// Etat du parcours de verification d'identite (remarque produit #6) : suivi du
/// dernier dossier + capture des trois photos + soumission.
class KycController extends ChangeNotifier {
  final KycApi _api;
  final ImagePicker _picker = ImagePicker();

  KycController(this._api);

  bool loading = true;
  String? errorMessage;
  KycSubmission? submission;

  KycDocumentType documentType = KycDocumentType.nationalId;
  String? frontPath;
  String? backPath;
  String? selfiePath;
  bool submitting = false;

  Future<void> load() async {
    loading = true;
    notifyListeners();
    try {
      submission = await _api.mySubmission();
      errorMessage = null;
    } on ApiException catch (error) {
      errorMessage = error.message;
    }
    loading = false;
    notifyListeners();
  }

  void setDocumentType(KycDocumentType type) {
    if (documentType == type) return;
    documentType = type;
    if (!type.needsBack) backPath = null;
    notifyListeners();
  }

  /// [slot] : `'front'`, `'back'` ou `'selfie'`. [source] : appareil photo ou
  /// galerie (choisi par l'utilisateur). Best-effort : annulation ou echec ne
  /// laissent jamais un tap muet, et le message dit *pourquoi* (permission,
  /// pas de camera...).
  Future<void> capture(String slot, ImageSource source) async {
    try {
      final XFile? shot = await _picker.pickImage(
        source: source,
        preferredCameraDevice: slot == 'selfie' ? CameraDevice.front : CameraDevice.rear,
        imageQuality: 80,
      );
      if (shot == null) return;
      switch (slot) {
        case 'front':
          frontPath = shot.path;
        case 'back':
          backPath = shot.path;
        case 'selfie':
          selfiePath = shot.path;
      }
      errorMessage = null;
      notifyListeners();
    } on PlatformException catch (error) {
      errorMessage = switch (error.code) {
        'camera_access_denied' || 'photo_access_denied' =>
          "Acces refuse. Autorisez l'appareil photo ou les photos dans les reglages du telephone.",
        'no_available_camera' => 'Aucun appareil photo disponible sur ce telephone.',
        'already_active' => 'Un choix de photo est deja en cours. Patientez un instant.',
        _ => 'Impossible d\'ouvrir ${source == ImageSource.camera ? "l'appareil photo" : "la galerie"}. Reessayez.',
      };
      notifyListeners();
    } catch (_) {
      errorMessage = 'Impossible de recuperer la photo. Reessayez.';
      notifyListeners();
    }
  }

  bool get canSubmit =>
      !submitting &&
      frontPath != null &&
      selfiePath != null &&
      (!documentType.needsBack || backPath != null);

  Future<bool> submit() async {
    if (!canSubmit) return false;
    submitting = true;
    errorMessage = null;
    notifyListeners();
    try {
      submission = await _api.submit(
        documentType: documentType,
        frontPath: frontPath!,
        backPath: documentType.needsBack ? backPath : null,
        selfiePath: selfiePath!,
      );
      frontPath = null;
      backPath = null;
      selfiePath = null;
      submitting = false;
      notifyListeners();
      return true;
    } on ApiException catch (error) {
      errorMessage = error.message;
      submitting = false;
      notifyListeners();
      return false;
    }
  }
}
