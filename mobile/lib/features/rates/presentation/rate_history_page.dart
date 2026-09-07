import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../../core/auth/auth_session.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../core/theme/app_typography.dart';
import '../../../shared/utils/date_formatting.dart';
import '../../../shared/widgets/corridor.dart';
import '../../../shared/widgets/empty_state.dart';
import '../../../shared/widgets/error_state.dart';
import '../../../shared/widgets/loading_view.dart';
import '../../../shared/widgets/primary_action.dart';
import '../../../shared/widgets/rate_display.dart';
import '../../../shared/widgets/sparkline.dart';
import '../application/rate_history_controller.dart';
import '../data/rate_history_api.dart';

/// "Taux XOF / CNY" (mission section 29) : taux du moment en grand, courbe,
/// min/max de la periode, acces a la creation d'une alerte. Uniquement des
/// donnees reelles — jamais de graphique fictif sur echec (section 37).
class RateHistoryPage extends StatelessWidget {
  const RateHistoryPage({super.key});

  @override
  Widget build(BuildContext context) {
    return ChangeNotifierProvider(
      create: (context) => RateHistoryController(context.read<RateHistoryApi>())..load(),
      child: const _RateHistoryView(),
    );
  }
}

class _RateHistoryView extends StatelessWidget {
  const _RateHistoryView();

  @override
  Widget build(BuildContext context) {
    final controller = context.watch<RateHistoryController>();
    final experienceGradient = context.watch<AuthSession>().experienceGradient;
    final heroForeground = experienceGradient.foreground;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Taux XOF / CNY'),
        actions: [
          IconButton(
            onPressed: () => context.push('/more/rate-alerts'),
            icon: const Icon(Icons.notifications_active_outlined),
            tooltip: 'Mes alertes',
          ),
        ],
      ),
      body: SafeArea(
        child: Builder(
          builder: (context) {
            if (controller.loading) {
              return const LoadingView();
            }
            if (controller.errorMessage != null) {
              return ErrorState(message: 'Impossible de charger l\'historique du taux.', onRetry: controller.load);
            }
            if (controller.entries.isEmpty) {
              return Center(
                child: EmptyState(
                  icon: Icons.show_chart,
                  title: 'Aucun historique disponible',
                  description: 'Les taux publies apparaitront ici.',
                  action: OutlinedButton.icon(
                    onPressed: controller.load,
                    icon: const Icon(Icons.refresh, size: 18),
                    label: const Text('Actualiser'),
                  ),
                ),
              );
            }

            final latest = controller.latest!;
            final range = controller.range;
            final variation = controller.variation;

            return RefreshIndicator(
              onRefresh: controller.load,
              color: Theme.of(context).colorScheme.primary,
              child: ListView(
                padding: const EdgeInsets.all(AppSpacing.lg),
                children: [
                  const Corridor(level: CorridorLevel.compact),
                  const SizedBox(height: AppSpacing.lg),
                  Container(
                    width: double.infinity,
                    padding: const EdgeInsets.all(AppSpacing.lg),
                    decoration: BoxDecoration(
                      gradient: experienceGradient.gradient,
                      borderRadius: BorderRadius.circular(AppSpacing.radiusMd),
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'TAUX ACTUEL',
                          style: AppTypography.eyebrow.copyWith(color: heroForeground.withValues(alpha: 0.85)),
                        ),
                        const SizedBox(height: AppSpacing.xs),
                        RateDisplay(customerRate: latest.customerRate, large: true, color: heroForeground),
                        const SizedBox(height: AppSpacing.xs),
                        Text(
                          'Mis a jour le ${DateFormatting.dayTime(latest.recordedAt)}',
                          style: AppTypography.caption.copyWith(color: heroForeground.withValues(alpha: 0.85)),
                        ),
                        if (variation != null) ...[
                          const SizedBox(height: AppSpacing.xs),
                          Text(
                            '${variation.percent > 0 ? '+' : ''}${variation.percent.toStringAsFixed(2)}% depuis le releve precedent',
                            style: AppTypography.caption.copyWith(
                              // Teintes choisies selon le contraste du degrade courant (voir
                              // heroForeground) : un vert/rouge clair lirait mal sur un fond
                              // pastel clair (Mode Histoire) tout comme il lirait bien sur un
                              // fond sombre (PRO/Mode Epopee).
                              color: heroForeground == Colors.white
                                  ? (variation.percent >= 0 ? const Color(0xFFB8F0C0) : const Color(0xFFFFD3D3))
                                  : (variation.percent >= 0 ? const Color(0xFF1B5E20) : const Color(0xFF8A1416)),
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                        ],
                      ],
                    ),
                  ),
                  const SizedBox(height: AppSpacing.lg),
                  Container(
                    padding: const EdgeInsets.all(AppSpacing.md),
                    decoration: BoxDecoration(
                      color: Colors.white,
                      border: Border.all(color: AppColors.outline),
                      borderRadius: BorderRadius.circular(AppSpacing.radiusMd),
                    ),
                    child: Sparkline(
                      values: controller.chronological.map((e) => double.parse(e.customerRate)).toList(),
                      color: Theme.of(context).colorScheme.primary,
                    ),
                  ),
                  if (range != null) ...[
                    const SizedBox(height: AppSpacing.lg),
                    Row(
                      children: [
                        Expanded(child: _statTile('MINIMUM (PERIODE)', '${range.$1} XOF')),
                        const SizedBox(width: AppSpacing.md),
                        Expanded(child: _statTile('MAXIMUM (PERIODE)', '${range.$2} XOF')),
                      ],
                    ),
                  ],
                  const SizedBox(height: AppSpacing.xl),
                  PrimaryAction(
                    label: 'Creer une alerte',
                    icon: Icons.add_alert_outlined,
                    onPressed: () => context.push('/more/rate-alerts'),
                  ),
                  const SizedBox(height: AppSpacing.xl),
                  Text('HISTORIQUE', style: AppTypography.eyebrow),
                  const SizedBox(height: AppSpacing.sm),
                  ...controller.entries.map(
                    (entry) => Padding(
                      padding: const EdgeInsets.symmetric(vertical: AppSpacing.sm),
                      child: Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Text('1 CNY = ${entry.customerRate} XOF', style: AppTypography.bodyStrong),
                          Text(DateFormatting.dayTime(entry.recordedAt), style: AppTypography.caption),
                        ],
                      ),
                    ),
                  ),
                ],
              ),
            );
          },
        ),
      ),
    );
  }

  Widget _statTile(String label, String value) {
    return Container(
      padding: const EdgeInsets.all(AppSpacing.md),
      decoration: BoxDecoration(
        color: Colors.white,
        border: Border.all(color: AppColors.outline),
        borderRadius: BorderRadius.circular(AppSpacing.radiusMd),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label, style: AppTypography.caption),
          const SizedBox(height: AppSpacing.xs),
          Text(value, style: AppTypography.bodyStrong),
        ],
      ),
    );
  }
}
