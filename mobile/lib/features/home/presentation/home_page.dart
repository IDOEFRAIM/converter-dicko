import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../../core/auth/auth_session.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../core/theme/app_typography.dart';
import '../../../shared/models/money.dart';
import '../../../shared/utils/async_value.dart';
import '../../../shared/utils/date_formatting.dart';
import '../../../shared/widgets/corridor.dart';
import '../../../shared/widgets/empty_state.dart';
import '../../../shared/widgets/error_state.dart';
import '../../../shared/widgets/loading_view.dart';
import '../../../shared/widgets/primary_action.dart';
import '../../../shared/widgets/rate_display.dart';
import '../../../shared/widgets/section_header.dart';
import '../../../shared/widgets/status_badge.dart';
import '../../orders/models/order_models.dart';
import '../../rates/models/rate_models.dart';
import '../../suppliers/models/supplier_models.dart';
import '../application/home_controller.dart';

/// Ecran le plus important de l'app (mission section 18) : communique
/// immediatement 🇧🇫 -> 🇨🇳, le taux du moment, puis l'action principale
/// (payer un fournisseur), puis un rappel de la derniere operation et des
/// fournisseurs enregistres. Uniquement des donnees reelles de l'API —
/// aucune valeur fictive (section 37).
class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => context.read<HomeController>().loadAll());
  }

  @override
  Widget build(BuildContext context) {
    final controller = context.watch<HomeController>();
    final user = context.watch<AuthSession>().currentUser;

    return Scaffold(
      body: SafeArea(
        child: RefreshIndicator(
          onRefresh: controller.loadAll,
          color: AppColors.navy,
          child: ListView(
            padding: const EdgeInsets.fromLTRB(AppSpacing.lg, AppSpacing.lg, AppSpacing.lg, AppSpacing.xxxl),
            children: [
              Text(
                user == null ? 'Bonjour 👋' : 'Bonjour ${user.firstName} 👋',
                style: AppTypography.titleLarge,
              ),
              const SizedBox(height: AppSpacing.lg),
              const Corridor(level: CorridorLevel.hero),
              const SizedBox(height: AppSpacing.xl),
              _RateHero(state: controller.latestRate, onRetry: controller.loadRate),
              const SizedBox(height: AppSpacing.lg),
              PrimaryAction(
                label: 'Payer un fournisseur',
                icon: Icons.send_outlined,
                onPressed: () => context.go('/pay'),
              ),
              const SizedBox(height: AppSpacing.xxl),
              SectionHeader(
                title: 'DERNIERE OPERATION',
                actionLabel: 'Tout voir',
                onActionTap: () => context.go('/activity'),
              ),
              _LastOperationCard(state: controller.lastOperation, onRetry: controller.loadLastOperation),
              const SizedBox(height: AppSpacing.xxl),
              SectionHeader(
                title: 'MES FOURNISSEURS',
                actionLabel: 'Tout voir',
                onActionTap: () => context.go('/suppliers'),
              ),
              _SuppliersPreview(state: controller.suppliers, onRetry: controller.loadSuppliers),
            ],
          ),
        ),
      ),
    );
  }
}

class _RateHero extends StatelessWidget {
  final AsyncValue<PublicRateHistoryEntry?> state;
  final VoidCallback onRetry;

  const _RateHero({required this.state, required this.onRetry});

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(AppSpacing.lg),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(AppSpacing.radiusMd),
        border: Border.all(color: AppColors.outline),
      ),
      child: state.when(
        loading: () => const SizedBox(height: 64, child: LoadingView()),
        error: (message) => ErrorState(message: message, onRetry: onRetry),
        data: (entry) {
          if (entry == null) {
            return const EmptyState(
              icon: Icons.show_chart,
              title: 'Aucun taux publie',
              description: 'Le taux client apparaitra ici des sa premiere publication.',
            );
          }
          return Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('TAUX DU MOMENT', style: AppTypography.eyebrow),
              const SizedBox(height: AppSpacing.xs),
              RateDisplay(customerRate: entry.customerRate, large: true, color: AppColors.navy),
              const SizedBox(height: AppSpacing.xs),
              Text('Mis a jour le ${DateFormatting.dayTime(entry.recordedAt)}', style: AppTypography.caption),
            ],
          );
        },
      ),
    );
  }
}

class _LastOperationCard extends StatelessWidget {
  final AsyncValue<OrderHistoryEntry?> state;
  final VoidCallback onRetry;

  const _LastOperationCard({required this.state, required this.onRetry});

  @override
  Widget build(BuildContext context) {
    return state.when(
      loading: () => const LoadingView(),
      error: (message) => ErrorState(message: message, onRetry: onRetry),
      data: (entry) {
        if (entry == null) {
          return const EmptyState(
            icon: Icons.receipt_long_outlined,
            title: 'Aucune operation',
            description: 'Vos transferts vers la Chine apparaitront ici.',
          );
        }
        return InkWell(
          borderRadius: BorderRadius.circular(AppSpacing.radiusMd),
          onTap: () => context.go('/activity'),
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
                      Text('#${entry.reference}', style: AppTypography.bodyStrong),
                      const SizedBox(height: AppSpacing.xs),
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
        );
      },
    );
  }
}

class _SuppliersPreview extends StatelessWidget {
  final AsyncValue<List<SupplierSummary>> state;
  final VoidCallback onRetry;

  const _SuppliersPreview({required this.state, required this.onRetry});

  @override
  Widget build(BuildContext context) {
    return state.when(
      loading: () => const LoadingView(),
      error: (message) => ErrorState(message: message, onRetry: onRetry),
      data: (suppliers) {
        if (suppliers.isEmpty) {
          return const EmptyState(
            icon: Icons.storefront_outlined,
            title: 'Aucun fournisseur enregistre',
            description: 'Ajoutez un fournisseur pour le payer en quelques secondes la prochaine fois.',
          );
        }
        return Column(
          children: suppliers
              .map(
                (supplier) => Padding(
                  padding: const EdgeInsets.only(bottom: AppSpacing.sm),
                  child: Container(
                    padding: const EdgeInsets.all(AppSpacing.md),
                    decoration: BoxDecoration(
                      color: Colors.white,
                      borderRadius: BorderRadius.circular(AppSpacing.radiusMd),
                      border: Border.all(color: AppColors.outline),
                    ),
                    child: Row(
                      children: [
                        CircleAvatar(
                          backgroundColor: AppColors.ivoryDim,
                          foregroundColor: AppColors.navy,
                          child: Text(supplier.displayName.isEmpty ? '?' : supplier.displayName[0].toUpperCase()),
                        ),
                        const SizedBox(width: AppSpacing.md),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(supplier.displayName, style: AppTypography.bodyStrong),
                              Text(
                                '${supplier.type.label} · ${supplier.maskedAccountNumber}',
                                style: AppTypography.caption,
                              ),
                            ],
                          ),
                        ),
                        if (supplier.favorite) const Icon(Icons.star, color: AppColors.ochre, size: 18),
                      ],
                    ),
                  ),
                ),
              )
              .toList(growable: false),
        );
      },
    );
  }
}
