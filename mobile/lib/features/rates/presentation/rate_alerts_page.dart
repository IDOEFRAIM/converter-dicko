import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../core/theme/app_typography.dart';
import '../../../shared/utils/date_formatting.dart';
import '../../../shared/utils/validators.dart';
import '../../../shared/widgets/empty_state.dart';
import '../../../shared/widgets/error_state.dart';
import '../../../shared/widgets/loading_view.dart';
import '../../../shared/widgets/primary_action.dart';
import '../../../shared/widgets/status_badge.dart';
import '../application/rate_alerts_controller.dart';
import '../data/rate_history_api.dart';
import '../models/rate_models.dart';

/// "Mes alertes" (mission section 30) : surveiller un taux cible. Jamais une
/// transaction — une alerte declenchee ne pretend jamais qu'un achat a eu
/// lieu (section 16).
class RateAlertsPage extends StatelessWidget {
  const RateAlertsPage({super.key});

  @override
  Widget build(BuildContext context) {
    return ChangeNotifierProvider(
      create: (context) => RateAlertsController(context.read<RateHistoryApi>())..load(),
      child: const _RateAlertsView(),
    );
  }
}

class _RateAlertsView extends StatefulWidget {
  const _RateAlertsView();

  @override
  State<_RateAlertsView> createState() => _RateAlertsViewState();
}

class _RateAlertsViewState extends State<_RateAlertsView> {
  final _formKey = GlobalKey<FormState>();
  final _targetController = TextEditingController();
  DateTime? _expiresAt;

  @override
  void dispose() {
    _targetController.dispose();
    super.dispose();
  }

  Future<void> _create(RateAlertsController controller) async {
    // Bug reel trouve en test : une cible vide faisait echouer silencieusement
    // le tap sur "Creer l'alerte" — aucune requete, aucun message.
    if (!_formKey.currentState!.validate()) {
      return;
    }
    final success = await controller.create(targetRate: _targetController.text.trim(), expiresAt: _expiresAt);
    if (success) {
      _targetController.clear();
      setState(() => _expiresAt = null);
    }
  }

  Future<void> _pickExpiry() async {
    final picked = await showDatePicker(
      context: context,
      firstDate: DateTime.now().add(const Duration(days: 1)),
      lastDate: DateTime.now().add(const Duration(days: 365)),
      initialDate: DateTime.now().add(const Duration(days: 30)),
    );
    if (picked != null) {
      setState(() => _expiresAt = picked);
    }
  }

  Future<void> _confirmCancel(RateAlertsController controller, RateAlert alert) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text("Annuler l'alerte"),
        content: Text('Vous ne serez plus notifie lorsque le taux atteindra ${alert.targetRate} XOF.'),
        actions: [
          TextButton(onPressed: () => Navigator.of(context).pop(false), child: const Text('Garder')),
          TextButton(onPressed: () => Navigator.of(context).pop(true), child: const Text("Annuler l'alerte")),
        ],
      ),
    );
    if (confirmed == true) {
      await controller.cancel(alert);
    }
  }

  @override
  Widget build(BuildContext context) {
    final controller = context.watch<RateAlertsController>();

    return Scaffold(
      appBar: AppBar(title: const Text('Mes alertes')),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(AppSpacing.lg),
          children: [
            Text(
              'Surveillez un taux cible et soyez averti(e) lorsqu\'il est atteint.',
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
                  Text('CREER UNE ALERTE', style: AppTypography.eyebrow),
                  const SizedBox(height: AppSpacing.sm),
                  Text(
                    'Vous serez notifie(e) lorsque le taux client descend a votre cible ou en dessous. '
                    'Creer une alerte ne declenche aucun paiement.',
                    style: AppTypography.caption,
                  ),
                  const SizedBox(height: AppSpacing.md),
                  Form(
                    key: _formKey,
                    autovalidateMode: AutovalidateMode.onUserInteraction,
                    child: TextFormField(
                      controller: _targetController,
                      keyboardType: const TextInputType.numberWithOptions(decimal: true),
                      decoration: const InputDecoration(labelText: '1 CNY ≤ *', suffixText: 'XOF'),
                      validator: Validators.positiveRate,
                    ),
                  ),
                  const SizedBox(height: AppSpacing.md),
                  InkWell(
                    onTap: _pickExpiry,
                    child: InputDecorator(
                      decoration: const InputDecoration(labelText: 'Expiration (optionnelle)'),
                      child: Text(_expiresAt == null ? 'Aucune' : DateFormatting.dayOnly(_expiresAt!)),
                    ),
                  ),
                  if (controller.createErrorMessage != null) ...[
                    const SizedBox(height: AppSpacing.md),
                    Text(controller.createErrorMessage!, style: AppTypography.body.copyWith(color: AppColors.negative)),
                  ],
                  const SizedBox(height: AppSpacing.lg),
                  PrimaryAction(label: "Creer l'alerte", loading: controller.creating, onPressed: () => _create(controller)),
                ],
              ),
            ),
            const SizedBox(height: AppSpacing.xl),
            _buildList(controller),
          ],
        ),
      ),
    );
  }

  Widget _buildList(RateAlertsController controller) {
    if (controller.loading) {
      return const LoadingView();
    }
    if (controller.errorMessage != null && controller.alerts.isEmpty) {
      return ErrorState(message: controller.errorMessage!, onRetry: controller.load);
    }
    if (controller.alerts.isEmpty) {
      return const EmptyState(
        icon: Icons.notifications_none,
        title: 'Aucune alerte',
        description: 'Creez une alerte ci-dessus pour suivre le taux.',
      );
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (controller.activeAlerts.isNotEmpty) ...[
          Text('ALERTES ACTIVES', style: AppTypography.eyebrow),
          const SizedBox(height: AppSpacing.sm),
          ...controller.activeAlerts.map((alert) => _ActiveAlertCard(
                alert: alert,
                currentRate: controller.currentRate,
                gap: controller.gapFor(alert),
                onCancel: () => _confirmCancel(controller, alert),
              )),
        ],
        if (controller.closedAlerts.isNotEmpty) ...[
          const SizedBox(height: AppSpacing.lg),
          Text('ALERTES TERMINEES', style: AppTypography.eyebrow),
          const SizedBox(height: AppSpacing.sm),
          ...controller.closedAlerts.map((alert) => _ClosedAlertCard(alert: alert)),
        ],
      ],
    );
  }
}

