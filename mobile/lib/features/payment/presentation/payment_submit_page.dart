import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../../core/auth/auth_session.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../core/theme/app_typography.dart';
import '../../../core/theme/experience_theme.dart';
import '../../../shared/models/money.dart';
import '../../../shared/utils/validators.dart';
import '../../../shared/widgets/error_state.dart';
import '../../../shared/widgets/icon_badge.dart';
import '../../../shared/widgets/loading_view.dart';
import '../../../shared/widgets/primary_action.dart';
import '../../../shared/widgets/proof_dropzone.dart';
import '../../orders/data/order_api.dart';
import '../../settings/data/settings_api.dart';
import '../application/payment_submit_controller.dart';
import '../data/payment_api.dart';
import '../models/payment_models.dart';

/// "Paiement du transfert" (mission section 26) : declaration du paiement
/// puis ajout de la preuve. Gere explicitement le cas reseau (section 27) :
/// jamais "Paiement echoue" sur une simple panne de connexion.
class PaymentSubmitPage extends StatelessWidget {
  final String orderId;

  const PaymentSubmitPage({super.key, required this.orderId});

  @override
  Widget build(BuildContext context) {
    return ChangeNotifierProvider(
      create: (context) => PaymentSubmitController(
        orderApi: context.read<OrderApi>(),
        paymentApi: context.read<PaymentApi>(),
        settingsApi: context.read<SettingsApi>(),
        orderId: orderId,
      )..load(),
      child: const _PaymentSubmitView(),
    );
  }
}

class _PaymentSubmitView extends StatefulWidget {
  const _PaymentSubmitView();

  @override
  State<_PaymentSubmitView> createState() => _PaymentSubmitViewState();
}

class _PaymentSubmitViewState extends State<_PaymentSubmitView> {
  final _formKey = GlobalKey<FormState>();
  final _phoneController = TextEditingController();
  final _payerNameController = TextEditingController();
  PaymentMethod? _method;

  @override
  void initState() {
    super.initState();
    // Retour beta-testeur sept. 2026 : simplifier le formulaire -- dans l'immense majorite des
    // cas, le payeur EST le titulaire du compte (pas un tiers). Pre-remplir avec ses propres
    // nom/numero lui evite de re-taper ce qu'il connait deja ; les champs restent modifiables
    // pour le cas ou quelqu'un d'autre a effectue le paiement en son nom.
    final user = context.read<AuthSession>().currentUser;
    if (user != null) {
      _payerNameController.text = '${user.firstName} ${user.lastName}'.trim();
      _phoneController.text = user.phone;
    }
  }

  @override
  void dispose() {
    _phoneController.dispose();
    _payerNameController.dispose();
    super.dispose();
  }

