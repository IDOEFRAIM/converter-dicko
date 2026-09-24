import 'package:flutter/material.dart';

import '../../shared/models/current_user.dart';
import 'app_colors.dart';
import 'app_spacing.dart';
import 'app_typography.dart';
import 'experience_theme.dart';

/// Theme Material central de l'application. Aucun widget ne doit ecrire
/// `Color(0xFF...)` directement — toujours passer par [AppColors]/ce theme
/// (mission section 16).
///
/// [forProfile] fait varier UNIQUEMENT l'accent de marque (boutons, barre de
/// navigation, focus des champs, degrade) selon [ExperienceProfile] — jamais
/// les tons neutres (fond ivoire, texte, bordures), qui restent identiques
/// pour garder une lisibilite constante quel que soit l'habillage choisi.
abstract final class AppTheme {
  static ThemeData light() => forProfile(ExperienceProfile.pro);

  static ThemeData forProfile(ExperienceProfile profile) {
    final primary = ExperiencePalette.primaryFor(profile);

    final colorScheme = ColorScheme.fromSeed(
      seedColor: primary,
      brightness: Brightness.light,
      primary: primary,
      secondary: AppColors.ochre,
      error: AppColors.negative,
      surface: AppColors.surface,
    );

    return ThemeData(
      useMaterial3: true,
      colorScheme: colorScheme,
      scaffoldBackgroundColor: AppColors.ivory,
      // Repli pour tout texte hors AppTypography (rare) — suit la famille de
      // titres/corps. `null` = police systeme tant que Lot E n'est pas active.
      fontFamily: AppTypography.displayFontFamily,
      textTheme: TextTheme(
        displaySmall: AppTypography.displayXl,
        headlineSmall: AppTypography.titleLarge(primary),
        titleMedium: AppTypography.titleMedium,
        bodyMedium: AppTypography.body,
        bodySmall: AppTypography.caption,
        labelLarge: AppTypography.button,
      ),
      appBarTheme: AppBarTheme(
        backgroundColor: AppColors.ivory,
        foregroundColor: primary,
        elevation: 0,
        scrolledUnderElevation: 1,
        centerTitle: false,
        titleTextStyle: null,
      ),
      cardTheme: CardThemeData(
        // Base "papier" chaude plutot que blanc pur + une ombre basse tres
        // douce : la profondeur ne vient plus du seul filet gris.
        color: AppColors.paper,
        elevation: 0.5,
        shadowColor: AppColors.navy.withValues(alpha: 0.12),
        surfaceTintColor: Colors.transparent,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(AppSpacing.radiusMd),
          side: const BorderSide(color: AppColors.outline),
        ),
        margin: EdgeInsets.zero,
      ),
      dividerTheme: const DividerThemeData(color: AppColors.outline, thickness: 1, space: 1),
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ElevatedButton.styleFrom(
          backgroundColor: primary,
          foregroundColor: Colors.white,
          disabledBackgroundColor: primary.withValues(alpha: 0.4),
          minimumSize: const Size.fromHeight(52),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AppSpacing.radiusSm)),
          textStyle: AppTypography.button,
          elevation: 0,
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          foregroundColor: primary,
          side: const BorderSide(color: AppColors.outline),
          minimumSize: const Size.fromHeight(52),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AppSpacing.radiusSm)),
          textStyle: AppTypography.bodyStrong.copyWith(color: primary),
        ),
      ),
      textButtonTheme: TextButtonThemeData(
        style: TextButton.styleFrom(
          foregroundColor: primary,
          textStyle: AppTypography.bodyStrong.copyWith(color: primary),
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: AppColors.surface,
        contentPadding: const EdgeInsets.symmetric(horizontal: AppSpacing.lg, vertical: AppSpacing.md),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(AppSpacing.radiusSm),
          borderSide: const BorderSide(color: AppColors.outline),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(AppSpacing.radiusSm),
          borderSide: const BorderSide(color: AppColors.outline),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(AppSpacing.radiusSm),
          borderSide: BorderSide(color: primary, width: 1.5),
        ),
        errorBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(AppSpacing.radiusSm),
          borderSide: const BorderSide(color: AppColors.negative),
        ),
        labelStyle: AppTypography.body.copyWith(color: AppColors.inkMuted),
      ),
      // NavigationBar (Material 3) est le widget reellement utilise par
      // AppShell — pas l'ancien BottomNavigationBar, qui a sa propre
      // ThemeData et ne serait jamais applique ici.
      navigationBarTheme: NavigationBarThemeData(
        backgroundColor: AppColors.surface,
        indicatorColor: primary.withValues(alpha: 0.1),
        elevation: 0,
        // 10 (pas 12) : retour client "le texte va a la ligne : fournisseur
        // dans la barre de navigation" -- avec 6 destinations sur un ecran
        // etroit, "Fournisseurs" (le libelle le plus long) ne rentrait pas
        // sur une ligne a 12, meme seul visible (onlyShowSelected).
        labelTextStyle: WidgetStateProperty.resolveWith(
          (states) => TextStyle(
            fontSize: 10,
            fontWeight: states.contains(WidgetState.selected) ? FontWeight.w700 : FontWeight.w500,
            color: states.contains(WidgetState.selected) ? primary : AppColors.inkFaint,
          ),
        ),
        iconTheme: WidgetStateProperty.resolveWith(
          (states) => IconThemeData(
            color: states.contains(WidgetState.selected) ? primary : AppColors.inkFaint,
          ),
        ),
      ),
      snackBarTheme: SnackBarThemeData(
        backgroundColor: AppColors.ink,
        contentTextStyle: AppTypography.body.copyWith(color: Colors.white),
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AppSpacing.radiusSm)),
      ),
    );
  }
}