class _ActiveAlertCard extends StatelessWidget {
  final RateAlert alert;
  final String? currentRate;
  final double? gap;
  final VoidCallback onCancel;

  const _ActiveAlertCard({required this.alert, required this.currentRate, required this.gap, required this.onCancel});

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
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('1 CNY ≤ ${alert.targetRate} XOF', style: AppTypography.bodyStrong),
                if (currentRate != null) ...[
                  const SizedBox(height: AppSpacing.xs),
                  Text('Taux actuel : 1 CNY = $currentRate XOF', style: AppTypography.caption),
                ],
                if (gap != null) ...[
                  Text(
                    'Ecart : ${gap! > 0 ? '+' : ''}${gap!.toStringAsFixed(2)} %',
                    style: AppTypography.caption.copyWith(
                      fontWeight: FontWeight.w700,
                      color: Theme.of(context).colorScheme.primary,
                    ),
                  ),
                ],
                Text(
                  'Creee le ${DateFormatting.dayOnly(alert.createdAt)}'
                  '${alert.expiresAt != null ? ' · expire le ${DateFormatting.dayOnly(alert.expiresAt!)}' : ''}',
                  style: AppTypography.caption,
                ),
              ],
            ),
          ),
          Column(
            children: [
              const StatusBadge(status: 'ACTIVE'),
              const SizedBox(height: AppSpacing.xs),
              TextButton(onPressed: onCancel, child: const Text('Annuler')),
            ],
          ),
        ],
      ),
    );
  }
}

class _ClosedAlertCard extends StatelessWidget {
  final RateAlert alert;

  const _ClosedAlertCard({required this.alert});

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
              Text('1 CNY ≤ ${alert.targetRate} XOF', style: AppTypography.bodyStrong),
              StatusBadge(status: alert.status.code),
            ],
          ),
          const SizedBox(height: AppSpacing.xs),
          if (alert.status == RateAlertStatus.triggered) ...[
            Row(
              children: [
                const Icon(Icons.check_circle, size: 16, color: AppColors.positive),
                const SizedBox(width: AppSpacing.xs),
                Expanded(
                  child: Text(
                    'Objectif atteint${alert.triggeredAt != null ? ' le ${DateFormatting.dayTime(alert.triggeredAt!)}' : ''}. '
                    'Votre objectif de ${alert.targetRate} XOF/CNY a ete atteint.',
                    style: AppTypography.caption,
                  ),
                ),
              ],
            ),
            const SizedBox(height: AppSpacing.xs),
            TextButton(
              onPressed: () => context.push('/more/rates'),
              child: const Text('Voir le taux'),
            ),
          ] else if (alert.status == RateAlertStatus.expired)
            Text(
              'Votre objectif n\'a pas ete atteint avant la date prevue'
              '${alert.expiresAt != null ? ' (${DateFormatting.dayOnly(alert.expiresAt!)})' : ''}.',
              style: AppTypography.caption,
            )
          else if (alert.status == RateAlertStatus.cancelled)
            Text(
              'Annulee${alert.cancelledAt != null ? ' le ${DateFormatting.dayOnly(alert.cancelledAt!)}' : ''}.',
              style: AppTypography.caption,
            ),
        ],
      ),
    );
  }
}
