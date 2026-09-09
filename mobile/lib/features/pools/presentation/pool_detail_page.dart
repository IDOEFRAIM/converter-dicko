import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';
import 'package:share_plus/share_plus.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../core/theme/app_surfaces.dart';
import '../../../core/theme/app_typography.dart';
import '../../../shared/models/money.dart';
import '../../../shared/utils/date_formatting.dart';
import '../../../shared/widgets/collection_gauge.dart';
import '../../../shared/widgets/error_state.dart';
import '../../../shared/widgets/grain.dart';
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
                  _ThermometerCard(
                    pool: pool,
                    participants: controller.participants,
                    onShare: () => _share(pool),
                  ),
                  const SizedBox(height: AppSpacing.lg),
                  if (pool.status == PoolStatus.succeeded) _SuccessBanner(pool: pool),
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
  final List<PoolParticipant> participants;
  final VoidCallback onShare;

  const _ThermometerCard({required this.pool, required this.participants, required this.onShare});

  @override
  Widget build(BuildContext context) {
    final succeeded = pool.status == PoolStatus.succeeded;
    return ClipRRect(
      borderRadius: BorderRadius.circular(AppSpacing.radiusMd),
      child: Container(
        width: double.infinity,
        padding: const EdgeInsets.all(AppSpacing.lg),
        decoration: AppSurfaces.lacquer(),
        child: Stack(
          children: [
            const Positioned.fill(child: LedgerGrain(opacity: 0.05)),
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text('CODE ${pool.code}',
                        style: AppTypography.eyebrow.copyWith(color: AppColors.keyline)),
                    StatusBadge(status: pool.status.code),
                  ],
                ),
                const SizedBox(height: AppSpacing.sm),
                Center(
                  child: CollectionGauge(
                    progress: pool.progress,
                    currentLabel: Money(pool.currentAmountXof, AppCurrency.xof).formattedWithCurrency(),
                    targetLabel: Money(pool.targetAmountXof, AppCurrency.xof).formattedWithCurrency(),
                    succeeded: succeeded,
                  ),
                ),
                if (pool.isActive) ...[
                  const SizedBox(height: AppSpacing.xs),
                  Center(
                    child: Text(
                      'Recompense a l\'objectif : -${pool.rewardMarginReductionPercentage} pts de marge',
                      textAlign: TextAlign.center,
                      style: AppTypography.caption.copyWith(color: AppColors.keyline, fontWeight: FontWeight.w700),
                    ),
                  ),
                ],
                const SizedBox(height: AppSpacing.md),
                Divider(color: AppColors.onLacquer.withValues(alpha: 0.12), height: 1),
                const SizedBox(height: AppSpacing.md),
                Row(
                  children: [
                    ParticipantMonograms(
                      names: participants.map((p) => p.firstName).toList(growable: false),
                      foreground: AppColors.onLacquer,
                      background: AppColors.lacquerEdge,
                    ),
                    const SizedBox(width: AppSpacing.sm),
                    Expanded(
                      child: Text(
                        '${pool.participantCount} participant(s)',
                        style: AppTypography.caption.copyWith(color: AppColors.onLacquerMuted),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: AppSpacing.sm),
                Text(
                  pool.isActive
                      ? 'Expire le ${DateFormatting.dayTime(pool.expiresAt)}'
                      : 'Cloturee le ${DateFormatting.dayTime(pool.succeededAt ?? pool.expiredAt ?? pool.cancelledAt ?? pool.expiresAt)}',
                  style: AppTypography.caption.copyWith(color: AppColors.onLacquerMuted),
                ),
                if (pool.isActive) ...[
                  const SizedBox(height: AppSpacing.md),
                  OutlinedButton.icon(
                    onPressed: onShare,
                    icon: const Icon(Icons.share_outlined, color: AppColors.keyline),
                    label: const Text('Inviter des amis', style: TextStyle(color: AppColors.keyline)),
                    style: OutlinedButton.styleFrom(
                      side: BorderSide(color: AppColors.keyline.withValues(alpha: 0.6)),
                    ),
                  ),
                ],
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _SuccessBanner extends StatelessWidget {
  final Pool pool;

  const _SuccessBanner({required this.pool});

  @override
  Widget build(BuildContext context) {
    return ClipRRect(
      borderRadius: BorderRadius.circular(AppSpacing.radiusMd),
      child: Container(
        padding: const EdgeInsets.all(AppSpacing.md),
        decoration: AppSurfaces.lacquer(),
        child: Stack(
          children: [
            const Positioned.fill(child: LedgerGrain(opacity: 0.06)),
            Row(
              children: [
                const PoolSeal(size: 40),
                const SizedBox(width: AppSpacing.md),
                Expanded(
                  child: Text(
                    'Objectif atteint ! -${pool.rewardMarginReductionPercentage} points de marge sur '
                    'ton prochain transfert.',
                    style: AppTypography.body.copyWith(color: AppColors.onLacquer),
                  ),
                ),
              ],
            ),
          ],
        ),
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
      decoration: AppSurfaces.paper(),
      child: Row(
        children: [
          Monogram(
            name: participant.firstName,
            size: 40,
            foreground: Theme.of(context).colorScheme.primary,
            background: AppColors.ivoryDim,
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
