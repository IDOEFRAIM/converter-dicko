import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../core/theme/app_typography.dart';
import '../../../shared/models/money.dart';
import '../../../shared/utils/date_formatting.dart';
import '../../../shared/utils/validators.dart';
import '../../../shared/widgets/empty_state.dart';
import '../../../shared/widgets/error_state.dart';
import '../../../shared/widgets/loading_view.dart';
import '../../../shared/widgets/primary_action.dart';
import '../../../shared/widgets/status_badge.dart';
import '../application/preferred_rate_controller.dart';
import '../data/preferred_rate_api.dart';
import '../models/preferred_rate_models.dart';

/// "Taux preferentiel" : "echanger X XOF uniquement lorsque le taux atteint
/// ma cible" (mission, PreferredRateController). Le montant est immobilise
/// sur le Wallet des la creation — voir la doc de [PreferredRateController]
/// (application) : une creation echoue avec un message backend explicite
/// (solde Wallet insuffisant) si le client n'a pas de solde disponible.
class PreferredRatePage extends StatelessWidget {
  const PreferredRatePage({super.key});

  @override
  Widget build(BuildContext context) {
    return ChangeNotifierProvider(
      create: (context) => PreferredRateController(context.read<PreferredRateApi>())..load(),
      child: const _PreferredRateView(),
    );
  }
}

class _PreferredRateView extends StatefulWidget {
  const _PreferredRateView();

  @override
  State<_PreferredRateView> createState() => _PreferredRateViewState();
}

class _PreferredRateViewState extends State<_PreferredRateView> {
  final _formKey = GlobalKey<FormState>();
  final _amountController = TextEditingController();
  final _targetController = TextEditingController();

  @override
  void dispose() {
    _amountController.dispose();
    _targetController.dispose();
    super.dispose();
  }

  Future<void> _create(PreferredRateController controller) async {
    // Meme principe que partout ailleurs dans l'app : un formulaire
    // incomplet ne doit jamais faire echouer silencieusement le tap sur
    // "Creer la demande" (mission section 38).
    if (!_formKey.currentState!.validate()) {
      return;
    }
    final success = await controller.create(
      amountXof: _amountController.text.trim(),
      targetRate: _targetController.text.trim(),
    );
    if (success) {
      _amountController.clear();
      _targetController.clear();
    }
  }

