import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';
import 'package:share_plus/share_plus.dart';

import '../../../core/auth/auth_session.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../core/theme/app_typography.dart';
import '../../../core/theme/experience_theme.dart';
import '../../../shared/models/money.dart';
import '../../../shared/utils/date_formatting.dart';
import '../../../shared/widgets/error_state.dart';
import '../../../shared/widgets/loading_view.dart';
import '../../../shared/widgets/primary_action.dart';
import '../../../shared/widgets/status_badge.dart';
import '../application/pool_detail_controller.dart';
import '../data/pool_api.dart';
import '../models/pool_models.dart';

/// Detail d'une Ruee collective (mission "differenciation marketing", Lot 3) :
/// thermometre, participants, invitation par code, et acces au transfert qui
/// y contribue. Le "temps reel" est simule par sondage periodique tant que la
/// Ruee est ACTIVE (voir [PoolDetailController]) -- aucune infrastructure
/// WebSocket/SSE cote backend, on ne pretend jamais l'inverse.
class PoolDetailPage extends StatelessWidget {
  final String poolId;

  const PoolDetailPage({super.key, required this.poolId});

  @override
  Widget build(BuildContext context) {
    return ChangeNotifierProvider(
      create: (context) => PoolDetailController(poolApi: context.read<PoolApi>(), poolId: poolId)..load(),
      child: const _PoolDetailView(),
    );
  }
}

class _PoolDetailView extends StatelessWidget {
  const _PoolDetailView();

  Future<void> _share(Pool pool) async {
    try {
      await SharePlus.instance.share(ShareParams(
        text: 'Rejoins ma Ruee sur l\'app ! Objectif : ${Money(pool.targetAmountXof, AppCurrency.xof).formattedWithCurrency()}. '
            'Code : ${pool.code}',
        subject: 'Rejoins ma Ruee !',
      ));
    } catch (_) {
      // Feuille de partage indisponible (plugin non enregistre, aucune app compatible...) :
      // le code reste visible et copiable a l'ecran, jamais bloquant pour la suite du parcours.
    }
  }

  Future<void> _confirmCancel(BuildContext context, PoolDetailController controller) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Annuler la Ruee'),
        content: const Text('Les participants seront prevenus. Cette action est definitive.'),
        actions: [
          TextButton(onPressed: () => Navigator.of(context).pop(false), child: const Text('Garder')),
          TextButton(onPressed: () => Navigator.of(context).pop(true), child: const Text('Annuler la Ruee')),
        ],
      ),
    );
    if (confirmed == true) {
      await controller.cancel();
    }
  }

  @override
  Widget build(BuildContext context) {
    final controller = context.watch<PoolDetailController>();
    final profile = context.watch<AuthSession>().experienceProfile;
    final gradient = ExperiencePalette.gradientFor(profile);

    return Scaffold(
      appBar: AppBar(title: const Text('Ruee collective')),
      body: SafeArea(
        child: Builder(
          builder: (context) {
            if (controller.loading) {
              return const LoadingView();
            }
            final pool = controller.pool;
            if (pool == null) {
              return ErrorState(
                message: controller.errorMessage ?? 'Impossible de charger cette Ruee.',
                onRetry: controller.load,
              );
            }
            return RefreshIndicator(
              onRefresh: controller.load,
              color: Theme.of(context).colorScheme.primary,
              child: ListView(
                padding: const EdgeInsets.all(AppSpacing.lg),
                children: [
                  _ThermometerCard(pool: pool, gradient: gradient, onShare: () => _share(pool)),
                  const SizedBox(height: AppSpacing.lg),
                  if (pool.status == PoolStatus.succeeded) _SuccessBanner(pool: pool, gradient: gradient),
                  if (pool.status == PoolStatus.expired || pool.status == PoolStatus.cancelled)
                    _ClosedBanner(pool: pool),
                  const SizedBox(height: AppSpacing.lg),
                  Text('PARTICIPANTS (${controller.participants.length})', style: AppTypography.eyebrow),
                  const SizedBox(height: AppSpacing.sm),
                  ...controller.participants.map((p) => _ParticipantTile(participant: p)),
                  const SizedBox(height: AppSpacing.xl),
                  _Actions(pool: pool, controller: controller, onCancel: () => _confirmCancel(context, controller)),
                ],
              ),
            );
          },
        ),
      ),
    );
  }
}

class _ThermometerCard extends StatelessWidget {
  final Pool pool;
  final ExperienceGradient gradient;
  final VoidCallback onShare;

