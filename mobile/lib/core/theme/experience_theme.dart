import 'package:flutter/material.dart';

import '../../shared/models/current_user.dart';
import 'app_colors.dart';

/// Degrade de marque + couleur de premier plan associee — un degrade pastel
/// clair (Mode Histoire) a besoin d'un texte fonce pour rester lisible,
/// contrairement aux degrades sombres (PRO/Mode Epopee) qui gardent un texte
/// blanc. Jamais suppose implicitement : chaque degrade porte son propre
/// contraste correct.
class ExperienceGradient {
  final LinearGradient gradient;
  final Color foreground;

  const ExperienceGradient({required this.gradient, required this.foreground});
}

/// Point d'acces unique aux couleurs qui varient selon
/// [ExperienceProfile] — jamais le taux, les frais ni aucune regle metier,
/// uniquement l'habillage (mission "differenciation marketing").
abstract final class ExperiencePalette {
  static Color primaryFor(ExperienceProfile profile) => switch (profile) {
        ExperienceProfile.pro => AppColors.navy,
        ExperienceProfile.studentMale => AppColors.epicPrimary,
        ExperienceProfile.studentFemale => AppColors.softMauve,
      };

  static ExperienceGradient gradientFor(ExperienceProfile profile) => switch (profile) {
        ExperienceProfile.pro => const ExperienceGradient(gradient: AppColors.gradient, foreground: Colors.white),
        ExperienceProfile.studentMale =>
          const ExperienceGradient(gradient: AppColors.epicGradient, foreground: Colors.white),
        ExperienceProfile.studentFemale =>
          const ExperienceGradient(gradient: AppColors.storyGradient, foreground: AppColors.navyDark),
      };
}
