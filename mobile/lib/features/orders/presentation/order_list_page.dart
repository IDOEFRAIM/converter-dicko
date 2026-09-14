import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../core/theme/app_typography.dart';
import '../../../shared/models/money.dart';
import '../../../shared/utils/date_formatting.dart';
import '../../../shared/widgets/empty_state.dart';
import '../../../shared/widgets/error_state.dart';
import '../../../shared/widgets/loading_view.dart';
import '../../../shared/widgets/notification_bell_button.dart';
import '../../../shared/widgets/status_badge.dart';
import '../application/order_list_controller.dart';
import '../data/order_api.dart';

/// Onglet "Activite" — historique des transferts (mission section 19/29).
class OrderListPage extends StatelessWidget {
  const OrderListPage({super.key});

  @override
  Widget build(BuildContext context) {
    return ChangeNotifierProvider(
      create: (context) => OrderListController(context.read<OrderApi>())..load(),
      child: const _OrderListView(),
    );
  }
}

class _OrderListView extends StatelessWidget {
  const _OrderListView();

  @override
  Widget build(BuildContext context) {
    final controller = context.watch<OrderListController>();

    return Scaffold(
      appBar: AppBar(title: const Text('Activite'), actions: const [NotificationBellButton()]),
      body: SafeArea(
        child: Builder(
          builder: (context) {
            if (controller.loading && controller.orders.isEmpty) {
              return const LoadingView();
            }
            if (controller.errorMessage != null && controller.orders.isEmpty) {
              return ErrorState(message: controller.errorMessage!, onRetry: controller.load);
            }
            if (controller.orders.isEmpty) {
              return const EmptyState(icon: Icons.receipt_long_outlined, title: 'Aucune operation');
            }
            return RefreshIndicator(
              onRefresh: controller.load,
              color: Theme.of(context).colorScheme.primary,
              child: ListView.separated(
                padding: const EdgeInsets.all(AppSpacing.lg),
                itemCount: controller.orders.length,
                separatorBuilder: (_, _) => const SizedBox(height: AppSpacing.sm),
                itemBuilder: (context, index) {
                  final order = controller.orders[index];
                  return InkWell(
                    borderRadius: BorderRadius.circular(AppSpacing.radiusMd),
                    onTap: () => context.push('/activity/orders/${order.id}'),
                    child: Container(
                      padding: const EdgeInsets.all(AppSpacing.lg),
                      decoration: BoxDecoration(
                        color: Colors.white,
                        borderRadius: BorderRadius.circular(AppSpacing.radiusMd),
                        border: Border.all(color: AppColors.outline),
                      ),
                      child: Row(
                        children: [
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text('#${order.reference}', style: AppTypography.bodyStrong),
                                const SizedBox(height: AppSpacing.xs),
                                Text(
                                  Money(order.amountXof, AppCurrency.xof).formattedWithCurrency(),
                                  style: AppTypography.caption,
                                ),
                                Text(DateFormatting.dayOnly(order.createdAt), style: AppTypography.caption),
                              ],
                            ),
                          ),
                          StatusBadge(status: order.status.code),
                        ],
                      ),
                    ),
                  );
                },
              ),
            );
          },
        ),
      ),
    );
  }
}
