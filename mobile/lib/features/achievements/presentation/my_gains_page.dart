import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../../core/auth/auth_session.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../core/theme/app_typography.dart';
import '../../../core/theme/experience_theme.dart';
import '../../../shared/models/current_user.dart';
import '../../../shared/models/money.dart';
import '../../../shared/utils/date_formatting.dart';
import '../../../shared/widgets/empty_state.dart';
import '../../../shared/widgets/error_state.dart';
import '../../../shared/widgets/loading_view.dart';
import '../../../shared/widgets/status_badge.dart';
import '../../orders/data/order_api.dart';
import '../application/my_gains_controller.dart';
import '../data/achievement_api.dart';

/// "Mes gains" (mission "differenciation marketing", Lot 2) : un compteur
/// sobre pour PRO, un badge + XP pour STUDENT_MALE/FEMALE — memes donnees
/// reelles (volume transfere, transferts termines), jamais un chiffre
/// invente ni presente comme une "economie" vs un concurrent (voir
/// AchievementSummary).
class MyGainsPage extends StatelessWidget {
  const MyGainsPage({super.key});

  @override
  Widget build(BuildContext context) {
    return ChangeNotifierProvider(
      create: (context) => MyGainsController(
        achievementApi: context.read<AchievementApi>(),
        orderApi: context.read<OrderApi>(),
      )..load(),
      child: const _MyGainsView(),
    );
  }
}

class _MyGainsView extends StatelessWidget {
  const _MyGainsView();

  @override
  Widget build(BuildContext context) {
    final controller = context.watch<MyGainsController>();
    final profile = context.watch<AuthSession>().experienceProfile;

    return Scaffold(
      appBar: AppBar(title: const Text('Mes gains')),
      body: SafeArea(
        child: RefreshIndicator(
          onRefresh: controller.load,
          color: AppColors.navy,
          child: ListView(
            padding: const EdgeInsets.all(AppSpacing.lg),
            children: [
              _SummaryHero(controller: controller, profile: profile),
              const SizedBox(height: AppSpacing.xl),
              Text(_historyTitle(profile), style: AppTypography.eyebrow),
              const SizedBox(height: AppSpacing.sm),
              _HistorySection(controller: controller),
            ],
          ),
        ),
      ),
    );
  }

  String _historyTitle(ExperienceProfile profile) => switch (profile) {
        ExperienceProfile.pro => 'HISTORIQUE',
        ExperienceProfile.studentMale => 'LIVRE DES GAINS',
        ExperienceProfile.studentFemale => 'CARNET DE ROUTE',
      };
}

class _SummaryHero extends StatelessWidget {
  final MyGainsController controller;
  final ExperienceProfile profile;

  const _SummaryHero({required this.controller, required this.profile});

  @override
  Widget build(BuildContext context) {
    if (controller.loadingSummary) {
      return const LoadingView();
    }
    final summary = controller.summary;
    if (summary == null) {
      return ErrorState(
        message: controller.summaryErrorMessage ?? 'Impossible de charger vos gains.',
        onRetry: controller.loadSummary,
      );
    }

    if (profile == ExperienceProfile.pro) {
      return Container(
        width: double.infinity,
        padding: const EdgeInsets.all(AppSpacing.lg),
        decoration: BoxDecoration(
          color: Colors.white,
          border: Border.all(color: AppColors.outline),
          borderRadius: BorderRadius.circular(AppSpacing.radiusMd),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('VOLUME TRANSFERE CE MOIS-CI', style: AppTypography.eyebrow),
            const SizedBox(height: AppSpacing.xs),
            Text(
              Money(summary.currentMonthAmountXofCompleted, AppCurrency.xof).formattedWithCurrency(),
              style: AppTypography.metricLarge,
            ),
            const SizedBox(height: AppSpacing.sm),
            Text(
              '${summary.completedTransferCount} transfert(s) termine(s) au total.',
              style: AppTypography.caption,
            ),
          ],
        ),
      );
    }

    final gradient = ExperiencePalette.gradientFor(profile);
    final foreground = gradient.foreground;
    final badgeLabel = summary.badgeLabel;
    final subtitle = profile == ExperienceProfile.studentMale
        ? 'Chaque transfert termine te rapproche du prochain rang.'
        : 'Chaque transfert termine ecrit une nouvelle page de ton carnet.';

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(AppSpacing.lg),
      decoration: BoxDecoration(gradient: gradient.gradient, borderRadius: BorderRadius.circular(AppSpacing.radiusMd)),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            badgeLabel == null ? 'AUCUN BADGE ENCORE' : 'TON RANG',
            style: AppTypography.eyebrow.copyWith(color: foreground.withValues(alpha: 0.85)),
          ),
          const SizedBox(height: AppSpacing.xs),
          Text(
            badgeLabel ?? 'A toi de jouer',
            style: AppTypography.metricLarge.copyWith(color: foreground),
          ),
          const SizedBox(height: AppSpacing.sm),
          Text('${summary.xp} XP', style: AppTypography.bodyStrong.copyWith(color: foreground)),
          const SizedBox(height: AppSpacing.xs),
          Text(subtitle, style: AppTypography.caption.copyWith(color: foreground.withValues(alpha: 0.85))),
          if (summary.nextBadgeLabel != null) ...[
            const SizedBox(height: AppSpacing.sm),
            Text(
              'Encore ${summary.transfersUntilNextBadge} transfert(s) pour devenir ${summary.nextBadgeLabel} !',
              style: AppTypography.caption.copyWith(color: foreground, fontWeight: FontWeight.w700),
            ),
          ],
        ],
      ),
    );
  }
}

class _HistorySection extends StatelessWidget {
  final MyGainsController controller;

  const _HistorySection({required this.controller});

  @override
  Widget build(BuildContext context) {
    if (controller.loadingHistory) {
      return const LoadingView();
    }
    if (controller.historyErrorMessage != null && controller.completedTransfers.isEmpty) {
      return ErrorState(message: controller.historyErrorMessage!, onRetry: controller.loadHistory);
    }
    if (controller.completedTransfers.isEmpty) {
      return const EmptyState(
        icon: Icons.emoji_events_outlined,
        title: 'Aucun transfert termine',
        description: 'Vos transferts termines apparaitront ici.',
      );
    }
    return Column(
      children: controller.completedTransfers
          .map(
            (entry) => InkWell(
              borderRadius: BorderRadius.circular(AppSpacing.radiusMd),
              onTap: () => context.push('/activity/orders/${entry.id}'),
              child: Container(
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
                          Text('#${entry.reference}', style: AppTypography.bodyStrong),
                          Text(
                            Money(entry.amountXof, AppCurrency.xof).formattedWithCurrency(),
                            style: AppTypography.caption,
                          ),
                          Text(DateFormatting.dayOnly(entry.createdAt), style: AppTypography.caption),
                        ],
                      ),
                    ),
                    StatusBadge(status: entry.status.code),
                  ],
                ),
              ),
            ),
          )
          .toList(growable: false),
    );
  }
}
