import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../core/theme/app_typography.dart';
import '../../../shared/models/money.dart';
import '../../../shared/models/purpose.dart';
import '../../../shared/utils/validators.dart';
import '../../../shared/widgets/loading_view.dart';
import '../../../shared/widgets/primary_action.dart';
import '../../orders/data/order_api.dart';
import '../../quote/models/quote_models.dart';
import '../../suppliers/data/supplier_api.dart';
import '../../suppliers/models/supplier_models.dart';
import '../application/order_create_controller.dart';
import '../models/order_models.dart';

/// Regroupe le devis accepte et l'eventuelle Ruee collective a laquelle cet
/// ordre doit contribuer (mission "differenciation marketing", Lot 3) — un
/// seul objet transmissible via `extra` au routeur, plutot que deux valeurs
/// distinctes qui se perdraient l'une l'autre entre les deux ecrans.
class OrderCreateArgs {
  final Quote quote;
  final String? poolId;

  const OrderCreateArgs({required this.quote, this.poolId});
}

/// Choix du beneficiaire (fournisseur enregistre ou saisie manuelle) + motif,
/// puis creation de l'ordre a partir d'un devis deja accepte (mission
/// section 24, entree du parcours).
class OrderCreatePage extends StatelessWidget {
  final Quote quote;
  final String? poolId;

  const OrderCreatePage({super.key, required this.quote, this.poolId});

  @override
  Widget build(BuildContext context) {
    return ChangeNotifierProvider(
      create: (context) => OrderCreateController(
        orderApi: context.read<OrderApi>(),
        supplierApi: context.read<SupplierApi>(),
        quote: quote,
        poolId: poolId,
      )..load(),
      child: const _OrderCreateView(),
    );
  }
}

class _OrderCreateView extends StatefulWidget {
  const _OrderCreateView();

  @override
  State<_OrderCreateView> createState() => _OrderCreateViewState();
}

class _OrderCreateViewState extends State<_OrderCreateView> {
  final _formKey = GlobalKey<FormState>();
  final _fullNameController = TextEditingController();
  final _identifierController = TextEditingController();
  final _bankNameController = TextEditingController();
  final _bankBranchController = TextEditingController();
  final _purposeDetailsController = TextEditingController();

  BeneficiaryType _manualType = BeneficiaryType.alipay;
  Purpose? _purpose;

  @override
  void dispose() {
    _fullNameController.dispose();
    _identifierController.dispose();
    _bankNameController.dispose();
    _bankBranchController.dispose();
    _purposeDetailsController.dispose();
    super.dispose();
  }

  Future<void> _submit(OrderCreateController controller) async {
    if (controller.source == BeneficiarySource.manual && !_formKey.currentState!.validate()) {
      return;
    }
    final manualBeneficiary = controller.source == BeneficiarySource.manual
        ? BeneficiaryRequest(
            type: _manualType,
            fullName: _fullNameController.text.trim(),
            identifier: _identifierController.text.trim(),
            bankName: _bankNameController.text.trim().isEmpty ? null : _bankNameController.text.trim(),
            bankBranch: _bankBranchController.text.trim().isEmpty ? null : _bankBranchController.text.trim(),
          )
        : null;

    final order = await controller.submit(
      manualBeneficiary: manualBeneficiary,
      purpose: _purpose,
      purposeDetails: _purposeDetailsController.text.trim().isEmpty ? null : _purposeDetailsController.text.trim(),
    );
    if (order != null && mounted) {
      final poolId = controller.poolId;
      // Une contribution a une Ruee collective merite de revenir sur son detail (thermometre a
      // jour, celebration eventuelle) plutot que sur l'ordre lui-meme — l'ordre reste accessible
      // depuis l'onglet Activite comme d'habitude.
      context.go(poolId == null ? '/activity/orders/${order.id}' : '/pay/pools/$poolId');
    }
  }

