import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../core/theme/app_typography.dart';
import '../../../shared/models/money.dart';
import '../../../shared/utils/validators.dart';
import '../../../shared/widgets/error_state.dart';
import '../../../shared/widgets/loading_view.dart';
import '../../../shared/widgets/primary_action.dart';
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
  final _referenceController = TextEditingController();
  final _phoneController = TextEditingController();
  PaymentMethod? _method;

  @override
  void dispose() {
    _referenceController.dispose();
    _phoneController.dispose();
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
      transactionReference: _referenceController.text.trim(),
      payerPhone: _phoneController.text.trim().isEmpty ? null : _phoneController.text.trim(),
    );
  }

  @override
  Widget build(BuildContext context) {
    final controller = context.watch<PaymentSubmitController>();

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
                  Text('A PAYER', style: AppTypography.eyebrow),
                  const SizedBox(height: AppSpacing.xs),
                  Text(
                    Money(order.amountXof, AppCurrency.xof).formattedWithCurrency(),
                    style: AppTypography.metricLarge,
                  ),
                  Text('Reference : #${order.reference}', style: AppTypography.caption),
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
    return [
      Form(
        key: _formKey,
        autovalidateMode: AutovalidateMode.onUserInteraction,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const _StepLabel(number: 1, label: 'Effectuez le paiement'),
            const SizedBox(height: AppSpacing.md),
            if (methods.length > 1) ...[
              DropdownButtonFormField<PaymentMethod>(
                initialValue: _method,
                decoration: const InputDecoration(labelText: 'Moyen de paiement'),
                items: methods.map((m) => DropdownMenuItem(value: m, child: Text(m.label))).toList(growable: false),
                onChanged: (value) => setState(() => _method = value),
              ),
              const SizedBox(height: AppSpacing.md),
            ],
            TextFormField(
              controller: _referenceController,
              maxLength: 100,
              decoration: const InputDecoration(labelText: 'Reference de transaction *'),
              validator: (value) => Validators.requiredMaxLength(value, 100, label: 'La reference de transaction'),
            ),
            const SizedBox(height: AppSpacing.md),
            TextFormField(
              controller: _phoneController,
              keyboardType: TextInputType.phone,
              maxLength: 20,
              decoration: const InputDecoration(labelText: 'Numero du payeur (optionnel)'),
              validator: (value) => Validators.optionalMaxLength(value, 20, label: 'Le numero du payeur'),
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
        label: controller.submitting ? 'Envoi en cours...' : 'Envoyer',
        loading: controller.submitting,
        onPressed: () => _submit(controller),
      ),
    ];
  }

  List<Widget> _buildProofStep(BuildContext context, PaymentSubmitController controller) {
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
            const Expanded(child: Text('Paiement declare. Notre equipe va le verifier.')),
          ],
        ),
      ),
      const SizedBox(height: AppSpacing.xl),
      const _StepLabel(number: 2, label: 'Ajoutez la preuve'),
      const SizedBox(height: AppSpacing.md),
      if (controller.proofUploaded)
        Container(
          padding: const EdgeInsets.all(AppSpacing.md),
          decoration: BoxDecoration(
            color: AppColors.positiveSurface,
            borderRadius: BorderRadius.circular(AppSpacing.radiusSm),
          ),
          child: const Row(
            children: [
              Icon(Icons.check_circle, color: AppColors.positive, size: 18),
              SizedBox(width: AppSpacing.sm),
              Expanded(child: Text('Preuve ajoutee.')),
            ],
          ),
        )
      else
        OutlinedButton.icon(
          onPressed: controller.uploadingProof ? null : controller.pickAndUploadProof,
          icon: controller.uploadingProof
              ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2))
              : const Icon(Icons.attach_file),
          label: Text(controller.uploadingProof ? 'Envoi...' : 'Choisir un fichier'),
        ),
      if (controller.errorMessage != null) ...[
        const SizedBox(height: AppSpacing.md),
        Text(controller.errorMessage!, style: AppTypography.body.copyWith(color: AppColors.negative)),
      ],
      const SizedBox(height: AppSpacing.xl),
      SizedBox(
        width: double.infinity,
        child: ElevatedButton(
          onPressed: () => context.go('/activity/orders/${controller.orderId}'),
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
