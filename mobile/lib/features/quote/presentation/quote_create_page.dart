import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../../core/auth/auth_session.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../core/theme/app_typography.dart';
import '../../../core/theme/experience_theme.dart';
import '../../../shared/models/money.dart';
import '../../../shared/utils/date_formatting.dart';
import '../../../shared/utils/validators.dart';
import '../../../shared/widgets/corridor.dart';
import '../../../shared/widgets/icon_badge.dart';
import '../../../shared/widgets/primary_action.dart';
import '../../../shared/widgets/transfer_ticket.dart';
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
    final profile = context.watch<AuthSession>().experienceProfile;

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
              if (quote == null)
                ..._buildAmountForm(controller, profile)
              else
                ..._buildQuoteResult(controller, quote),
            ],
          ),
        ),
      ),
    );
  }

  List<Widget> _buildAmountForm(QuoteCreateController controller, ExperienceProfile profile) {
    final accent = ExperiencePalette.accentFor(profile, AccentRole.send);
    return [
      Row(
        children: [
          IconBadge(icon: Icons.send_outlined, color: accent.color, background: accent.surface, size: 32),
          const SizedBox(width: AppSpacing.sm),
          Text('VOUS ENVOYEZ', style: AppTypography.eyebrow),
        ],
      ),
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
        label: 'Continuer',
        loading: controller.creating,
        onPressed: () => _requestQuote(controller),
      ),
    ];
  }

  List<Widget> _buildQuoteResult(QuoteCreateController controller, Quote quote) {
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
                  'Remise Ruee appliquee',
                  style: AppTypography.body.copyWith(color: AppColors.positive, fontWeight: FontWeight.w600),
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: AppSpacing.md),
      ],
      TransferTicket(
        sendAmount: Money(quote.amountXof, AppCurrency.xof),
        receiveAmount: Money(quote.amountCny, AppCurrency.cny),
        receiveApprox: true,
        rows: [
          TicketRow('Taux', '1 CNY = ${quote.customerRate} XOF'),
          TicketRow('Frais', Money(quote.feeXof, AppCurrency.xof).formattedWithCurrency()),
        ],
        footnote: Text(
          isExpired
              ? 'Devis expire.'
              : 'Valable jusqu\'a ${DateFormatting.dayTime(quote.expiresAt)}.',
          style: AppTypography.caption.copyWith(color: isExpired ? AppColors.negative : AppColors.inkMuted),
        ),
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
          child: const Text('Modifier'),
        ),
      ),
    ];
  }
}
