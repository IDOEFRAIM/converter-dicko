import 'package:flutter/foundation.dart';
import 'package:image_picker/image_picker.dart';

import '../../../core/errors/api_exception.dart';
import '../../../core/network/idempotency.dart';
import '../../orders/data/order_api.dart';
import '../../orders/models/order_models.dart';
import '../../settings/data/settings_api.dart';
import '../../settings/models/public_settings.dart';
import '../data/payment_api.dart';
import '../models/payment_models.dart';

/// Etat de la page de paiement (mission section 26) : chargement de l'ordre
/// + des methodes activees, declaration du paiement (idempotente), puis
/// ajout de la preuve.
class PaymentSubmitController extends ChangeNotifier {
  final OrderApi _orderApi;
  final PaymentApi _paymentApi;
  final SettingsApi _settingsApi;
  final String orderId;
  final _idempotency = IdempotencyAttempt();
  final _imagePicker = ImagePicker();

  PaymentSubmitController({
    required OrderApi orderApi,
    required PaymentApi paymentApi,
    required SettingsApi settingsApi,
    required this.orderId,
  })  : _orderApi = orderApi,
        _paymentApi = paymentApi,
        _settingsApi = settingsApi;

  bool loading = true;
  String? loadError;
  OrderDetail? order;
  PublicSettings? settings;

  bool submitting = false;
  String? errorMessage;
  Payment? payment;

  bool uploadingProof = false;
  bool proofUploaded = false;

  Future<void> load() async {
    loading = true;
    notifyListeners();
    try {
      final results = await Future.wait([_orderApi.get(orderId), _settingsApi.publicSettings()]);
      order = results[0] as OrderDetail;
      settings = results[1] as PublicSettings;
      loadError = null;
    } on ApiException catch (error) {
      loadError = error.message;
    }
    loading = false;
    notifyListeners();
  }

  List<PaymentMethod> get enabledMethods {
    final codes = settings?.enabledPaymentMethods ?? const ['MOBILE_MONEY'];
    return codes.map(PaymentMethod.fromCode).where((m) => m != PaymentMethod.unknown).toList(growable: false);
  }

  Future<bool> submit({
    required PaymentMethod method,
    required String transactionReference,
    String? payerPhone,
  }) async {
    final currentOrder = order;
    if (currentOrder == null || submitting) return false;

    final request = SubmitPaymentRequest(
      method: method,
      receivedAmountXof: currentOrder.amountXof,
      transactionReference: transactionReference,
      payerPhone: payerPhone,
    );

    submitting = true;
    errorMessage = null;
    notifyListeners();

    try {
      final key = _idempotency.keyFor(request.toJson());
      payment = await _paymentApi.submit(orderId, request, idempotencyKey: key);
      _idempotency.complete();
      submitting = false;
      notifyListeners();
      return true;
    } on ApiException catch (error) {
      // Jamais "Paiement echoue" sur une panne reseau pure : l'action est
      // idempotente (meme cle reutilisee tant que complete() n'a pas ete
      // appele), un nouvel envoi est donc sans danger (mission section 27).
      errorMessage = error.isNetworkFailure
          ? 'Impossible de confirmer la reponse du serveur. Vous pouvez reessayer en toute securite.'
          : error.message;
      submitting = false;
      notifyListeners();
      return false;
    }
  }

  Future<void> pickAndUploadProof() async {
    final currentPayment = payment;
    if (currentPayment == null || uploadingProof) return;

    // uploadingProof est active AVANT l'appel au picker natif (et non apres) :
    // un double-tap pendant que la galerie s'ouvre declenchait sinon un
    // second appel concurrent a pickImage, qui echoue avec
    // PlatformException('already_active') -- non rattrape plus bas avant ce
    // correctif, donc un tap totalement muet (bug reel signale par
    // l'utilisateur : "on arrive pas a upload").
    uploadingProof = true;
    errorMessage = null;
    notifyListeners();
    try {
      final XFile? picked = await _imagePicker.pickImage(source: ImageSource.gallery, imageQuality: 90);
      if (picked == null) {
        uploadingProof = false;
        notifyListeners();
        return;
      }
      await _paymentApi.uploadProof(currentPayment.id, picked.path, picked.name);
      proofUploaded = true;
    } on ApiException catch (error) {
      errorMessage = error.message;
    } catch (error) {
      // Le picker natif (permission refusee, appel concurrent, aucune galerie
      // disponible...) ou la lecture du fichier choisi peuvent echouer sans
      // jamais lever d'ApiException -- jamais un tap muet (mission section 38).
      errorMessage = "Impossible de selectionner ou d'envoyer ce fichier. Reessayez.";
    }
    uploadingProof = false;
    notifyListeners();
  }
}
