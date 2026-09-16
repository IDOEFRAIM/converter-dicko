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

  /// Ruee collective a laquelle cet ordre contribue, optionnelle (mission
  /// "differenciation marketing", Lot 3) — voir [OrderCreateArgs].
  final String? poolId;

  final _idempotency = IdempotencyAttempt();

  OrderCreateController({
    required OrderApi orderApi,
    required SupplierApi supplierApi,
    required this.quote,
    this.poolId,
  })  : _orderApi = orderApi,
        _supplierApi = supplierApi;

  bool loadingFeasibility = true;
  OrderFeasibility? feasibility;

  bool loadingSuppliers = true;
  List<SupplierSummary> suppliers = [];

  BeneficiarySource source = BeneficiarySource.manual;
  String? selectedSupplierId;

  bool submitting = false;
  String? errorMessage;
  String? errorCode;

  /// `KYC_VERIFICATION_REQUIRED` (voir `OrderService.assertKycVerifiedIfRequired`
  /// backend) merite un traitement visuel distinct d'une erreur generique : le
  /// recours est concret — verifier son identite en libre-service (`/more/kyc`,
  /// remarque produit #6) ou reduire le montant sous le seuil, jamais un simple
  /// "reessayez".
  bool get isKycBlocked => errorCode == 'KYC_VERIFICATION_REQUIRED';

  /// `INSUFFICIENT_TREASURY` (voir `TreasuryService.reserve` backend) reste un
  /// blocage reel -- la tresorerie CNY ne peut pas honorer plus qu'elle ne
  /// detient, invariant comptable impossible a assouplir cote client (retour
  /// beta-testeur sept. 2026 discute avec l'equipe : la RESTRICTION reste,
  /// seul le TON change). Le message brut backend expose des montants
  /// internes ("disponible X, demande Y") -- jamais affiche tel quel, on lui
  /// substitue un message neutre qui n'alarme pas et n'invite pas a
  /// re-essayer immediatement (le prochain essai echouerait pour la meme
  /// raison tant que la tresorerie n'est pas reapprovisionnee).
  bool get isInsufficientTreasury => errorCode == 'INSUFFICIENT_TREASURY';

  static const _insufficientTreasuryMessage =
      'Pour des raisons de maintenance, ce transfert va prendre un peu plus de temps que prevu. '
      'Reessayez un peu plus tard.';

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

  /// Rafraichit la liste des fournisseurs sans recharger tout l'ecran (feasibilite, devis...).
  ///
  /// Retour beta-testeur sept. 2026 : "meme quand il a pu l'ajouter [un fournisseur] et il
  /// revient, l'app prend du temps avant de se mettre a jour... il est oblige de ressortir, il
  /// doit encore revenir, refaire un autre ordre pour voir que le fournisseur a ete mis a jour".
  /// Cause exacte : ce controleur ne chargeait la liste des fournisseurs qu'UNE fois, a la
  /// creation de l'ecran (voir [load]) -- `_QrRequiredNotice` (order_create_page.dart) poussait
  /// `/suppliers/new` par-dessus sans jamais reconstruire ce controleur, donc sans jamais
  /// re-declencher `_loadSuppliers()`, meme apres un retour reussi.
  ///
  /// [selectId] (l'identifiant du fournisseur qui vient d'etre cree, transmis par
  /// `SupplierFormPage` au retour) est automatiquement selectionne s'il est bien present dans
  /// la liste rafraichie -- l'utilisateur n'a plus a re-choisir un fournisseur qu'il vient tout
  /// juste de creer pour cet ordre precis.
  Future<void> reloadSuppliers({String? selectId}) async {
    try {
      final page = await _supplierApi.list(size: 50, status: 'ACTIVE');
      suppliers = page.content;
      if (selectId != null && suppliers.any((s) => s.id == selectId)) {
        source = BeneficiarySource.supplier;
        selectedSupplierId = selectId;
      } else if (source == BeneficiarySource.supplier &&
          !suppliers.any((s) => s.id == selectedSupplierId)) {
        // Le fournisseur precedemment selectionne a disparu (desactive entre-temps...) : retombe
        // sur le premier disponible plutot que de garder un ID selectionne mais introuvable.
        selectedSupplierId = suppliers.isNotEmpty ? suppliers.first.id : null;
      }
      notifyListeners();
    } on ApiException {
      // Echec silencieux : la liste precedente reste affichee plutot que de vider l'ecran sur
      // une simple erreur reseau de rafraichissement (mission section 2, meme discipline que
      // _loadFeasibility).
    }
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
      poolId: poolId,
    );

    submitting = true;
    errorMessage = null;
    errorCode = null;
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
      errorCode = error.code;
      errorMessage = errorCode == 'INSUFFICIENT_TREASURY' ? _insufficientTreasuryMessage : error.message;
      submitting = false;
      notifyListeners();
      return null;
    }
  }
}