  Future<void> _confirmCancel(PreferredRateController controller, PreferredRate request) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Annuler la demande'),
        content: Text(
          '${Money(request.amountXof, AppCurrency.xof).formattedWithCurrency()} seront restitues a votre solde.',
        ),
        actions: [
          TextButton(onPressed: () => Navigator.of(context).pop(false), child: const Text('Garder')),
          TextButton(onPressed: () => Navigator.of(context).pop(true), child: const Text('Annuler la demande')),
        ],
      ),
    );
    if (confirmed == true) {
      await controller.cancel(request);
    }
  }

  @override
  Widget build(BuildContext context) {
    final controller = context.watch<PreferredRateController>();

    return Scaffold(
      appBar: AppBar(title: const Text('Taux preferentiel')),
      body: SafeArea(
        child: RefreshIndicator(
          onRefresh: controller.load,
          color: Theme.of(context).colorScheme.primary,
          child: ListView(
            padding: const EdgeInsets.all(AppSpacing.lg),
            children: [
              Text(
                'Echangez automatiquement des que le taux atteint votre cible.',
                style: AppTypography.caption,
              ),
              const SizedBox(height: AppSpacing.lg),
              Container(
                padding: const EdgeInsets.all(AppSpacing.lg),
                decoration: BoxDecoration(
                  color: Colors.white,
                  border: Border.all(color: AppColors.outline),
                  borderRadius: BorderRadius.circular(AppSpacing.radiusMd),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('NOUVELLE DEMANDE', style: AppTypography.eyebrow),
                    const SizedBox(height: AppSpacing.md),
                    Form(
                      key: _formKey,
                      autovalidateMode: AutovalidateMode.onUserInteraction,
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          TextFormField(
                            controller: _amountController,
                            keyboardType: const TextInputType.numberWithOptions(decimal: true),
                            decoration: const InputDecoration(labelText: 'Montant', suffixText: 'XOF'),
                            validator: Validators.positiveAmount,
                          ),
                          const SizedBox(height: AppSpacing.md),
                          TextFormField(
                            controller: _targetController,
                            keyboardType: const TextInputType.numberWithOptions(decimal: true),
                            decoration: const InputDecoration(labelText: '1 CNY ≥ *', suffixText: 'XOF'),
                            validator: Validators.positiveRate,
                          ),
                        ],
                      ),
                    ),
                    if (controller.createErrorMessage != null) ...[
                      const SizedBox(height: AppSpacing.md),
                      Text(
                        controller.createErrorMessage!,
                        style: AppTypography.body.copyWith(color: AppColors.negative),
                      ),
                    ],
                    const SizedBox(height: AppSpacing.lg),
                    PrimaryAction(
                      label: 'Creer la demande',
                      loading: controller.creating,
                      onPressed: () => _create(controller),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: AppSpacing.xl),
              _buildList(controller),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildList(PreferredRateController controller) {
    if (controller.loading) {
      return const LoadingView();
    }
    if (controller.errorMessage != null && controller.requests.isEmpty) {
      return ErrorState(message: controller.errorMessage!, onRetry: controller.load);
    }
    if (controller.requests.isEmpty) {
      return const EmptyState(icon: Icons.trending_up, title: 'Aucune demande');
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (controller.activeRequests.isNotEmpty) ...[
          Text('EN COURS', style: AppTypography.eyebrow),
          const SizedBox(height: AppSpacing.sm),
          ...controller.activeRequests.map(
            (request) => _PreferredRateCard(
              request: request,
              cancelling: controller.isCancelling(request.id),
              onCancel: () => _confirmCancel(controller, request),
            ),
          ),
        ],
        if (controller.closedRequests.isNotEmpty) ...[
          const SizedBox(height: AppSpacing.lg),
          Text('TERMINEES', style: AppTypography.eyebrow),
          const SizedBox(height: AppSpacing.sm),
          ...controller.closedRequests.map((request) => _PreferredRateCard(request: request)),
        ],
      ],
    );
  }
}

class _PreferredRateCard extends StatelessWidget {
  final PreferredRate request;
  final bool cancelling;
  final VoidCallback? onCancel;

  const _PreferredRateCard({required this.request, this.cancelling = false, this.onCancel});

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: AppSpacing.sm),
      padding: const EdgeInsets.all(AppSpacing.md),
      decoration: BoxDecoration(
        color: Colors.white,
        border: Border.all(color: AppColors.outline),
        borderRadius: BorderRadius.circular(AppSpacing.radiusMd),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                Money(request.amountXof, AppCurrency.xof).formattedWithCurrency(),
                style: AppTypography.bodyStrong,
              ),
              StatusBadge(status: request.status.code),
            ],
          ),
          const SizedBox(height: AppSpacing.xs),
          ..._buildPhaseContent(context),
          Text(
            'Cree le ${DateFormatting.dayOnly(request.createdAt)}',
            style: AppTypography.caption,
          ),
          if (onCancel != null && request.phase == PreferredRatePhase.waiting) ...[
            const SizedBox(height: AppSpacing.sm),
            Align(
              alignment: Alignment.centerRight,
              child: TextButton(
                onPressed: cancelling ? null : onCancel,
                child: Text(cancelling ? 'Annulation...' : 'Annuler'),
              ),
            ),
          ],
        ],
      ),
    );
  }

  List<Widget> _buildPhaseContent(BuildContext context) {
    switch (request.phase) {
      case PreferredRatePhase.waiting:
        return [
          Text('Des que 1 CNY ≥ ${request.targetRate} XOF', style: AppTypography.body),
          if (request.currentRate != null) ...[
            const SizedBox(height: AppSpacing.xs),
            Text('Taux actuel : 1 CNY = ${request.currentRate} XOF', style: AppTypography.caption),
          ],
          const SizedBox(height: AppSpacing.xs),
          Text('Expire le ${DateFormatting.dayTime(request.expiresAt)}', style: AppTypography.caption),
          const SizedBox(height: AppSpacing.xs),
        ];
      case PreferredRatePhase.exchangeInProgress:
        final exchange = request.exchange;
        return [
          Text(
            'Taux atteint : 1 CNY = ${request.achievedRate ?? request.targetRate} XOF',
            style: AppTypography.body,
          ),
          if (exchange != null) ...[
            const SizedBox(height: AppSpacing.xs),
            Text('Vers ${Money(exchange.amountCny, AppCurrency.cny).formattedWithCurrency()}',
                style: AppTypography.caption),
            const SizedBox(height: AppSpacing.xs),
            Text(
              _stageLabel(exchange.stage),
              style: AppTypography.caption.copyWith(color: Theme.of(context).colorScheme.primary),
            ),
          ],
          const SizedBox(height: AppSpacing.xs),
        ];
      case PreferredRatePhase.exchangeCompleted:
        final exchange = request.exchange;
        return [
          Text(
            'Echange termine a 1 CNY = ${request.achievedRate ?? request.targetRate} XOF',
            style: AppTypography.body,
          ),
          if (exchange != null) ...[
            const SizedBox(height: AppSpacing.xs),
            Text('Recu : ${Money(exchange.amountCny, AppCurrency.cny).formattedWithCurrency()}',
                style: AppTypography.caption),
          ],
          const SizedBox(height: AppSpacing.xs),
        ];
      case PreferredRatePhase.expired:
        return [
          Text('Cible non atteinte. Montant restitue.', style: AppTypography.caption),
          const SizedBox(height: AppSpacing.xs),
        ];
      case PreferredRatePhase.cancelled:
        return [
          Text('Annulee. Montant restitue.', style: AppTypography.caption),
          const SizedBox(height: AppSpacing.xs),
        ];
      case PreferredRatePhase.unknown:
        return const [];
    }
  }

  String _stageLabel(String stage) {
    switch (stage) {
      case 'STARTED':
        return 'Echange demarre.';
      case 'PROGRESS_45':
        return 'Echange en cours (45 min).';
      case 'PROGRESS_90':
        return 'Echange en cours (1h30).';
      case 'COMPLETED':
        return 'Echange termine.';
      default:
        return 'Echange en cours.';
    }
  }
}
