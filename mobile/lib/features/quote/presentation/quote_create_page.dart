import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../core/theme/app_typography.dart';
import '../../../shared/models/money.dart';
import '../../../shared/utils/date_formatting.dart';
import '../../../shared/utils/validators.dart';
import '../../../shared/widgets/corridor.dart';
import '../../../shared/widgets/money_display.dart';
import '../../../shared/widgets/primary_action.dart';
import '../../orders/presentation/order_create_page.dart';
import '../application/quote_create_controller.dart';
import '../data/quote_api.dart';
import '../models/quote_models.dart';

/// "Vous envoyez / Le beneficiaire recoit" (mission section 20). Point
/// d'entree principal du parcours de paiement, accessible depuis l'onglet
/// "Payer" et depuis le bouton "Payer un fournisseur" du Home.
///
/// [poolId] optionnel (mission "differenciation marketing", Lot 3) : quand
/// non nul, l'ordre cree en bout de parcours contribuera a cette Ruee
/// collective (voir [OrderCreateArgs]).
class QuoteCreatePage extends StatelessWidget {
  final String? poolId;

  const QuoteCreatePage({super.key, this.poolId});

  @override
  Widget build(BuildContext context) {
    return ChangeNotifierProvider(
      create: (context) => QuoteCreateController(context.read<QuoteApi>()),
      child: _QuoteCreateView(poolId: poolId),
    );
  }
}

class _QuoteCreateView extends StatefulWidget {
  final String? poolId;

  const _QuoteCreateView({this.poolId});

  @override
  State<_QuoteCreateView> createState() => _QuoteCreateViewState();
}

class _QuoteCreateViewState extends State<_QuoteCreateView> {
  final _formKey = GlobalKey<FormState>();
  final _amountController = TextEditingController();

  @override
  void dispose() {
    _amountController.dispose();
    super.dispose();
  }

  Future<void> _requestQuote(QuoteCreateController controller) async {
    // Bug reel trouve en test : un montant vide faisait echouer silencieusement
    // le tap sur "Obtenir un devis" — aucune requete, aucun message.
    if (!_formKey.currentState!.validate()) {
      return;
    }
    await controller.createForAmountXof(_amountController.text.trim());
  }

  Future<void> _continueToOrder(QuoteCreateController controller) async {
    final accepted = await controller.accept();
    if (accepted == null || !mounted) return;
    // Route imbriquee dans l'onglet "Payer" (barre du bas toujours visible) —
    // une fois l'ordre cree, cet ecran navigue lui-meme en `go()` vers le
    // detail de l'ordre (onglet "Activite"), la transaction etant terminee.
    if (context.mounted) {
      context.push('/pay/orders/new', extra: OrderCreateArgs(quote: accepted, poolId: widget.poolId));
    }
  }

  @override
  Widget build(BuildContext context) {
    final controller = context.watch<QuoteCreateController>();
    final quote = controller.quote;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Payer un fournisseur'),
        actions: [
          if (widget.poolId == null)
            IconButton(
              onPressed: () => context.push('/pay/pools'),
              icon: const Icon(Icons.groups_outlined),
              tooltip: 'Mes Ruees',
            ),
        ],
      ),
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
      Form(
        key: _formKey,
        autovalidateMode: AutovalidateMode.onUserInteraction,
        child: TextFormField(
          controller: _amountController,
          keyboardType: const TextInputType.numberWithOptions(decimal: true),
          style: AppTypography.metricLarge,
          decoration: const InputDecoration(suffixText: 'XOF', hintText: '0'),
          onFieldSubmitted: (_) => _requestQuote(controller),
          validator: Validators.positiveAmount,
        ),
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
      if (quote.poolRewardApplied) ...[
        Container(
          width: double.infinity,
          padding: const EdgeInsets.all(AppSpacing.md),
          decoration: BoxDecoration(
            color: AppColors.positiveSurface,
            borderRadius: BorderRadius.circular(AppSpacing.radiusSm),
          ),
          child: Row(
            children: [
              const Icon(Icons.celebration_outlined, color: AppColors.positive, size: 18),
              const SizedBox(width: AppSpacing.sm),
              Expanded(
                child: Text(
                  'Reduction Ruee collective appliquee a ce devis !',
                  style: AppTypography.body.copyWith(color: AppColors.positive, fontWeight: FontWeight.w600),
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: AppSpacing.md),
      ],
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
              style: AppTypography.metricLarge.copyWith(color: Theme.of(context).colorScheme.primary),
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