  const _ThermometerCard({required this.pool, required this.gradient, required this.onShare});

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(AppSpacing.lg),
      decoration: BoxDecoration(gradient: gradient.gradient, borderRadius: BorderRadius.circular(AppSpacing.radiusMd)),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text('CODE ${pool.code}', style: AppTypography.eyebrow.copyWith(color: gradient.foreground)),
              StatusBadge(status: pool.status.code),
            ],
          ),
          const SizedBox(height: AppSpacing.md),
          Text(
            '${Money(pool.currentAmountXof, AppCurrency.xof).formattedWithCurrency()} / '
            '${Money(pool.targetAmountXof, AppCurrency.xof).formattedWithCurrency()}',
            style: AppTypography.metricLarge.copyWith(color: gradient.foreground),
          ),
          const SizedBox(height: AppSpacing.sm),
          ClipRRect(
            borderRadius: BorderRadius.circular(AppSpacing.radiusPill),
            child: LinearProgressIndicator(
              value: pool.progress,
              minHeight: 10,
              backgroundColor: gradient.foreground.withValues(alpha: 0.25),
              valueColor: AlwaysStoppedAnimation(gradient.foreground),
            ),
          ),
          const SizedBox(height: AppSpacing.sm),
          Text(
            pool.isActive
                ? 'Expire le ${DateFormatting.dayTime(pool.expiresAt)}'
                : 'Cloturee le ${DateFormatting.dayTime(pool.succeededAt ?? pool.expiredAt ?? pool.cancelledAt ?? pool.expiresAt)}',
            style: AppTypography.caption.copyWith(color: gradient.foreground.withValues(alpha: 0.85)),
          ),
          if (pool.isActive) ...[
            const SizedBox(height: AppSpacing.md),
            OutlinedButton.icon(
              onPressed: onShare,
              icon: Icon(Icons.share_outlined, color: gradient.foreground),
              label: Text('Inviter des amis', style: TextStyle(color: gradient.foreground)),
              style: OutlinedButton.styleFrom(side: BorderSide(color: gradient.foreground.withValues(alpha: 0.6))),
            ),
          ],
        ],
      ),
    );
  }
}

class _SuccessBanner extends StatelessWidget {
  final Pool pool;
  final ExperienceGradient gradient;

  const _SuccessBanner({required this.pool, required this.gradient});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(AppSpacing.md),
      decoration: BoxDecoration(
        color: AppColors.positiveSurface,
        borderRadius: BorderRadius.circular(AppSpacing.radiusSm),
      ),
      child: Row(
        children: [
          const Icon(Icons.celebration_outlined, color: AppColors.positive),
          const SizedBox(width: AppSpacing.sm),
          Expanded(
            child: Text(
              'Objectif atteint ! Vous beneficiez de -${pool.rewardMarginReductionPercentage} points de marge sur '
              'votre prochain transfert.',
              style: AppTypography.body.copyWith(color: AppColors.positive),
            ),
          ),
        ],
      ),
    );
  }
}

class _ClosedBanner extends StatelessWidget {
  final Pool pool;

  const _ClosedBanner({required this.pool});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(AppSpacing.md),
      decoration: BoxDecoration(color: AppColors.ivoryDim, borderRadius: BorderRadius.circular(AppSpacing.radiusSm)),
      child: Text(
        pool.status == PoolStatus.expired
            ? 'Cette Ruee a expire sans atteindre son objectif.'
            : 'Cette Ruee a ete annulee par son createur.',
        style: AppTypography.body.copyWith(color: AppColors.inkMuted),
      ),
    );
  }
}

class _ParticipantTile extends StatelessWidget {
  final PoolParticipant participant;

  const _ParticipantTile({required this.participant});

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
          CircleAvatar(
            backgroundColor: AppColors.ivoryDim,
            foregroundColor: Theme.of(context).colorScheme.primary,
            child: Text(participant.firstName.isEmpty ? '?' : participant.firstName[0].toUpperCase()),
          ),
          const SizedBox(width: AppSpacing.md),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(participant.firstName, style: AppTypography.bodyStrong),
                Text(
                  double.parse(participant.contributedAmountXof) > 0
                      ? 'A contribue ${Money(participant.contributedAmountXof, AppCurrency.xof).formattedWithCurrency()}'
                      : 'A rejoint sans contribuer pour l\'instant',
                  style: AppTypography.caption,
                ),
              ],
            ),
          ),
          if (participant.isCreator) const Icon(Icons.star, color: AppColors.ochre, size: 18),
        ],
      ),
    );
  }
}

class _Actions extends StatelessWidget {
  final Pool pool;
  final PoolDetailController controller;
  final VoidCallback onCancel;

  const _Actions({required this.pool, required this.controller, required this.onCancel});

  @override
  Widget build(BuildContext context) {
    if (!pool.isActive) {
      return const SizedBox.shrink();
    }
    if (!pool.viewerIsParticipant) {
      return Column(
        children: [
          if (controller.joinErrorMessage != null) ...[
            Text(controller.joinErrorMessage!, style: AppTypography.body.copyWith(color: AppColors.negative)),
            const SizedBox(height: AppSpacing.md),
          ],
          PrimaryAction(label: 'Rejoindre la Ruee', loading: controller.joining, onPressed: controller.join),
        ],
      );
    }
    return Column(
      children: [
        PrimaryAction(
          label: 'Payer maintenant',
          icon: Icons.send_outlined,
          onPressed: () => context.push('/pay/pools/${pool.id}/checkout'),
        ),
        if (pool.viewerIsCreator) ...[
          const SizedBox(height: AppSpacing.sm),
          TextButton(
            onPressed: controller.cancelling ? null : onCancel,
            child: Text(
              controller.cancelling ? 'Annulation...' : 'Annuler la Ruee',
              style: const TextStyle(color: AppColors.negative),
            ),
          ),
        ],
      ],
    );
  }
}