  @override
  Widget build(BuildContext context) {
    final controller = context.watch<OrderCreateController>();
    final quote = controller.quote;

    return Scaffold(
      appBar: AppBar(title: const Text('Beneficiaire')),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(AppSpacing.lg),
          children: [
            Container(
              padding: const EdgeInsets.all(AppSpacing.lg),
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(AppSpacing.radiusMd),
                border: Border.all(color: AppColors.outline),
              ),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(Money(quote.amountXof, AppCurrency.xof).formattedWithCurrency(), style: AppTypography.bodyStrong),
                  const Icon(Icons.arrow_forward, size: 16, color: AppColors.inkFaint),
                  Text(
                    Money(quote.amountCny, AppCurrency.cny).formattedWithCurrency(),
                    style: AppTypography.bodyStrong.copyWith(color: Theme.of(context).colorScheme.primary),
                  ),
                ],
              ),
            ),
            if (!controller.loadingFeasibility &&
                controller.feasibility != null &&
                !controller.feasibility!.sufficientLiquidity) ...[
              const SizedBox(height: AppSpacing.md),
              Container(
                padding: const EdgeInsets.all(AppSpacing.md),
                decoration: BoxDecoration(
                  color: AppColors.warningSurface,
                  borderRadius: BorderRadius.circular(AppSpacing.radiusSm),
                ),
                child: Text(
                  'La liquidite pourrait etre insuffisante pour ce montant. Vous pouvez tout de meme continuer.',
                  style: AppTypography.body.copyWith(color: AppColors.warning),
                ),
              ),
            ],
            const SizedBox(height: AppSpacing.xl),
            Text('BENEFICIAIRE', style: AppTypography.eyebrow),
            const SizedBox(height: AppSpacing.sm),
            if (controller.loadingSuppliers)
              const LoadingView()
            else ...[
              if (controller.suppliers.isNotEmpty)
                SegmentedButton<BeneficiarySource>(
                  segments: const [
                    ButtonSegment(value: BeneficiarySource.supplier, label: Text('Fournisseur enregistre')),
                    ButtonSegment(value: BeneficiarySource.manual, label: Text('Nouveau')),
                  ],
                  selected: {controller.source},
                  onSelectionChanged: (selection) => controller.selectSource(selection.first),
                ),
              const SizedBox(height: AppSpacing.md),
              if (controller.source == BeneficiarySource.supplier)
                _buildSupplierPicker(controller)
              else
                _buildManualForm(),
            ],
            const SizedBox(height: AppSpacing.xl),
            Text('MOTIF (OPTIONNEL)', style: AppTypography.eyebrow),
            const SizedBox(height: AppSpacing.sm),
            DropdownButtonFormField<Purpose?>(
              initialValue: _purpose,
              decoration: const InputDecoration(labelText: 'Motif'),
              items: [
                const DropdownMenuItem<Purpose?>(value: null, child: Text('Non precise')),
                ...Purpose.options.map((p) => DropdownMenuItem<Purpose?>(value: p, child: Text(p.label))),
              ],
              onChanged: (value) => setState(() => _purpose = value),
            ),
            const SizedBox(height: AppSpacing.md),
            TextField(
              controller: _purposeDetailsController,
              decoration: const InputDecoration(labelText: 'Precisions (optionnel)'),
              maxLength: 500,
            ),
            if (controller.errorMessage != null) ...[
              const SizedBox(height: AppSpacing.md),
              if (controller.isKycBlocked)
                _KycBlockedNotice(message: controller.errorMessage!)
              else
                Text(controller.errorMessage!, style: AppTypography.body.copyWith(color: AppColors.negative)),
            ],
            const SizedBox(height: AppSpacing.lg),
            PrimaryAction(
              label: 'Creer le transfert',
              loading: controller.submitting,
              onPressed: _canSubmit(controller) ? () => _submit(controller) : null,
            ),
          ],
        ),
      ),
    );
  }

  bool _canSubmit(OrderCreateController controller) {
    if (controller.submitting) return false;
    if (controller.source == BeneficiarySource.supplier) {
      return controller.selectedSupplierId != null;
    }
    return true;
  }

  Widget _buildSupplierPicker(OrderCreateController controller) {
    if (controller.suppliers.isEmpty) {
      return Text(
        'Aucun fournisseur enregistre. Renseignez un nouveau beneficiaire.',
        style: AppTypography.caption,
      );
    }
    return RadioGroup<String>(
      groupValue: controller.selectedSupplierId,
      onChanged: (value) => controller.selectSupplier(value!),
      child: Column(
        children: controller.suppliers
            .map(
              (supplier) => RadioListTile<String>(
                contentPadding: EdgeInsets.zero,
                value: supplier.id,
                title: Text(supplier.displayName, style: AppTypography.bodyStrong),
                subtitle: Text('${supplier.type.label} · ${supplier.maskedAccountNumber}'),
              ),
            )
            .toList(growable: false),
      ),
    );
  }

  Widget _buildManualForm() {
    return Form(
      key: _formKey,
      autovalidateMode: AutovalidateMode.onUserInteraction,
      child: Column(
        children: [
          DropdownButtonFormField<BeneficiaryType>(
            initialValue: _manualType,
            decoration: const InputDecoration(labelText: 'Type de compte'),
            items: BeneficiaryType.selectableOptions
                .map((type) => DropdownMenuItem(value: type, child: Text(type.label)))
                .toList(growable: false),
            onChanged: (value) => setState(() => _manualType = value!),
          ),
          const SizedBox(height: AppSpacing.md),
          TextFormField(
            controller: _fullNameController,
            maxLength: 120,
            decoration: const InputDecoration(labelText: 'Nom complet du beneficiaire'),
            validator: (v) => Validators.requiredMaxLength(v, 120, label: 'Le nom'),
          ),
          const SizedBox(height: AppSpacing.md),
          TextFormField(
            controller: _identifierController,
            maxLength: 120,
            decoration: InputDecoration(
              labelText: _manualType == BeneficiaryType.chineseBankAccount
                  ? 'Numero de compte bancaire'
                  : 'Identifiant du compte',
            ),
            validator: (v) => Validators.requiredMaxLength(v, 120, label: 'Ce champ'),
          ),
          if (_manualType == BeneficiaryType.chineseBankAccount) ...[
            const SizedBox(height: AppSpacing.md),
            TextFormField(
              controller: _bankNameController,
              maxLength: 120,
              decoration: const InputDecoration(labelText: 'Nom de la banque'),
              validator: (v) => Validators.requiredMaxLength(v, 120, label: 'La banque'),
            ),
            const SizedBox(height: AppSpacing.md),
            TextFormField(
              controller: _bankBranchController,
              maxLength: 120,
              decoration: const InputDecoration(labelText: 'Agence (optionnel)'),
              validator: (v) => Validators.optionalMaxLength(v, 120, label: "L'agence"),
            ),
          ],
        ],
      ),
    );
  }
}

