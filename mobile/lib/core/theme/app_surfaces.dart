import 'package:flutter/widgets.dart';

import 'app_colors.dart';
import 'app_spacing.dart';

/// Fabriques de decoration partagees — le langage "Registre" (papier / laque,
/// voir docs/MOBILE_DESIGN_LANGUAGE.md) en un seul endroit, pour que les
/// cartes cessent d'etre re-decrites `Container` par `Container` dans chaque
/// ecran.
abstract final class AppSurfaces {
  /// Carte "papier" : base chaude, filet fin, et une ombre basse tres douce
  /// quand [raised] — la profondeur ne vient plus du seul filet gris.
  static BoxDecoration paper({bool raised = true, Color? border}) {
    return BoxDecoration(
      color: AppColors.paper,
      borderRadius: BorderRadius.circular(AppSpacing.radiusMd),
      border: Border.all(color: border ?? AppColors.outline),
      boxShadow: raised
          ? [BoxShadow(color: AppColors.navy.withValues(alpha: 0.08), blurRadius: 18, offset: const Offset(0, 8))]
          : null,
    );
  }

  /// Surface premium "laque" : navy profond + filet d'or d'un seul trait.
  /// Reservee aux moments (hero d'accueil, rang debloque, recu).
  static BoxDecoration lacquer({double radius = AppSpacing.radiusMd, bool keyline = true}) {
    return BoxDecoration(
      gradient: AppColors.lacquerGradient,
      borderRadius: BorderRadius.circular(radius),
      border: keyline ? Border.all(color: AppColors.keyline.withValues(alpha: 0.55)) : null,
      boxShadow: [BoxShadow(color: AppColors.navyDark.withValues(alpha: 0.35), blurRadius: 28, offset: const Offset(0, 14))],
    );
  }
}
