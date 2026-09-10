import 'dart:io';

import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import 'package:provider/provider.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../core/theme/app_surfaces.dart';
import '../../../core/theme/app_typography.dart';
import '../../../shared/utils/date_formatting.dart';
import '../../../shared/widgets/loading_view.dart';
import '../../../shared/widgets/primary_action.dart';
import '../application/kyc_controller.dart';
import '../data/kyc_api.dart';
import '../models/kyc_models.dart';

/// Verification d'identite en libre-service (remarque produit #6) : facile,
/// rapide, intuitive — trois photos, un envoi, un statut.
class KycPage extends StatelessWidget {
  const KycPage({super.key});

  @override
  Widget build(BuildContext context) {
    return ChangeNotifierProvider(
      create: (context) => KycController(context.read<KycApi>())..load(),
      child: const _KycView(),
    );
  }
}

class _KycView extends StatelessWidget {
  const _KycView();

  Future<void> _pickSource(BuildContext context, KycController controller, String slot) async {
    final source = await showModalBottomSheet<ImageSource>(
      context: context,
      builder: (sheetContext) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              leading: const Icon(Icons.photo_camera_outlined),
              title: const Text('Appareil photo'),
              onTap: () => Navigator.of(sheetContext).pop(ImageSource.camera),
            ),
            ListTile(
              leading: const Icon(Icons.photo_library_outlined),
              title: const Text('Choisir dans la galerie'),
              onTap: () => Navigator.of(sheetContext).pop(ImageSource.gallery),
            ),
          ],
        ),
      ),
    );
    if (source != null) {
      await controller.capture(slot, source);
    }
  }

  Future<void> _submit(BuildContext context, KycController controller) async {
    final ok = await controller.submit();
    if (!context.mounted) return;
    if (ok) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Dossier envoye. Vous serez notifie de la reponse.')),
      );
    } else if (controller.errorMessage != null) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(controller.errorMessage!)));
    }
  }

  @override
  Widget build(BuildContext context) {
    final controller = context.watch<KycController>();

    return Scaffold(
      appBar: AppBar(title: const Text("Verification d'identite")),
      body: SafeArea(
        child: Builder(
          builder: (context) {
            if (controller.loading) return const LoadingView();

            final submission = controller.submission;
            final status = submission?.status ?? KycStatus.none;
            final showForm = status == KycStatus.none || status == KycStatus.rejected;

            return ListView(
              padding: const EdgeInsets.all(AppSpacing.lg),
              children: [
                if (status == KycStatus.approved) _approvedCard(),
                if (status == KycStatus.pending) _pendingCard(submission!),
                if (status == KycStatus.rejected) _rejectedCard(submission!),
                if (showForm) ..._form(context, controller),
              ],
            );
          },
        ),
      ),
    );
  }

  Widget _approvedCard() {
    return ClipRRect(
      borderRadius: BorderRadius.circular(AppSpacing.radiusMd),
      child: Container(
        width: double.infinity,
        padding: const EdgeInsets.all(AppSpacing.lg),
        decoration: AppSurfaces.lacquer(),
        child: Row(
          children: [
            const Icon(Icons.verified_rounded, color: AppColors.keyline, size: 22),
            const SizedBox(width: AppSpacing.md),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('IDENTITE VERIFIEE',
                      style: AppTypography.eyebrow.copyWith(color: AppColors.keyline)),
                  const SizedBox(height: 2),
                  Text('Vous pouvez transferer sans limite de seuil.',
                      style: AppTypography.body.copyWith(color: AppColors.onLacquerMuted)),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _pendingCard(KycSubmission submission) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(AppSpacing.lg),
      decoration: AppSurfaces.paper(),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('DOSSIER EN COURS D\'EXAMEN', style: AppTypography.eyebrow),
          const SizedBox(height: AppSpacing.xs),
          Text('Soumis le ${DateFormatting.dayTime(submission.submittedAt)}', style: AppTypography.caption),
          const SizedBox(height: AppSpacing.xs),
          Text(
            'Notre equipe verifie votre dossier, generalement sous 24 a 48 heures. '
            'Vous n\'avez rien d\'autre a faire.',
            style: AppTypography.body.copyWith(color: AppColors.inkMuted),
          ),
        ],
      ),
    );
  }

  Widget _rejectedCard(KycSubmission submission) {
    return Container(
      width: double.infinity,
      margin: const EdgeInsets.only(bottom: AppSpacing.lg),
      padding: const EdgeInsets.all(AppSpacing.md),
      decoration: BoxDecoration(
        color: AppColors.warningSurface,
        borderRadius: BorderRadius.circular(AppSpacing.radiusSm),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Icon(Icons.error_outline, size: 18, color: AppColors.warning),
              const SizedBox(width: AppSpacing.xs),
              Text('Dossier a corriger',
                  style: AppTypography.bodyStrong.copyWith(color: AppColors.warning)),
            ],
          ),
          if (submission.rejectionReason != null) ...[
            const SizedBox(height: AppSpacing.xs),
            Text(submission.rejectionReason!, style: AppTypography.body.copyWith(color: AppColors.warning)),
          ],
          const SizedBox(height: AppSpacing.xs),
          Text('Reprenez les photos ci-dessous et renvoyez.',
              style: AppTypography.caption.copyWith(color: AppColors.warning)),
        ],
      ),
    );
  }

  List<Widget> _form(BuildContext context, KycController controller) {
    return [
      Text('VOTRE PIECE', style: AppTypography.eyebrow),
      const SizedBox(height: AppSpacing.xs),
      for (final type in KycDocumentType.values)
        _DocTypeOption(
          type: type,
          selected: controller.documentType == type,
          onTap: () => controller.setDocumentType(type),
        ),
      const SizedBox(height: AppSpacing.md),
      _CaptureTile(
        label: 'Recto de la piece',
        hint: 'Lisible, sans reflet, dans le cadre',
        path: controller.frontPath,
        onTap: () => _pickSource(context, controller, 'front'),
      ),
      if (controller.documentType.needsBack)
        _CaptureTile(
          label: 'Verso de la piece',
          hint: 'Lisible, sans reflet',
          path: controller.backPath,
          onTap: () => _pickSource(context, controller, 'back'),
        ),
      _CaptureTile(
        label: 'Selfie',
        hint: 'Visage bien visible, sans lunettes de soleil',
        path: controller.selfiePath,
        onTap: () => _pickSource(context, controller, 'selfie'),
      ),
      if (controller.errorMessage != null) ...[
        const SizedBox(height: AppSpacing.sm),
        Text(controller.errorMessage!, style: AppTypography.body.copyWith(color: AppColors.negative)),
      ],
      const SizedBox(height: AppSpacing.lg),
      PrimaryAction(
        label: 'Envoyer le dossier',
        loading: controller.submitting,
        onPressed: controller.canSubmit ? () => _submit(context, controller) : null,
      ),
      const SizedBox(height: AppSpacing.sm),
      Text(
        'Vos documents sont transmis de maniere securisee et examines par notre equipe. '
        'Ils ne servent qu\'a verifier votre identite.',
        style: AppTypography.caption,
      ),
    ];
  }
}

