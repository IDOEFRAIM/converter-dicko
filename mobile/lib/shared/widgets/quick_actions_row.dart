import 'package:flutter/material.dart';

import '../../core/theme/app_spacing.dart';
import '../../core/theme/app_typography.dart';
import 'pressable.dart';

/// Un raccourci d'accueil : pastille d'icone coloree + libelle de 1-2 mots.
/// Retour client (sept. 2026, capture de reference) : moins de texte, des
/// icones qui montrent la destination avant de la nommer.
class QuickAction {
  final IconData icon;
  final String label;
  final Color background;
  final Color foreground;
  final VoidCallback onTap;

  const QuickAction({
    required this.icon,
    required this.label,
    required this.background,
    required this.foreground,
    required this.onTap,
  });
}

/// Rangee de 3 a 4 [QuickAction] espacees uniformement — voir [HomePage].
/// Chaque pastille reste tactile (Pressable) meme si le geste principal de
/// l'ecran reste ailleurs : ce sont des racourcis, pas LE parcours.
class QuickActionsRow extends StatelessWidget {
  final List<QuickAction> actions;

  const QuickActionsRow({super.key, required this.actions});

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [for (final action in actions) _QuickActionTile(action: action)],
    );
  }
}

class _QuickActionTile extends StatelessWidget {
  final QuickAction action;

  const _QuickActionTile({required this.action});

  @override
  Widget build(BuildContext context) {
    return Pressable(
      onTap: action.onTap,
      child: SizedBox(
        width: 68,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 56,
              height: 56,
              alignment: Alignment.center,
              decoration: BoxDecoration(color: action.background, shape: BoxShape.circle),
              child: Icon(action.icon, color: action.foreground, size: 24),
            ),
            const SizedBox(height: AppSpacing.xs),
            Text(
              action.label,
              style: AppTypography.caption,
              textAlign: TextAlign.center,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
            ),
          ],
        ),
      ),
    );
  }
}