/// Traitement visuel distinct pour `KYC_VERIFICATION_REQUIRED` : aucune
/// verification d'identite en libre-service n'existe cote backend (voir
/// `OrderCreateController.isKycBlocked`) -- un simple "reessayez" en rouge
/// serait trompeur, le client a besoin d'une action concrete (reduire le
/// montant ou contacter le support), jamais un ecran qui suggere qu'un
/// nouveau tap suffirait (mission section 38).
class _KycBlockedNotice extends StatelessWidget {
  final String message;

  const _KycBlockedNotice({required this.message});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(AppSpacing.md),
      decoration: BoxDecoration(
        color: AppColors.warningSurface,
        borderRadius: BorderRadius.circular(AppSpacing.radiusSm),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Icon(Icons.verified_user_outlined, size: 18, color: AppColors.warning),
              const SizedBox(width: AppSpacing.xs),
              Text('Verification d\'identite requise', style: AppTypography.bodyStrong.copyWith(color: AppColors.warning)),
            ],
          ),
          const SizedBox(height: AppSpacing.xs),
          Text(message, style: AppTypography.body.copyWith(color: AppColors.warning)),
          const SizedBox(height: AppSpacing.xs),
          Text(
            'Reduisez le montant en dessous du seuil, ou contactez le support pour faire verifier votre identite.',
            style: AppTypography.caption.copyWith(color: AppColors.warning),
          ),
        ],
      ),
    );
  }
}
