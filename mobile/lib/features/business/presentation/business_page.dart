import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../core/theme/app_typography.dart';
import '../../../shared/models/money.dart';
import '../../../shared/utils/date_formatting.dart';
import '../../../shared/widgets/loading_view.dart';
import '../../../shared/widgets/primary_action.dart';
import '../../../shared/widgets/status_badge.dart';
import '../../orders/data/order_api.dart';
import '../application/business_controller.dart';
import '../data/business_api.dart';
import '../models/business_models.dart';

/// Espace professionnel (mission section 32/33) : profil, KPI, activite
/// recente. Un `404` sur le profil est un etat normal (compte Personnel),
/// jamais une bannière d'erreur — revele simplement le formulaire de creation.
class BusinessPage extends StatelessWidget {
  const BusinessPage({super.key});

  @override
  Widget build(BuildContext context) {
    return ChangeNotifierProvider(
      create: (context) => BusinessController(
        businessApi: context.read<BusinessApi>(),
        orderApi: context.read<OrderApi>(),
      )..load(),
      child: const _BusinessView(),
    );
  }
}

class _BusinessView extends StatelessWidget {
  const _BusinessView();

  @override
  Widget build(BuildContext context) {
    final controller = context.watch<BusinessController>();

    return Scaffold(
      appBar: AppBar(title: const Text('Espace professionnel')),
      body: SafeArea(
        child: Builder(
          builder: (context) {
            if (controller.loading) {
              return const LoadingView();
            }
            if (controller.profile == null) {
              return const _BusinessProfileForm();
            }
            return _BusinessDashboard(controller: controller);
          },
        ),
      ),
    );
  }
}

class _BusinessDashboard extends StatelessWidget {
  final BusinessController controller;

  const _BusinessDashboard({required this.controller});

  @override
  Widget build(BuildContext context) {
    final profile = controller.profile!;
    final summary = controller.summary;

    return RefreshIndicator(
      onRefresh: controller.load,
      color: AppColors.navy,
      child: ListView(
        padding: const EdgeInsets.all(AppSpacing.lg),
        children: [
          Container(
            padding: const EdgeInsets.all(AppSpacing.lg),
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
                      Text(profile.businessName, style: AppTypography.titleMedium),
                      Text(profile.businessType.label, style: AppTypography.caption),
                    ],
                  ),
                ),
                IconButton(
                  onPressed: () => showDialog(
                    context: context,
                    // Un dialogue est pousse sur le Navigator, EN DEHORS de
                    // l'arbre du ChangeNotifierProvider local a cette page —
                    // on lui transmet donc explicitement la MEME instance de
                    // controleur (`.value`), plutot que de compter sur une
                    // recherche ambiante qui echouerait.
                    builder: (_) => ChangeNotifierProvider.value(
                      value: controller,
                      child: Dialog(child: _BusinessProfileForm(existing: profile)),
                    ),
                  ),
                  icon: const Icon(Icons.edit_outlined),
                ),
              ],
            ),
          ),
          const SizedBox(height: AppSpacing.xl),
          if (summary != null) ...[
            Text('APERCU', style: AppTypography.eyebrow),
            const SizedBox(height: AppSpacing.sm),
            GridView.count(
              crossAxisCount: 2,
              shrinkWrap: true,
              physics: const NeverScrollableScrollPhysics(),
              mainAxisSpacing: AppSpacing.sm,
              crossAxisSpacing: AppSpacing.sm,
              childAspectRatio: 2.2,
              children: [
                _kpiTile('Transferts', '${summary.transferCount}'),
                _kpiTile('Completes', '${summary.completedCount}'),
                _kpiTile('Annules', '${summary.cancelledCount}'),
                _kpiTile('Rejetes', '${summary.rejectedCount}'),
                _kpiTile('Total XOF', Money(summary.totalAmountXof, AppCurrency.xof).formatted()),
                _kpiTile('Total CNY', Money(summary.totalAmountCny, AppCurrency.cny).formatted()),
                _kpiTile('Frais', Money(summary.totalFeesXof, AppCurrency.xof).formattedWithCurrency()),
              ],
            ),
            const SizedBox(height: AppSpacing.xl),
          ],
          Text('ACTIVITE RECENTE', style: AppTypography.eyebrow),
          const SizedBox(height: AppSpacing.sm),
          if (controller.recentOrders.isEmpty)
            Text('Aucune operation.', style: AppTypography.caption)
          else
            ...controller.recentOrders.map(
              (order) => InkWell(
                borderRadius: BorderRadius.circular(AppSpacing.radiusMd),
                onTap: () => context.push('/orders/${order.id}'),
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
                            Text('#${order.reference}', style: AppTypography.bodyStrong),
                            Text(DateFormatting.dayOnly(order.createdAt), style: AppTypography.caption),
                          ],
                        ),
                      ),
                      StatusBadge(status: order.status.code),
                    ],
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }

  Widget _kpiTile(String label, String value) {
    return Container(
      padding: const EdgeInsets.all(AppSpacing.md),
      decoration: BoxDecoration(
        color: Colors.white,
        border: Border.all(color: AppColors.outline),
        borderRadius: BorderRadius.circular(AppSpacing.radiusMd),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Text(label, style: AppTypography.caption),
          const SizedBox(height: AppSpacing.xs),
          Text(value, style: AppTypography.bodyStrong),
        ],
      ),
    );
  }
}

