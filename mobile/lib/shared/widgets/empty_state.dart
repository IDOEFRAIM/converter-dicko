import 'package:flutter/material.dart';

import '../../core/theme/app_colors.dart';
import '../../core/theme/app_spacing.dart';
import '../../core/theme/app_typography.dart';

/// Etat vide reutilisable — listes sans resultat, historique vide, etc.
/// Miroir de `EmptyStateComponent` cote Angular, y compris l'action
/// optionnelle (ex. "Actualiser").
class EmptyState extends StatelessWidget {
  final String title;
  final String? description;
  final Widget? action;
  final IconData icon;

  const EmptyState({
    super.key,
    required this.title,
    this.description,
    this.action,
    this.icon = Icons.inbox_outlined,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: AppSpacing.xxxl, horizontal: AppSpacing.xl),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 40, color: AppColors.inkFaint),
          const SizedBox(height: AppSpacing.md),
          Text(title, style: AppTypography.bodyStrong, textAlign: TextAlign.center),
          if (description != null) ...[
            const SizedBox(height: AppSpacing.xs),
            Text(description!, style: AppTypography.caption, textAlign: TextAlign.center),
          ],
          if (action != null) ...[
            const SizedBox(height: AppSpacing.lg),
            action!,
          ],
        ],
      ),
    );
  }
}