  Future<void> _submit(PaymentSubmitController controller) async {
    // Bug reel trouve en test reel : sans ce controle explicite, un
    // formulaire incomplet faisait echouer silencieusement le tap sur
    // "Envoyer" — aucune requete, aucun message, l'utilisateur croyait
    // l'application cassee. `Form.validate()` force maintenant l'affichage
    // du message d'erreur sous le champ concerne.
    if (!_formKey.currentState!.validate()) {
      return;
    }
    final method = _method ?? controller.enabledMethods.firstOrNull;
    if (method == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Aucun moyen de paiement disponible pour le moment.')),
      );
      return;
    }
    await controller.submit(
      method: method,
      payerPhone: _phoneController.text.trim(),
      payerName: _payerNameController.text.trim(),
    );
  }

  @override
  Widget build(BuildContext context) {
    final controller = context.watch<PaymentSubmitController>();
    final profile = context.watch<AuthSession>().experienceProfile;
    final sendAccent = ExperiencePalette.accentFor(profile, AccentRole.send);

    return Scaffold(
      appBar: AppBar(title: const Text('Paiement du transfert')),
      body: SafeArea(
        child: Builder(
          builder: (context) {
            if (controller.loading) {
              return const LoadingView();
            }
            final order = controller.order;
            if (order == null) {
              return ErrorState(message: controller.loadError ?? 'Impossible de charger cet ordre.', onRetry: controller.load);
            }

            final methods = controller.enabledMethods;
            _method ??= methods.firstOrNull;

            return SingleChildScrollView(
              padding: const EdgeInsets.all(AppSpacing.lg),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      IconBadge(
                        icon: Icons.payments_outlined,
                        color: sendAccent.color,
                        background: sendAccent.surface,
                        size: 32,
                      ),
                      const SizedBox(width: AppSpacing.sm),
                      Text('A PAYER', style: AppTypography.eyebrow),
                    ],
                  ),
                  const SizedBox(height: AppSpacing.xs),
                  Text(
                    Money(order.amountXof, AppCurrency.xof).formattedWithCurrency(),
                    style: AppTypography.metricLarge,
                  ),
                  Text('#${order.reference}', style: AppTypography.caption),
                  const SizedBox(height: AppSpacing.xl),
                  if (controller.payment == null)
                    ..._buildDeclarationStep(controller, methods)
                  else
                    ..._buildProofStep(context, controller),
                ],
              ),
            );
          },
        ),
      ),
    );
  }

  List<Widget> _buildDeclarationStep(PaymentSubmitController controller, List<PaymentMethod> methods) {
    final instructions = controller.settings?.paymentInstructionsText ?? '';
    return [
      // Retour beta-testeur sept. 2026 : "j'arrive jusqu'a faire l'order... je sais meme pas
      // comment payer", puis "il est oblige de tourner et reflechir" -- fusionner "envoyer
      // l'argent" et "remplir le formulaire" sous un seul step "Payez" melangeait deux actions
      // bien distinctes (une externe, une dans l'app). Separees en deux etapes numerotees, la
      // suite devient evidente sans reflechir : ici -> la-bas -> ici de nouveau.
      const _StepLabel(number: 1, label: "Envoyez l'argent"),
      const SizedBox(height: AppSpacing.md),
      if (instructions.isNotEmpty)
        Container(
          padding: const EdgeInsets.all(AppSpacing.md),
          decoration: BoxDecoration(
            color: AppColors.ivoryDim,
            borderRadius: BorderRadius.circular(AppSpacing.radiusSm),
            border: Border.all(color: AppColors.outline),
          ),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Icon(Icons.info_outline, size: 18, color: AppColors.navy),
              const SizedBox(width: AppSpacing.xs),
              Expanded(child: Text(instructions, style: AppTypography.body.copyWith(color: AppColors.navy))),
            ],
          ),
        ),
      const SizedBox(height: AppSpacing.xl),
      const _StepLabel(number: 2, label: 'Confirmez votre paiement'),
      const SizedBox(height: AppSpacing.xs),
      // Explique le POURQUOI, pas seulement le QUOI (retour beta-testeur : le concept meme de
      // "declarer" un paiement deja envoye n'etait pas evident) -- personne n'a encore vu que
      // l'argent est arrive, cette etape est ce qui permet de le verifier et debloquer le transfert.
      Text(
        "Deja envoye ? Confirmez ci-dessous pour qu'on verifie et debloque votre transfert.",
        style: AppTypography.caption,
      ),
      const SizedBox(height: AppSpacing.md),
      Form(
        key: _formKey,
        autovalidateMode: AutovalidateMode.onUserInteraction,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (methods.length > 1) ...[
              DropdownButtonFormField<PaymentMethod>(
                initialValue: _method,
                decoration: const InputDecoration(labelText: 'Moyen de paiement'),
                items: methods.map((m) => DropdownMenuItem(value: m, child: Text(m.label))).toList(growable: false),
                onChanged: (value) => setState(() => _method = value),
              ),
              const SizedBox(height: AppSpacing.md),
            ],
            // Pre-rempli avec les coordonnees du compte (voir initState) -- rarement a modifier,
            // seulement si quelqu'un d'autre a effectue le paiement pour ce transfert.
            TextFormField(
              controller: _payerNameController,
              maxLength: 160,
              decoration: const InputDecoration(labelText: 'Nom du payeur *'),
              validator: (value) => Validators.requiredMaxLength(value, 160, label: 'Le nom du payeur'),
            ),
            const SizedBox(height: AppSpacing.md),
            TextFormField(
              controller: _phoneController,
              keyboardType: TextInputType.phone,
              maxLength: 20,
              decoration: const InputDecoration(labelText: 'Numero du payeur *'),
              validator: (value) => Validators.requiredMaxLength(value, 20, label: 'Le numero du payeur'),
            ),
          ],
        ),
      ),
      if (controller.errorMessage != null) ...[
        const SizedBox(height: AppSpacing.md),
        Container(
          padding: const EdgeInsets.all(AppSpacing.md),
          decoration: BoxDecoration(
            color: AppColors.warningSurface,
            borderRadius: BorderRadius.circular(AppSpacing.radiusSm),
          ),
          child: Text(controller.errorMessage!, style: AppTypography.body.copyWith(color: AppColors.warning)),
        ),
      ],
      const SizedBox(height: AppSpacing.xl),
      PrimaryAction(
        label: 'Envoyer',
        loading: controller.submitting,
        onPressed: () => _submit(controller),
      ),
    ];
  }

  List<Widget> _buildProofStep(BuildContext context, PaymentSubmitController controller) {
    // Retour client oct. 2026 : "confirmer le paiement avec une capture est plus simple" --
    // un administrateur ne peut PAS confirmer un paiement sans preuve tant que
    // REQUIRE_PAYMENT_PROOF est actif (voir PaymentService.confirm, backend). Avant ce
    // correctif, "Voir le transfert" restait toujours actionnable ici, meme sans preuve
    // jointe : l'utilisateur croyait avoir termine, alors que son transfert resterait bloque
    // indefiniment cote administration, sans qu'il en soit jamais informe.
    final proofRequired = controller.settings?.requirePaymentProof ?? true;
    final canFinish = controller.proofUploaded || !proofRequired;
    return [
      Container(
        padding: const EdgeInsets.all(AppSpacing.md),
        decoration: BoxDecoration(
          color: AppColors.positiveSurface,
          borderRadius: BorderRadius.circular(AppSpacing.radiusSm),
        ),
        child: Row(
          children: [
            const Icon(Icons.check_circle, color: AppColors.positive, size: 18),
            const SizedBox(width: AppSpacing.sm),
            const Expanded(child: Text('Paiement declare · en verification')),
          ],
        ),
      ),
      const SizedBox(height: AppSpacing.xl),
      _StepLabel(number: 3, label: proofRequired ? 'Ajoutez la preuve *' : 'Ajoutez la preuve'),
      const SizedBox(height: AppSpacing.xs),
      Text(
        proofRequired
            ? 'Obligatoire : sans capture de votre paiement, notre equipe ne peut pas debloquer votre transfert.'
            : 'Facultatif, mais accelere la verification par notre equipe.',
        style: AppTypography.caption,
      ),
      const SizedBox(height: AppSpacing.md),
      ProofDropzone(
        uploading: controller.uploadingProof,
        done: controller.proofUploaded,
        previewPath: controller.proofPreviewPath,
        fileName: controller.proofFileName,
        onTap: controller.uploadingProof || controller.proofUploaded
            ? null
            : controller.pickAndUploadProof,
      ),
      if (controller.errorMessage != null) ...[
        const SizedBox(height: AppSpacing.md),
        Text(controller.errorMessage!, style: AppTypography.body.copyWith(color: AppColors.negative)),
      ],
      const SizedBox(height: AppSpacing.xl),
      SizedBox(
        width: double.infinity,
        child: ElevatedButton(
          onPressed: canFinish ? () => context.go('/activity/orders/${controller.orderId}') : null,
          child: const Text('Voir le transfert'),
        ),
      ),
    ];
  }
}

class _StepLabel extends StatelessWidget {
  final int number;
  final String label;

  const _StepLabel({required this.number, required this.label});

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        CircleAvatar(
          radius: 12,
          backgroundColor: Theme.of(context).colorScheme.primary,
          child: Text('$number', style: const TextStyle(color: Colors.white, fontSize: 12, fontWeight: FontWeight.w700)),
        ),
        const SizedBox(width: AppSpacing.sm),
        Text(label, style: AppTypography.bodyStrong),
      ],
    );
  }
}

extension _FirstOrNull<T> on List<T> {
  T? get firstOrNull => isEmpty ? null : first;
}
