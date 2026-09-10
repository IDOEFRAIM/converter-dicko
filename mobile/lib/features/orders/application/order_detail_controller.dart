import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart' show PlatformException;
import 'package:image_picker/image_picker.dart';

import '../../../core/errors/api_exception.dart';
import '../../../core/network/api_client.dart';
import '../../../core/storage/memory_book_store.dart';
import '../data/order_api.dart';
import '../models/order_models.dart';

class OrderDetailController extends ChangeNotifier {
  final OrderApi _orderApi;
  final MemoryBookStore _memoryBook;
  final ImagePicker _imagePicker = ImagePicker();
  final String orderId;

  OrderDetailController({
    required OrderApi orderApi,
    required MemoryBookStore memoryBook,
    required this.orderId,
  })  : _orderApi = orderApi,
        _memoryBook = memoryBook;

  bool loading = true;
  String? errorMessage;
  OrderDetail? order;

  bool cancelling = false;
  bool downloadingReceipt = false;
  BinaryDownload? lastReceipt;

  bool downloadingProforma = false;

  /// Selfie souvenir local rattache a cet ordre (remarque produit #5) —
  /// `null` tant qu'aucun n'a ete pris. Jamais envoye au serveur.
  String? selfiePath;
  bool capturingSelfie = false;

  Future<void> load() async {
    loading = true;
    errorMessage = null;
    notifyListeners();
    try {
      order = await _orderApi.get(orderId);
    } on ApiException catch (error) {
      errorMessage = error.message;
    }
    selfiePath = await _memoryBook.pathFor(orderId);
    loading = false;
    notifyListeners();
  }

  /// Prend un selfie (camera) et l'ajoute au livre memoire local. Best-effort :
  /// annulation, permission refusee ou plugin absent ne laissent jamais un tap
  /// muet (meme discipline que l'upload de preuve de paiement).
  Future<void> captureSelfie() async {
    if (capturingSelfie) return;
    capturingSelfie = true;
    errorMessage = null;
    notifyListeners();
    try {
      final XFile? shot = await _imagePicker.pickImage(
        source: ImageSource.camera,
        preferredCameraDevice: CameraDevice.front,
        imageQuality: 85,
      );
      if (shot != null) {
        selfiePath = await _memoryBook.put(orderId, shot.path);
      }
    } on PlatformException catch (error) {
      errorMessage = switch (error.code) {
        'camera_access_denied' =>
          "Acces refuse. Autorisez l'appareil photo dans les reglages du telephone.",
        'no_available_camera' => 'Aucun appareil photo disponible sur ce telephone.',
        _ => "Impossible d'ouvrir l'appareil photo. Reessayez.",
      };
    } catch (_) {
      errorMessage = "Impossible d'enregistrer le selfie. Reessayez.";
    }
    capturingSelfie = false;
    notifyListeners();
  }

  Future<void> removeSelfie() async {
    await _memoryBook.remove(orderId);
    selfiePath = null;
    notifyListeners();
  }

  Future<bool> cancel(String reason) async {
    final current = order;
    if (current == null || cancelling) return false;
    cancelling = true;
    notifyListeners();
    try {
      order = await _orderApi.cancel(current.id, reason);
      cancelling = false;
      notifyListeners();
      return true;
    } on ApiException catch (error) {
      errorMessage = error.message;
      cancelling = false;
      notifyListeners();
      return false;
    }
  }

  Future<BinaryDownload?> downloadReceipt() async {
    final current = order;
    if (current == null || downloadingReceipt) return null;
    downloadingReceipt = true;
    errorMessage = null;
    notifyListeners();
    try {
      final download = await _orderApi.downloadReceipt(current.id);
      lastReceipt = download;
      downloadingReceipt = false;
      notifyListeners();
      return download;
    } on ApiException catch (error) {
      errorMessage = error.message;
      downloadingReceipt = false;
      notifyListeners();
      return null;
    }
  }

  Future<BinaryDownload?> downloadProforma() async {
    final current = order;
    if (current == null || downloadingProforma) return null;
    downloadingProforma = true;
    errorMessage = null;
    notifyListeners();
    try {
      final download = await _orderApi.downloadProforma(current.id);
      downloadingProforma = false;
      notifyListeners();
      return download;
    } on ApiException catch (error) {
      errorMessage = error.message;
      downloadingProforma = false;
      notifyListeners();
      return null;
    }
  }
}
