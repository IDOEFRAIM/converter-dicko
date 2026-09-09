import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../core/theme/app_spacing.dart';
import '../../../core/theme/app_typography.dart';
import '../../../shared/utils/date_formatting.dart';
import '../../../shared/widgets/corridor_timeline.dart';
import '../../../shared/widgets/error_state.dart';
import '../../../shared/widgets/loading_view.dart';
import '../application/order_tracking_controller.dart';
import '../data/order_api.dart';
import '../models/order_models.dart';
import '../models/tracking_models.dart';

/// Suivi du transfert (mission section 25) — la timeline est tracee *le long
/// du corridor* (langage de design "Le Comptoir", Lot C). On utilise
/// exclusivement `event.code` (jamais un `label` backend brut), jamais une
/// machine d'etat independante : [CorridorTimeline] n'est qu'une vue.
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

  StationTone _toneFor(TrackingEvent event, bool isCurrent) {
    if (event.code.isNegative) return StationTone.negative;
    if (event.code.isRefund) return StationTone.refund;
    if (isCurrent) return StationTone.current;
    return StationTone.done;
  }

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

            final stations = [
              for (var i = 0; i < tracking.timeline.length; i++)
                CorridorStation(
                  label: tracking.timeline[i].code.label,
                  timestamp: DateFormatting.dayTime(tracking.timeline[i].occurredAt),
                  tone: _toneFor(tracking.timeline[i], controller.isCurrent(tracking.timeline[i], i)),
                ),
            ];

            return RefreshIndicator(
              onRefresh: controller.load,
              child: ListView(
                padding: const EdgeInsets.all(AppSpacing.lg),
                physics: const AlwaysScrollableScrollPhysics(),
                children: [
                  CorridorTimeline(
                    stations: stations,
                    reachedDestination: tracking.currentStatus == OrderStatus.completed,
                  ),
                  const SizedBox(height: AppSpacing.md),
                  if (controller.lastUpdatedAt != null)
                    Text(
                      'Derniere mise a jour affichee : ${DateFormatting.dayTime(controller.lastUpdatedAt!.toUtc())}',
                      style: AppTypography.caption,
                    ),
                ],
              ),
            );
          },
        ),
      ),
    );
  }
}
