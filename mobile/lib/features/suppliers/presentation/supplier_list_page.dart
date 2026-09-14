import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../core/theme/app_typography.dart';
import '../../../shared/widgets/empty_state.dart';
import '../../../shared/widgets/error_state.dart';
import '../../../shared/widgets/loading_view.dart';
import '../../../shared/widgets/notification_bell_button.dart';
import '../application/supplier_list_controller.dart';
import '../data/supplier_api.dart';
import '../models/supplier_models.dart';

/// Carnet de fournisseurs (mission section 21) : Tous / Favoris / Desactives,
/// recherche locale sur la page deja chargee, acces au detail et a l'ajout.
class SupplierListPage extends StatelessWidget {
  const SupplierListPage({super.key});

  @override
  Widget build(BuildContext context) {
    return ChangeNotifierProvider(
      create: (context) => SupplierListController(context.read<SupplierApi>())..load(),
      child: const _SupplierListView(),
    );
  }
}

class _SupplierListView extends StatelessWidget {
  const _SupplierListView();

  static const _emptyCopy = {
    SupplierListFilter.all: ('Aucun fournisseur enregistre', Icons.storefront_outlined),
    SupplierListFilter.favorites: ('Aucun fournisseur favori', Icons.star_border),
    SupplierListFilter.deactivated: ('Aucun fournisseur desactive', Icons.block_outlined),
  };

  @override
  Widget build(BuildContext context) {
    final controller = context.watch<SupplierListController>();

    return Scaffold(
      appBar: AppBar(
        title: const Text('Fournisseurs'),
        actions: [
          IconButton(
            onPressed: () async {
              final created = await context.push<bool>('/suppliers/new');
              if (created == true) {
                controller.load();
              }
            },
            icon: const Icon(Icons.add),
            tooltip: 'Ajouter un fournisseur',
          ),
          const NotificationBellButton(),
        ],
      ),
      body: SafeArea(
        child: Column(
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(AppSpacing.lg, AppSpacing.md, AppSpacing.lg, 0),
              child: SegmentedButton<SupplierListFilter>(
                segments: const [
                  ButtonSegment(value: SupplierListFilter.all, label: Text('Tous')),
                  ButtonSegment(value: SupplierListFilter.favorites, label: Text('Favoris')),
                  ButtonSegment(value: SupplierListFilter.deactivated, label: Text('Desactives')),
                ],
                selected: {controller.filter},
                onSelectionChanged: (selection) => controller.setFilter(selection.first),
              ),
            ),
            Padding(
              padding: const EdgeInsets.all(AppSpacing.lg),
              child: TextField(
                onChanged: controller.setSearchText,
                decoration: const InputDecoration(
                  prefixIcon: Icon(Icons.search),
                  hintText: 'Rechercher un fournisseur',
                ),
              ),
            ),
            Expanded(child: _buildBody(context, controller)),
          ],
        ),
      ),
    );
  }

  Widget _buildBody(BuildContext context, SupplierListController controller) {
    if (controller.loading) {
      return const LoadingView();
    }
    if (controller.errorMessage != null && controller.suppliers.isEmpty) {
      return ErrorState(message: controller.errorMessage!, onRetry: controller.load);
    }
    final suppliers = controller.filteredSuppliers;
    if (suppliers.isEmpty) {
      final (title, icon) = _emptyCopy[controller.filter]!;
      return EmptyState(icon: icon, title: title, description: 'Ajoutez-en un pour payer plus vite.');
    }
    return RefreshIndicator(
      onRefresh: controller.load,
      color: Theme.of(context).colorScheme.primary,
      child: ListView.separated(
        padding: const EdgeInsets.fromLTRB(AppSpacing.lg, 0, AppSpacing.lg, AppSpacing.lg),
        itemCount: suppliers.length,
        separatorBuilder: (_, _) => const SizedBox(height: AppSpacing.sm),
        itemBuilder: (context, index) => _SupplierTile(supplier: suppliers[index]),
      ),
    );
  }
}

class _SupplierTile extends StatelessWidget {
  final SupplierSummary supplier;

  const _SupplierTile({required this.supplier});

  @override
  Widget build(BuildContext context) {
    return InkWell(
      borderRadius: BorderRadius.circular(AppSpacing.radiusMd),
      onTap: () => context.push('/suppliers/${supplier.id}'),
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
              foregroundColor: Theme.of(context).colorScheme.primary,
              child: Text(supplier.displayName.isEmpty ? '?' : supplier.displayName[0].toUpperCase()),
            ),
            const SizedBox(width: AppSpacing.md),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(supplier.displayName, style: AppTypography.bodyStrong),
                  Text('${supplier.type.label} · ${supplier.maskedAccountNumber}', style: AppTypography.caption),
                  if (!supplier.readyForPayment) ...[
                    const SizedBox(height: 2),
                    Text('Code QR manquant', style: AppTypography.caption.copyWith(color: AppColors.warning)),
                  ],
                ],
              ),
            ),
            if (supplier.favorite) const Icon(Icons.star, color: AppColors.ochre, size: 18),
            const SizedBox(width: AppSpacing.xs),
            const Icon(Icons.chevron_right, color: AppColors.inkFaint),
          ],
        ),
      ),
    );
  }
}
