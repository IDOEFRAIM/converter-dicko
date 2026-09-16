import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../../core/auth/auth_session.dart';
import '../../../core/config/app_config.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../core/theme/app_typography.dart';
import '../../../core/theme/experience_theme.dart';
import '../../../shared/models/money.dart';
import '../../../shared/models/purpose.dart';
import '../../../shared/utils/validators.dart';
import '../../../shared/utils/whatsapp_launcher.dart';
import '../../../shared/widgets/icon_badge.dart';
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

  // La saisie manuelle ponctuelle ne peut jamais joindre de code QR (voir
  // BeneficiaryType.manualEntryOptions) -- seul le compte bancaire chinois
  // y a sa place, c'est donc l'unique valeur possible ici.
  final BeneficiaryType _manualType = BeneficiaryType.chineseBankAccount;
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
      final amount = double.tryParse(order.amountXof) ?? 0;
      if (amount >= AppConfig.whatsAppConfirmationThresholdXof) {
        await _showWhatsAppConfirmation(order);
      }
      if (!mounted) return;
      final poolId = controller.poolId;
      // Retour beta-testeur sept. 2026 : "je sais meme pas comment payer" -- atterrir sur le
      // detail de l'ordre forcait l'utilisateur a chercher lui-meme le bouton "Payer maintenant"
      // au milieu du reste (statut, beneficiaire...). Un ordre vient toujours de naitre
      // AWAITING_PAYMENT (voir OrderService.create backend) : l'etape suivante est TOUJOURS payer,
      // jamais un autre choix -- on y va donc directement, sans detour. Une contribution a une
      // Ruee collective reste une exception deliberee : son detail (thermometre, celebration
      // eventuelle) prime, l'ordre restant accessible depuis l'onglet Activite comme d'habitude.
      context.go(poolId == null ? '/activity/orders/${order.id}/payment' : '/pay/pools/$poolId');
    }
  }

  /// Retour client sept. 2026 : "a partir de plus de 02 millions tu dois etre
  /// ramene sur WhatsApp pour confirmer ton ordre au 71 00 25 25" -- l'ordre
  /// est deja cree normalement (aucune regle serveur ajoutee, voir
  /// `AppConfig.whatsAppConfirmationThresholdXof`), cette boite oriente
  /// simplement vers le canal humain attendu pour les gros montants.
  Future<void> _showWhatsAppConfirmation(OrderDetail order) async {
    final amountLabel = Money(order.amountXof, AppCurrency.xof).formattedWithCurrency();
    await showDialog<void>(
      context: context,
      barrierDismissible: false,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Confirmation par WhatsApp'),
        content: Text(
          'Votre transfert de $amountLabel doit etre confirme par WhatsApp au '
          '71 00 25 25 avant traitement. Indiquez la reference ${order.reference}.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(),
            child: const Text('Plus tard'),
          ),
          FilledButton.icon(
            onPressed: () async {
              await launchWhatsAppConfirmation(
                message: 'Bonjour, je confirme mon transfert de $amountLabel (reference ${order.reference}).',
              );
              if (dialogContext.mounted) Navigator.of(dialogContext).pop();
            },
            icon: const Icon(Icons.chat),
            label: const Text('Ouvrir WhatsApp'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final controller = context.watch<OrderCreateController>();
    final profile = context.watch<AuthSession>().experienceProfile;
    final sendAccent = ExperiencePalette.accentFor(profile, AccentRole.send);
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
                  'Liquidite peut-etre insuffisante. Vous pouvez continuer.',
                  style: AppTypography.body.copyWith(color: AppColors.warning),
                ),
              ),
            ],
            if ((double.tryParse(quote.amountXof) ?? 0) >= AppConfig.whatsAppConfirmationThresholdXof) ...[
              const SizedBox(height: AppSpacing.md),
              Container(
                padding: const EdgeInsets.all(AppSpacing.md),
                decoration: BoxDecoration(
                  color: AppColors.warningSurface,
                  borderRadius: BorderRadius.circular(AppSpacing.radiusSm),
                ),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Icon(Icons.chat, size: 18, color: AppColors.warning),
                    const SizedBox(width: AppSpacing.xs),
                    Expanded(
                      child: Text(
                        'Au-dela de 2 000 000 XOF, une confirmation par WhatsApp au 71 00 25 25 '
                        'vous sera demandee apres la creation du transfert.',
                        style: AppTypography.body.copyWith(color: AppColors.warning),
                      ),
                    ),
                  ],
                ),
              ),
            ],
            const SizedBox(height: AppSpacing.xl),
            Row(
              children: [
                IconBadge(
                  icon: Icons.storefront_outlined,
                  color: sendAccent.color,
                  background: sendAccent.surface,
                  size: 32,
                ),
                const SizedBox(width: AppSpacing.sm),
                Text('BENEFICIAIRE', style: AppTypography.eyebrow),
              ],
            ),
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
                _buildManualForm(controller),
            ],
            const SizedBox(height: AppSpacing.xl),
            Text('MOTIF', style: AppTypography.eyebrow),
            const SizedBox(height: AppSpacing.sm),
            DropdownButtonFormField<Purpose?>(
              initialValue: _purpose,
              decoration: const InputDecoration(labelText: 'Motif (optionnel)'),
              items: [
                const DropdownMenuItem<Purpose?>(value: null, child: Text('Non precise')),
                ...Purpose.options.map((p) => DropdownMenuItem<Purpose?>(value: p, child: Text(p.label))),
              ],
              onChanged: (value) => setState(() => _purpose = value),
            ),
            const SizedBox(height: AppSpacing.md),
            TextField(
              controller: _purposeDetailsController,
              decoration: const InputDecoration(labelText: 'Precisions'),
              maxLength: 500,
            ),
            if (controller.errorMessage != null) ...[
              const SizedBox(height: AppSpacing.md),
              if (controller.isKycBlocked)
                _KycBlockedNotice(message: controller.errorMessage!)
              else if (controller.isInsufficientTreasury)
                _MaintenanceDelayNotice(message: controller.errorMessage!)
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
      return Text('Aucun fournisseur. Renseignez un nouveau beneficiaire.', style: AppTypography.caption);
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

  Widget _buildManualForm(OrderCreateController controller) {
    return Form(
      key: _formKey,
      autovalidateMode: AutovalidateMode.onUserInteraction,
      child: Column(
        children: [
          // Alipay/WeChat Pay retires de la saisie manuelle (retour client :
          // "pour alipay et wechat on doit forcement avoir un qrcode") -- cette
          // requete ne peut joindre aucune image, donc aucun texte ne doit
          // jamais tenir lieu de code QR. Le seul chemin correct passe par un
          // fournisseur enregistre (le vrai code QR y est televerse et
          // valide -- voir SupplierFormPage).
          //
          // Retour beta-testeur sept. 2026 : revenir de "Ajouter un fournisseur" laissait cet
          // ecran affiche tel quel, sans le nouveau fournisseur nulle part -- l'utilisateur devait
          // quitter puis recommencer un ordre pour le voir apparaitre. On attend maintenant le
          // retour (l'id du fournisseur cree, voir SupplierFormPage) pour rafraichir la liste ET
          // basculer directement sur ce fournisseur, sans quitter cet ecran.
          _QrRequiredNotice(
            onAddSupplier: () async {
              final newSupplierId = await context.push<String>('/suppliers/new');
              if (newSupplierId != null && context.mounted) {
                await controller.reloadSuppliers(selectId: newSupplierId);
              }
            },
          ),
          const SizedBox(height: AppSpacing.md),
          TextFormField(
            controller: _fullNameController,
            maxLength: 120,
            decoration: const InputDecoration(labelText: 'Nom du beneficiaire'),
            validator: (v) => Validators.requiredMaxLength(v, 120, label: 'Le nom'),
          ),
          const SizedBox(height: AppSpacing.md),
          TextFormField(
            controller: _identifierController,
            maxLength: 120,
            decoration: const InputDecoration(labelText: 'Numero de compte bancaire'),
            validator: (v) => Validators.requiredMaxLength(v, 120, label: 'Ce champ'),
          ),
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
      ),
    );
  }
}

/// Alipay/WeChat Pay retires de la saisie manuelle ponctuelle (voir
/// `_buildManualForm`) : le vrai code QR n'a nulle part ou etre televerse
/// dans cette requete. Oriente explicitement vers le fournisseur enregistre,
/// seul chemin ou le QR est reellement demande et valide.
class _QrRequiredNotice extends StatelessWidget {
  final VoidCallback onAddSupplier;

  const _QrRequiredNotice({required this.onAddSupplier});

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
              const Icon(Icons.qr_code_2, size: 18, color: AppColors.warning),
              const SizedBox(width: AppSpacing.xs),
              Text('Alipay et WeChat Pay ?', style: AppTypography.bodyStrong.copyWith(color: AppColors.warning)),
            ],
          ),
          const SizedBox(height: AppSpacing.xs),
          Text(
            'Un compte Alipay ou WeChat Pay s\'identifie par son code QR, jamais par un texte. '
            'Enregistrez ce beneficiaire comme fournisseur pour y joindre le vrai code QR '
            '-- ce formulaire ne concerne que les comptes bancaires chinois.',
            style: AppTypography.body.copyWith(color: AppColors.warning),
          ),
          const SizedBox(height: AppSpacing.sm),
          OutlinedButton.icon(
            onPressed: onAddSupplier,
            icon: const Icon(Icons.qr_code_2, size: 18),
            label: const Text('Ajouter un fournisseur Alipay/WeChat'),
            style: OutlinedButton.styleFrom(foregroundColor: AppColors.warning),
          ),
        ],
      ),
    );
  }
}