class _BusinessProfileForm extends StatefulWidget {
  final BusinessProfile? existing;

  const _BusinessProfileForm({this.existing});

  @override
  State<_BusinessProfileForm> createState() => _BusinessProfileFormState();
}

class _BusinessProfileFormState extends State<_BusinessProfileForm> {
  final _formKey = GlobalKey<FormState>();
  late final _nameController = TextEditingController(text: widget.existing?.businessName);
  late final _registrationController = TextEditingController(text: widget.existing?.registrationNumber);
  late final _countryController = TextEditingController(text: widget.existing?.country ?? 'Burkina Faso');
  late final _cityController = TextEditingController(text: widget.existing?.city);
  late final _addressController = TextEditingController(text: widget.existing?.address);
  late BusinessType _type = widget.existing?.businessType ?? BusinessType.merchant;

  @override
  void dispose() {
    _nameController.dispose();
    _registrationController.dispose();
    _countryController.dispose();
    _cityController.dispose();
    _addressController.dispose();
    super.dispose();
  }

  Future<void> _submit(BusinessController controller) async {
    if (!_formKey.currentState!.validate()) return;
    final request = UpsertBusinessProfileRequest(
      businessName: _nameController.text.trim(),
      businessType: _type,
      registrationNumber: _registrationController.text.trim().isEmpty ? null : _registrationController.text.trim(),
      country: _countryController.text.trim(),
      city: _cityController.text.trim().isEmpty ? null : _cityController.text.trim(),
      address: _addressController.text.trim().isEmpty ? null : _addressController.text.trim(),
    );
    final success = await controller.saveProfile(request);
    if (success && mounted) {
      final isEditing = widget.existing != null;
      if (isEditing) {
        Navigator.of(context).pop();
      }
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(isEditing ? 'Profil mis a jour.' : 'Profil professionnel cree.')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final controller = context.watch<BusinessController>();
    final isEditing = widget.existing != null;

    return Padding(
      padding: const EdgeInsets.all(AppSpacing.lg),
      child: SingleChildScrollView(
        child: Form(
          key: _formKey,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                isEditing ? 'Modifier le profil' : 'Creer un profil professionnel',
                style: AppTypography.titleMedium,
              ),
              if (!isEditing) ...[
                const SizedBox(height: AppSpacing.xs),
                Text(
                  'Suivez vos transferts professionnels avec des statistiques dediees.',
                  style: AppTypography.caption,
                ),
              ],
              const SizedBox(height: AppSpacing.lg),
              TextFormField(
                controller: _nameController,
                decoration: const InputDecoration(labelText: 'Nom de l\'entreprise'),
                validator: (v) => (v == null || v.trim().isEmpty) ? 'Ce champ est obligatoire.' : null,
              ),
              const SizedBox(height: AppSpacing.md),
              DropdownButtonFormField<BusinessType>(
                initialValue: _type,
                decoration: const InputDecoration(labelText: 'Type d\'activite'),
                items: BusinessType.values.map((t) => DropdownMenuItem(value: t, child: Text(t.label))).toList(growable: false),
                onChanged: (value) => setState(() => _type = value!),
              ),
              const SizedBox(height: AppSpacing.md),
              TextFormField(
                controller: _registrationController,
                decoration: const InputDecoration(labelText: 'Numero d\'enregistrement (optionnel)'),
              ),
              const SizedBox(height: AppSpacing.md),
              TextFormField(
                controller: _countryController,
                decoration: const InputDecoration(labelText: 'Pays'),
                validator: (v) => (v == null || v.trim().isEmpty) ? 'Le pays est obligatoire.' : null,
              ),
              const SizedBox(height: AppSpacing.md),
              TextFormField(
                controller: _cityController,
                decoration: const InputDecoration(labelText: 'Ville (optionnel)'),
              ),
              const SizedBox(height: AppSpacing.md),
              TextFormField(
                controller: _addressController,
                decoration: const InputDecoration(labelText: 'Adresse (optionnel)'),
              ),
              if (controller.saveErrorMessage != null) ...[
                const SizedBox(height: AppSpacing.md),
                Text(controller.saveErrorMessage!, style: AppTypography.body.copyWith(color: AppColors.negative)),
              ],
              const SizedBox(height: AppSpacing.xl),
              PrimaryAction(
                label: isEditing ? 'Enregistrer' : 'Creer le profil',
                loading: controller.savingProfile,
                onPressed: () => _submit(controller),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
