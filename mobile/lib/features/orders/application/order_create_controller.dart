import 'package:flutter/foundation.dart';

import '../../../core/errors/api_exception.dart';
import '../../../core/network/idempotency.dart';
import '../../../shared/models/purpose.dart';
import '../../quote/models/quote_models.dart';
import '../../suppliers/data/supplier_api.dart';
import '../../suppliers/models/supplier_models.dart';
import '../data/order_api.dart';
import '../models/order_models.dart';

enum BeneficiarySource { supplier, manual }

/// Etat de l'ecran de creation d'ordre : verification de faisabilite,
/// source du beneficiaire (fournisseur enregistre ou saisie manuelle), motif,
/// puis creation de l'ordre. Suit exactement le contrat backend — aucune
/// validation "shadow" qui dupliquerait une regle serveur (mission section 2).
class OrderCreateController extends ChangeNotifier {
  final OrderApi _orderApi;
  final SupplierApi _supplierApi;
  final Quote quote;
  final _idempotency = IdempotencyAttempt();

  OrderCreateController({required OrderApi orderApi, required SupplierApi supplierApi, required this.quote})
      : _orderApi = orderApi,
        _supplierApi = supplierApi;

  bool loadingFeasibility = true;
  OrderFeasibility? feasibility;

  bool loadingSuppliers = true;
  List<SupplierSummary> suppliers = [];

  BeneficiarySource source = BeneficiarySource.manual;
  String? selectedSupplierId;

  bool submitting = false;
  String? errorMessage;

  Future<void> load() async {
    await Future.wait([_loadFeasibility(), _loadSuppliers()]);
  }

  Future<void> _loadFeasibility() async {
    loadingFeasibility = true;
    notifyListeners();
    try {
      feasibility = await _orderApi.checkFeasibility(quote.id);
    } on ApiException {
      // Purement indicatif (mission section 2) : un echec ici ne bloque
      // jamais la suite du parcours, la verite reste la reservation faite a
      // la creation reelle de l'ordre.
      feasibility = null;
    }
    loadingFeasibility = false;
    notifyListeners();
  }

  Future<void> _loadSuppliers() async {
    loadingSuppliers = true;
    notifyListeners();
    try {
      final page = await _supplierApi.list(size: 50, status: 'ACTIVE');
      suppliers = page.content;
      // Un fournisseur deja enregistre est pre-selectionne par defaut (miroir
      // de la page Angular equivalente).
      if (suppliers.isNotEmpty) {
        source = BeneficiarySource.supplier;
        selectedSupplierId = suppliers.first.id;
      } else {
        source = BeneficiarySource.manual;
      }
    } on ApiException {
      suppliers = [];
    }
    loadingSuppliers = false;
    notifyListeners();
  }

  void selectSource(BeneficiarySource newSource) {
    source = newSource;
    notifyListeners();
  }

  void selectSupplier(String id) {
    selectedSupplierId = id;
    notifyListeners();
  }

  Future<OrderDetail?> submit({
    BeneficiaryRequest? manualBeneficiary,
    Purpose? purpose,
    String? purposeDetails,
  }) async {
    if (submitting) return null;

    final request = CreateOrderRequest(
      quoteId: quote.id,
      beneficiary: source == BeneficiarySource.manual ? manualBeneficiary : null,
      supplierId: source == BeneficiarySource.supplier ? selectedSupplierId : null,
      purpose: purpose,
      purposeDetails: purposeDetails,
    );

    submitting = true;
    errorMessage = null;
    notifyListeners();

    try {
      final key = _idempotency.keyFor(request.toJson());
      final order = await _orderApi.create(request, idempotencyKey: key);
      _idempotency.complete();
      submitting = false;
      notifyListeners();
      return order;
    } on ApiException catch (error) {
      // Ne PAS appeler complete() : un nouveau tap avec le meme montant/
      // beneficiaire doit rejouer la meme cle d'idempotence (mission
      // section 27) plutot que de risquer un doublon si la premiere
      // tentative avait en realite reussi cote serveur.
      errorMessage = error.message;
      submitting = false;
      notifyListeners();
      return null;
    }
  }
}
