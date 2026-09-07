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

/// Textes qui varient selon [ExperienceProfile] (mission "differenciation
/// marketing" : "Taux OR aujourd'hui !" pour STUDENT_MALE, ton chaleureux
/// pour STUDENT_FEMALE) — volontairement limite aux points de contact les
/// plus visibles (accueil), jamais une reecriture de tous les textes de
/// l'app. PRO garde exactement le ton sobre actuel, inchange.
abstract final class ExperienceCopy {
  static String greeting(ExperienceProfile profile, String firstName) => switch (profile) {
        ExperienceProfile.pro => 'Bonjour $firstName',
        ExperienceProfile.studentMale => 'Pret a en decoudre, $firstName ?',
        ExperienceProfile.studentFemale => 'Coucou $firstName',
      };

  static String greetingEmoji(ExperienceProfile profile) => switch (profile) {
        ExperienceProfile.pro => '👋',
        ExperienceProfile.studentMale => '🔥',
        ExperienceProfile.studentFemale => '✨',
      };

  static String homeRateEyebrow(ExperienceProfile profile) => switch (profile) {
        ExperienceProfile.pro => 'TAUX DU MOMENT',
        ExperienceProfile.studentMale => 'TAUX OR AUJOURD\'HUI',
        ExperienceProfile.studentFemale => 'LE TAUX DU JOUR',
      };

  static String payCta(ExperienceProfile profile) => switch (profile) {
        ExperienceProfile.pro => 'Payer un fournisseur',
        ExperienceProfile.studentMale => 'Lancer le transfert',
        ExperienceProfile.studentFemale => 'Envoyer mon transfert',
      };
}
