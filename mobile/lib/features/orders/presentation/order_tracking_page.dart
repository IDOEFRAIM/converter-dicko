import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../core/theme/app_typography.dart';
import '../../../shared/utils/date_formatting.dart';
import '../../../shared/widgets/corridor.dart';
import '../../../shared/widgets/error_state.dart';
import '../../../shared/widgets/loading_view.dart';
import '../application/order_tracking_controller.dart';
import '../data/order_api.dart';
import '../models/tracking_models.dart';

/// Timeline verticale du transfert (mission section 25) — utilise
/// exclusivement `event.code` (jamais un `label` backend brut), jamais une
/// machine d'etat independante.
class OrderTrackingPage extends StatelessWidget {
  final String orderId;

  const OrderTrackingPage({super.key, required this.orderId});

  @override
  Widget build(BuildContext context) {
    return ChangeNotifierProvider(
      create: (context) => OrderTrackingController(orderApi: context.read<OrderApi>(), orderId: orderId)..load(),
      child: const _OrderTrackingView(),
    );
  }
}

class _OrderTrackingView extends StatelessWidget {
  const _OrderTrackingView();

  @override
  Widget build(BuildContext context) {
    final controller = context.watch<OrderTrackingController>();

    return Scaffold(
      appBar: AppBar(
        title: const Text('Suivi du transfert'),
        actions: [
          IconButton(
            onPressed: controller.loading ? null : controller.load,
            icon: const Icon(Icons.refresh),
            tooltip: 'Actualiser',
          ),
        ],
      ),
      body: SafeArea(
        child: Builder(
          builder: (context) {
            if (controller.loading && controller.tracking == null) {
              return const LoadingView();
            }
            final tracking = controller.tracking;
            if (tracking == null) {
              return ErrorState(
                message: controller.errorMessage ?? 'Impossible de charger le suivi.',
                onRetry: controller.load,
              );
            }
            return ListView(
              padding: const EdgeInsets.all(AppSpacing.lg),
              children: [
                const Corridor(level: CorridorLevel.compact),
                const SizedBox(height: AppSpacing.lg),
                if (controller.lastUpdatedAt != null)
                  Text(
                    'Derniere mise a jour affichee : ${DateFormatting.dayTime(controller.lastUpdatedAt!.toUtc())}',
                    style: AppTypography.caption,
                  ),
                const SizedBox(height: AppSpacing.lg),
                for (var i = 0; i < tracking.timeline.length; i++)
                  _TimelineTile(
                    event: tracking.timeline[i],
                    isCurrent: controller.isCurrent(tracking.timeline[i], i),
                    isLast: i == tracking.timeline.length - 1,
                  ),
              ],
            );
          },
        ),
      ),
    );
  }
}

class _TimelineTile extends StatelessWidget {
  final TrackingEvent event;
  final bool isCurrent;
  final bool isLast;

  const _TimelineTile({required this.event, required this.isCurrent, required this.isLast});

  @override
  Widget build(BuildContext context) {
    final isNegative = event.code.isNegative;
    final isRefund = event.code.isRefund;

    final Color dotColor;
    final IconData icon;
    if (isNegative) {
      dotColor = AppColors.negative;
      icon = Icons.error;
    } else if (isRefund) {
      dotColor = AppColors.inkFaint;
      icon = Icons.undo;
    } else if (isCurrent) {
      dotColor = AppColors.ochre;
      icon = Icons.sync;
    } else {
      dotColor = Theme.of(context).colorScheme.primary;
      icon = Icons.check;
    }

    return IntrinsicHeight(
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Column(
            children: [
              Container(
                width: 28,
                height: 28,
                decoration: BoxDecoration(color: dotColor.withValues(alpha: isNegative || isCurrent || isRefund ? 1 : 0.15), shape: BoxShape.circle),
                child: Icon(icon, size: 16, color: isNegative || isCurrent || isRefund ? Colors.white : dotColor),
              ),
              if (!isLast) Expanded(child: Container(width: 2, color: AppColors.outline)),
            ],
          ),
          const SizedBox(width: AppSpacing.md),
          Expanded(
            child: Padding(
              padding: const EdgeInsets.only(bottom: AppSpacing.lg),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  if (isRefund)
                    Text('REMBOURSEMENT', style: AppTypography.eyebrow.copyWith(color: AppColors.inkFaint)),
                  Text(event.code.label, style: AppTypography.bodyStrong),
                  Text(DateFormatting.dayTime(event.occurredAt), style: AppTypography.caption),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}
