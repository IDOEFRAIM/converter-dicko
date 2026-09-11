import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../core/theme/app_surfaces.dart';
import '../../../core/theme/app_typography.dart';
import '../../../shared/models/money.dart';
import '../../../shared/widgets/empty_state.dart';
import '../../../shared/widgets/error_state.dart';
import '../../../shared/widgets/loading_view.dart';
import '../../../shared/widgets/primary_action.dart';
import '../../../shared/widgets/status_badge.dart';
import '../application/my_pools_controller.dart';
import '../data/pool_api.dart';
import '../models/pool_models.dart';

/// "Mes Ruees" (mission "differenciation marketing", Lot 3) : creees ou
/// rejointes, actives puis terminees.
class MyPoolsPage extends StatelessWidget {
  const MyPoolsPage({super.key});

  @override
  Widget build(BuildContext context) {
    return ChangeNotifierProvider(
      create: (context) => MyPoolsController(context.read<PoolApi>())..load(),
      child: const _MyPoolsView(),
    );
  }
}

class _MyPoolsView extends StatelessWidget {
  const _MyPoolsView();

  @override
  Widget build(BuildContext context) {
    final controller = context.watch<MyPoolsController>();

    return Scaffold(
      appBar: AppBar(
        title: const Text('Mes Ruees'),
        actions: [
          IconButton(
            onPressed: () => context.push('/pay/pools/join'),
            icon: const Icon(Icons.qr_code_outlined),
            tooltip: 'Rejoindre avec un code',
          ),
        ],
      ),
      body: SafeArea(
        child: RefreshIndicator(
          onRefresh: controller.load,
          color: Theme.of(context).colorScheme.primary,
          child: ListView(
            padding: const EdgeInsets.all(AppSpacing.lg),
            children: [
              PrimaryAction(
                label: 'Lancer une Ruee',
                icon: Icons.bolt_outlined,
                onPressed: () => context.push('/pay/pools/new'),
              ),
              const SizedBox(height: AppSpacing.xl),
              _buildList(controller),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildList(MyPoolsController controller) {
    if (controller.loading) {
      return const LoadingView();
    }
    if (controller.errorMessage != null && controller.pools.isEmpty) {
      return ErrorState(message: controller.errorMessage!, onRetry: controller.load);
    }
    if (controller.pools.isEmpty) {
      return const EmptyState(
        icon: Icons.groups_outlined,
        title: 'Aucune Ruee',
        description: 'Lancez-en une ou rejoignez-en une.',
      );
    }
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (controller.active.isNotEmpty) ...[
          Text('EN COURS', style: AppTypography.eyebrow),
          const SizedBox(height: AppSpacing.sm),
          ...controller.active.map((p) => _PoolTile(pool: p)),
          const SizedBox(height: AppSpacing.lg),
        ],
        if (controller.closed.isNotEmpty) ...[
          Text('TERMINEES', style: AppTypography.eyebrow),
          const SizedBox(height: AppSpacing.sm),
          ...controller.closed.map((p) => _PoolTile(pool: p)),
        ],
      ],
    );
  }
}

class _PoolTile extends StatelessWidget {
  final Pool pool;

  const _PoolTile({required this.pool});

  @override
  Widget build(BuildContext context) {
    return InkWell(
      borderRadius: BorderRadius.circular(AppSpacing.radiusMd),
      onTap: () => context.push('/pay/pools/${pool.id}'),
      child: Container(
        margin: const EdgeInsets.only(bottom: AppSpacing.sm),
        padding: const EdgeInsets.all(AppSpacing.md),
        decoration: AppSurfaces.paper(),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('Code ${pool.code}', style: AppTypography.bodyStrong),
                      Text(
                        '${Money(pool.currentAmountXof, AppCurrency.xof).formattedWithCurrency()} / '
                        '${Money(pool.targetAmountXof, AppCurrency.xof).formattedWithCurrency()} · '
                        '${pool.participantCount} participant(s)',
                        style: AppTypography.caption,
                      ),
                    ],
                  ),
                ),
                StatusBadge(status: pool.status.code),
              ],
            ),
            const SizedBox(height: AppSpacing.sm),
            ClipRRect(
              borderRadius: BorderRadius.circular(AppSpacing.radiusPill),
              child: LinearProgressIndicator(
                value: pool.progress,
                minHeight: 3,
                backgroundColor: AppColors.paperEdge,
                valueColor: const AlwaysStoppedAnimation(AppColors.keyline),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