/// Traitement visuel distinct pour `KYC_VERIFICATION_REQUIRED` (voir
/// `OrderCreateController.isKycBlocked`) -- un simple "reessayez" en rouge
/// serait trompeur : le client a besoin d'une action concrete (verifier son
/// identite via `/more/kyc`, ou reduire le montant sous le seuil), jamais un
/// ecran qui suggere qu'un nouveau tap suffirait (mission section 38).
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
              Text('Identite a verifier', style: AppTypography.bodyStrong.copyWith(color: AppColors.warning)),
            ],
          ),
          const SizedBox(height: AppSpacing.xs),
          Text(message, style: AppTypography.body.copyWith(color: AppColors.warning)),
          const SizedBox(height: AppSpacing.sm),
          OutlinedButton.icon(
            onPressed: () => context.push('/more/kyc'),
            icon: const Icon(Icons.verified_user_outlined, size: 18),
            label: const Text('Verifier mon identite'),
            style: OutlinedButton.styleFrom(foregroundColor: AppColors.warning),
          ),
        ],
      ),
    );
  }
}

/// Traitement visuel distinct pour `INSUFFICIENT_TREASURY` (voir
/// `OrderCreateController.isInsufficientTreasury`) -- volontairement discret
/// et non alarmant (pas de rouge, pas d'icone d'erreur) : le blocage reste
/// reel cote serveur, mais rien ici ne doit inquieter l'utilisateur ni
/// suggerer un probleme de son cote.
class _MaintenanceDelayNotice extends StatelessWidget {
  final String message;

  const _MaintenanceDelayNotice({required this.message});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(AppSpacing.md),
      decoration: BoxDecoration(
        color: AppColors.warningSurface,
        borderRadius: BorderRadius.circular(AppSpacing.radiusSm),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Icon(Icons.schedule_outlined, size: 18, color: AppColors.warning),
          const SizedBox(width: AppSpacing.xs),
          Expanded(child: Text(message, style: AppTypography.body.copyWith(color: AppColors.warning))),
        ],
      ),
    );
  }
}