class _DocTypeOption extends StatelessWidget {
  final KycDocumentType type;
  final bool selected;
  final VoidCallback onTap;

  const _DocTypeOption({required this.type, required this.selected, required this.onTap});

  @override
  Widget build(BuildContext context) {
    final accent = Theme.of(context).colorScheme.primary;
    return Padding(
      padding: const EdgeInsets.only(bottom: AppSpacing.sm),
      child: InkWell(
        borderRadius: BorderRadius.circular(AppSpacing.radiusMd),
        onTap: onTap,
        child: Container(
          padding: const EdgeInsets.all(AppSpacing.md),
          decoration: BoxDecoration(
            color: AppColors.paper,
            borderRadius: BorderRadius.circular(AppSpacing.radiusMd),
            border: Border.all(color: selected ? accent : AppColors.paperEdge, width: selected ? 1.6 : 1),
          ),
          child: Row(
            children: [
              Icon(
                selected ? Icons.radio_button_checked : Icons.radio_button_unchecked,
                color: selected ? accent : AppColors.inkFaint,
                size: 20,
              ),
              const SizedBox(width: AppSpacing.md),
              Expanded(child: Text(type.label, style: AppTypography.bodyStrong)),
            ],
          ),
        ),
      ),
    );
  }
}

class _CaptureTile extends StatelessWidget {
  final String label;
  final String? hint;
  final String? path;
  final VoidCallback onTap;

  const _CaptureTile({required this.label, required this.path, required this.onTap, this.hint});

  @override
  Widget build(BuildContext context) {
    final done = path != null;
    return Padding(
      padding: const EdgeInsets.only(bottom: AppSpacing.md),
      child: InkWell(
        borderRadius: BorderRadius.circular(AppSpacing.radiusMd),
        onTap: onTap,
        child: Container(
          padding: const EdgeInsets.all(AppSpacing.md),
          decoration: BoxDecoration(
            color: AppColors.paper,
            borderRadius: BorderRadius.circular(AppSpacing.radiusMd),
            border: Border.all(color: done ? AppColors.positive : AppColors.paperEdge, width: 1.5),
          ),
          child: Row(
            children: [
              SizedBox(
                width: 56,
                height: 56,
                child: done
                    ? ClipRRect(
                        borderRadius: BorderRadius.circular(AppSpacing.radiusSm),
                        child: Image.file(
                          File(path!),
                          fit: BoxFit.cover,
                          cacheWidth: 160,
                          errorBuilder: (context, error, stackTrace) => const ColoredBox(
                            color: AppColors.ivoryDim,
                            child: Icon(Icons.image_outlined, color: AppColors.inkMuted),
                          ),
                        ),
                      )
                    : DecoratedBox(
                        decoration: BoxDecoration(
                          color: AppColors.ivoryDim,
                          borderRadius: BorderRadius.circular(AppSpacing.radiusSm),
                        ),
                        child: const Icon(Icons.photo_camera_outlined, color: AppColors.inkMuted),
                      ),
              ),
              const SizedBox(width: AppSpacing.md),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(label, style: AppTypography.bodyStrong),
                    if (hint != null) Text(hint!, style: AppTypography.caption),
                    if (done)
                      Text('Photo prise · toucher pour refaire',
                          style: AppTypography.caption.copyWith(color: AppColors.positive)),
                  ],
                ),
              ),
              Icon(done ? Icons.check_circle : Icons.chevron_right,
                  color: done ? AppColors.positive : AppColors.inkFaint),
            ],
          ),
        ),
      ),
    );
  }
}
