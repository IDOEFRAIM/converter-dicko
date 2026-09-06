import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../core/theme/app_typography.dart';
import '../../../shared/widgets/error_state.dart';
import '../../../shared/widgets/loading_view.dart';
import '../../../shared/widgets/status_badge.dart';
import '../application/supplier_detail_controller.dart';
import '../data/supplier_api.dart';
import '../models/supplier_models.dart';

/// Detail d'un fournisseur/partenaire commercial (mission section 21) — pas
/// un simple "contact" : favoris, desactivation, edition, "Payer a nouveau".
class SupplierDetailPage extends StatelessWidget {
  final String supplierId;

  const SupplierDetailPage({super.key, required this.supplierId});

  @override
  Widget build(BuildContext context) {
    return ChangeNotifierProvider(
      create: (context) =>
          SupplierDetailController(supplierApi: context.read<SupplierApi>(), supplierId: supplierId)..load(),
      child: const _SupplierDetailView(),
    );
  }
}

class _SupplierDetailView extends StatelessWidget {
  const _SupplierDetailView();

  Future<void> _confirmDeactivate(BuildContext context, SupplierDetailController controller) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Desactiver le fournisseur'),
        content: const Text(
          'Ce fournisseur ne sera plus proposable pour un nouveau paiement. Vos ordres deja crees avec lui ne '
          'sont pas affectes. Aucune suppression n\'a lieu.',
        ),
        actions: [
          TextButton(onPressed: () => Navigator.of(context).pop(false), child: const Text('Annuler')),
          TextButton(onPressed: () => Navigator.of(context).pop(true), child: const Text('Desactiver')),
        ],
      ),
    );
    if (confirmed == true) {
      await controller.deactivate();
    }
  }

  @override
  Widget build(BuildContext context) {
    final controller = context.watch<SupplierDetailController>();
    final supplier = controller.supplier;

    return Scaffold(
      appBar: AppBar(
        title: Text(supplier?.displayName ?? 'Fournisseur'),
        actions: [
          if (supplier != null)
            IconButton(
              onPressed: controller.actionInProgress ? null : controller.toggleFavorite,
              icon: Icon(
                supplier.favorite ? Icons.star : Icons.star_border,
                color: supplier.favorite ? AppColors.ochre : null,
              ),
              tooltip: supplier.favorite ? 'Retirer des favoris' : 'Ajouter aux favoris',
            ),
          if (supplier != null)
            IconButton(
              onPressed: () async {
                final updated = await context.push<bool>('/suppliers/${supplier.id}/edit', extra: supplier);
                if (updated == true) controller.load();
              },
              icon: const Icon(Icons.edit_outlined),
              tooltip: 'Modifier',
            ),
        ],
      ),
      body: SafeArea(
        child: Builder(
          builder: (context) {
            if (controller.loading) {
              return const LoadingView();
            }
            if (supplier == null) {
              return ErrorState(
                message: controller.errorMessage ?? 'Impossible de charger ce fournisseur.',
                onRetry: controller.load,
              );
            }
            return ListView(
              padding: const EdgeInsets.all(AppSpacing.lg),
              children: [
                if (supplier.status == SupplierStatus.inactive)
                  Padding(
                    padding: const EdgeInsets.only(bottom: AppSpacing.md),
                    child: Container(
                      padding: const EdgeInsets.all(AppSpacing.md),
                      decoration: BoxDecoration(
                        color: AppColors.warningSurface,
                        borderRadius: BorderRadius.circular(AppSpacing.radiusSm),
                      ),
                      child: Text(
                        'Ce fournisseur est desactive. Reactivez-le en le modifiant pour l\'utiliser a nouveau.',
                        style: AppTypography.body.copyWith(color: AppColors.warning),
                      ),
                    ),
                  ),
                Container(
                  padding: const EdgeInsets.all(AppSpacing.lg),
                  decoration: BoxDecoration(
                    color: Colors.white,
                    borderRadius: BorderRadius.circular(AppSpacing.radiusMd),
                    border: Border.all(color: AppColors.outline),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Text(supplier.type.label, style: AppTypography.eyebrow),
                          StatusBadge(status: supplier.status == SupplierStatus.active ? 'ACTIVE' : 'INACTIVE'),
                        ],
                      ),
                      const SizedBox(height: AppSpacing.sm),
                      _row('Identifiant', supplier.accountNumber),
                      if (supplier.accountName != null) _row('Titulaire', supplier.accountName!),
                      if (supplier.bankName != null) _row('Banque', supplier.bankName!),
                      if (supplier.bankBranch != null) _row('Agence', supplier.bankBranch!),
                      if (supplier.phone != null) _row('Telephone', supplier.phone!),
                      if (supplier.email != null) _row('Email', supplier.email!),
                      if (supplier.city != null || supplier.country != null)
                        _row('Localisation', [supplier.city, supplier.country].whereType<String>().join(', ')),
                      if (supplier.purpose != null) _row('Motif par defaut', supplier.purpose!.label),
                      if (supplier.notes != null && supplier.notes!.isNotEmpty) _row('Notes', supplier.notes!),
                    ],
                  ),
                ),
                const SizedBox(height: AppSpacing.xl),
                if (supplier.status == SupplierStatus.active)
                  SizedBox(
                    width: double.infinity,
                    child: ElevatedButton.icon(
                      onPressed: () => context.push('/suppliers/${supplier.id}/pay-again'),
                      icon: const Icon(Icons.send_outlined, size: 18),
                      label: const Text('Payer a nouveau'),
                    ),
                  ),
                const SizedBox(height: AppSpacing.md),
                if (supplier.status == SupplierStatus.active)
                  SizedBox(
                    width: double.infinity,
                    child: OutlinedButton(
                      onPressed: controller.actionInProgress ? null : () => _confirmDeactivate(context, controller),
                      child: const Text('Desactiver'),
                    ),
                  ),
              ],
            );
          },
        ),
      ),
    );
  }

  Widget _row(String label, String value) {
    return Padding(
      padding: const EdgeInsets.only(top: AppSpacing.sm),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label.toUpperCase(), style: AppTypography.caption),
          Text(value, style: AppTypography.bodyStrong),
        ],
      ),
    );
  }
}
