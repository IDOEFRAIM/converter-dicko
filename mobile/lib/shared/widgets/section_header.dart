import 'package:flutter/material.dart';

import '../../core/theme/app_spacing.dart';
import '../../core/theme/app_typography.dart';

/// En-tete de section reutilisable (titre + action optionnelle "Tout voir").
/// Miroir du role de `.section-header` / `PageHeaderComponent` cote Angular.
class SectionHeader extends StatelessWidget {
  final String title;
  final String? actionLabel;
  final VoidCallback? onActionTap;

  const SectionHeader({super.key, required this.title, this.actionLabel, this.onActionTap});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: AppSpacing.sm),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(title, style: AppTypography.eyebrow),
          if (actionLabel != null)
            GestureDetector(
              onTap: onActionTap,
              child: Text(actionLabel!, style: AppTypography.caption.copyWith(fontWeight: FontWeight.w700)),
            ),
        ],
      ),
    );
  }
}
