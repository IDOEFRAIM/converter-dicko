import 'package:flutter/material.dart';

import '../../core/theme/app_colors.dart';
import '../../core/theme/app_spacing.dart';
import '../../core/theme/app_typography.dart';

/// Etat d'erreur reutilisable, avec action "Reessayer" — aucun ecran reseau
/// ne doit rester vide silencieusement en cas d'echec API (mission section 38).
class ErrorState extends StatelessWidget {
  final String message;
  final VoidCallback? onRetry;

  const ErrorState({super.key, required this.message, this.onRetry});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: AppSpacing.xxl, horizontal: AppSpacing.xl),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Icon(Icons.error_outline, size: 36, color: AppColors.negative),
          const SizedBox(height: AppSpacing.md),
          Text(
            message,
            style: AppTypography.body,
            textAlign: TextAlign.center,
            semanticsLabel: message,
          ),
          if (onRetry != null) ...[
            const SizedBox(height: AppSpacing.lg),
            OutlinedButton.icon(
              onPressed: onRetry,
              icon: const Icon(Icons.refresh, size: 18),
              label: const Text('Reessayer'),
            ),
          ],
        ],
      ),
    );
  }
}
