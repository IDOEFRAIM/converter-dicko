import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../core/theme/app_typography.dart';
import '../../../shared/models/money.dart';
import '../../../shared/utils/date_formatting.dart';
import '../../../shared/widgets/corridor.dart';
import '../../../shared/widgets/money_display.dart';
import '../../../shared/widgets/primary_action.dart';
import '../application/quote_create_controller.dart';
import '../data/quote_api.dart';
import '../models/quote_models.dart';

/// "Vous envoyez / Le beneficiaire recoit" (mission section 20). Point
/// d'entree principal du parcours de paiement, accessible depuis l'onglet
/// "Payer" et depuis le bouton "Payer un fournisseur" du Home.
class QuoteCreatePage extends StatelessWidget {
  const QuoteCreatePage({super.key});

  @override
  Widget build(BuildContext context) {
    return ChangeNotifierProvider(
      create: (context) => QuoteCreateController(context.read<QuoteApi>()),
      child: const _QuoteCreateView(),
    );
  }
}

class _QuoteCreateView extends StatefulWidget {
  const _QuoteCreateView();

  @override
  State<_QuoteCreateView> createState() => _QuoteCreateViewState();
}

class _QuoteCreateViewState extends State<_QuoteCreateView> {
  final _amountController = TextEditingController();

  @override
  void dispose() {
    _amountController.dispose();
    super.dispose();
  }

  Future<void> _requestQuote(QuoteCreateController controller) async {
    final amount = _amountController.text.trim();
    if (amount.isEmpty) return;
    await controller.createForAmountXof(amount);
  }

  Future<void> _continueToOrder(QuoteCreateController controller) async {
    final accepted = await controller.accept();
    if (accepted == null || !mounted) return;
    // Une fois l'ordre cree, l'ecran suivant navigue en `go()` (remplace la
    // pile) vers le detail de l'ordre : la transaction est consideree
    // terminee, on ne revient jamais en arriere sur ce formulaire de devis.
    if (context.mounted) {
      context.push('/orders/new', extra: accepted);
    }
  }

  @override
  Widget build(BuildContext context) {
    final controller = context.watch<QuoteCreateController>();
    final quote = controller.quote;

    return Scaffold(
      appBar: AppBar(title: const Text('Payer un fournisseur')),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(AppSpacing.lg),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Corridor(level: CorridorLevel.normal),
              const SizedBox(height: AppSpacing.xl),
              if (quote == null) ..._buildAmountForm(controller) else ..._buildQuoteResult(context, controller, quote),
            ],
          ),
        ),
      ),
    );
  }

  List<Widget> _buildAmountForm(QuoteCreateController controller) {
    return [
      Text('VOUS ENVOYEZ', style: AppTypography.eyebrow),
      const SizedBox(height: AppSpacing.sm),
      TextField(
        controller: _amountController,
        keyboardType: const TextInputType.numberWithOptions(decimal: false),
        style: AppTypography.metricLarge,
        decoration: const InputDecoration(suffixText: 'XOF', hintText: '0'),
        onSubmitted: (_) => _requestQuote(controller),
      ),
      if (controller.errorMessage != null) ...[
        const SizedBox(height: AppSpacing.md),
        Text(controller.errorMessage!, style: AppTypography.body.copyWith(color: AppColors.negative)),
      ],
      const SizedBox(height: AppSpacing.xl),
      PrimaryAction(
        label: 'Obtenir un devis',
        loading: controller.creating,
        onPressed: () => _requestQuote(controller),
      ),
    ];
  }

  List<Widget> _buildQuoteResult(BuildContext context, QuoteCreateController controller, Quote quote) {
    final isExpired = quote.isExpired;
    return [
      Container(
        width: double.infinity,
        padding: const EdgeInsets.all(AppSpacing.lg),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(AppSpacing.radiusMd),
          border: Border.all(color: AppColors.outline),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('VOUS ENVOYEZ', style: AppTypography.eyebrow),
            const SizedBox(height: AppSpacing.xs),
            MoneyDisplay(money: Money(quote.amountXof, AppCurrency.xof), size: MoneyDisplaySize.large),
            const SizedBox(height: AppSpacing.lg),
            const Center(child: Icon(Icons.arrow_downward, color: AppColors.inkFaint)),
            const SizedBox(height: AppSpacing.lg),
            Text('LE BENEFICIAIRE RECOIT', style: AppTypography.eyebrow),
            const SizedBox(height: AppSpacing.xs),
            Text(
              '≈ ${Money(quote.amountCny, AppCurrency.cny).formattedWithCurrency()}',
              style: AppTypography.metricLarge.copyWith(color: AppColors.navy),
            ),
            const SizedBox(height: AppSpacing.md),
            const Divider(),
            const SizedBox(height: AppSpacing.md),
            _kv('Taux', '1 CNY = ${quote.customerRate} XOF'),
            const SizedBox(height: AppSpacing.sm),
            _kv('Frais', Money(quote.feeXof, AppCurrency.xof).formattedWithCurrency()),
          ],
        ),
      ),
      const SizedBox(height: AppSpacing.md),
      Text(
        isExpired
            ? 'Ce devis a expire. Demandez-en un nouveau.'
            : 'Devis valable jusqu\'a ${DateFormatting.dayTime(quote.expiresAt)}.',
        style: AppTypography.caption.copyWith(color: isExpired ? AppColors.negative : AppColors.inkMuted),
      ),
      if (controller.errorMessage != null) ...[
        const SizedBox(height: AppSpacing.md),
        Text(controller.errorMessage!, style: AppTypography.body.copyWith(color: AppColors.negative)),
      ],
      const SizedBox(height: AppSpacing.xl),
      if (isExpired)
        PrimaryAction(label: 'Nouveau devis', onPressed: controller.reset)
      else
        PrimaryAction(
          label: 'Continuer',
          loading: controller.accepting,
          onPressed: () => _continueToOrder(controller),
        ),
      const SizedBox(height: AppSpacing.sm),
      Center(
        child: TextButton(
          onPressed: controller.accepting ? null : controller.reset,
          child: const Text('Modifier le montant'),
        ),
      ),
    ];
  }

  Widget _kv(String label, String value) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Text(label, style: AppTypography.caption),
        Text(value, style: AppTypography.bodyStrong),
      ],
    );
  }
}
