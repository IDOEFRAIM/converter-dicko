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

/// Couleur d'une pastille d'action ([IconBadge]) : icone + fond clair assorti.
class AccentColor {
  final Color color;
  final Color surface;

  const AccentColor(this.color, this.surface);
}

/// Role semantique d'une pastille — jamais une couleur directement : la
/// couleur reelle varie avec [ExperienceProfile] (voir [ExperiencePalette.accentFor]).
/// [send] : payer/envoyer/proforma (parcours de paiement). [group] : Ruee
/// collective/moment personnel (souvenir). [rate] : taux preferentiel/alertes
/// de taux. [premium] : portefeuille/justificatif officiel — tout ce qui est
/// "de valeur, officiel".
enum AccentRole { send, group, rate, premium }

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

  /// Couleur d'une pastille d'action pour [profile] et [role] — retour
  /// client, sept. 2026 : "les couleurs doivent etre alignees avec les
  /// differents types de profil". Point d'acces UNIQUE : aucun ecran ne doit
  /// lire `AppColors.shortcut*`/`epic*`/`story*` directement pour un
  /// [IconBadge] de navigation, toujours passer par cette fonction — sinon
  /// un habillage se retrouve avec les couleurs d'un autre.
  static AccentColor accentFor(ExperienceProfile profile, AccentRole role) {
    switch (profile) {
      case ExperienceProfile.pro:
        return switch (role) {
          AccentRole.send => const AccentColor(AppColors.shortcutOrange, AppColors.shortcutOrangeSurface),
          AccentRole.group => const AccentColor(AppColors.shortcutViolet, AppColors.shortcutVioletSurface),
          AccentRole.rate => const AccentColor(AppColors.signal, AppColors.signalSurfaceSoft),
          AccentRole.premium => const AccentColor(AppColors.keyline, AppColors.keylineSurfaceSoft),
        };
      case ExperienceProfile.studentMale:
        // "Mode Epopee" : les 3 teintes du degrade epique servent aux 3
        // premiers roles ; seul "premium" (ambre) est une teinte nouvelle.
        return switch (role) {
          AccentRole.send => const AccentColor(AppColors.chinaRed, AppColors.chinaRedSurface),
          AccentRole.group => const AccentColor(AppColors.epicPrimary, AppColors.epicPrimarySurface),
          AccentRole.rate => const AccentColor(AppColors.ochre, AppColors.keylineSurfaceSoft),
          AccentRole.premium => const AccentColor(AppColors.epicAmber, AppColors.epicAmberSurface),
        };
      case ExperienceProfile.studentFemale:
        // "Mode Histoire" : blushPink/skyBlue servent de FOND (ils sont deja
        // clairs) a une icone plus saturee (storyPink/storyBlue). "premium"
        // reste l'or de marque : le portefeuille est un repere universel,
        // pas un moment d'habillage.
        return switch (role) {
          AccentRole.send => const AccentColor(AppColors.softMauve, AppColors.storyMauveSurface),
          AccentRole.group => const AccentColor(AppColors.storyPink, AppColors.blushPink),
          AccentRole.rate => const AccentColor(AppColors.storyBlue, AppColors.skyBlue),
          AccentRole.premium => const AccentColor(AppColors.keyline, AppColors.keylineSurfaceSoft),
        };
    }
  }
}

/// Textes qui varient selon [ExperienceProfile] (mission "differenciation
/// marketing" : "Taux OR aujourd'hui !" pour STUDENT_MALE, ton chaleureux
/// pour STUDENT_FEMALE) — volontairement limite aux points de contact les
/// plus visibles (accueil), jamais une reecriture de tous les textes de
/// l'app. PRO garde exactement le ton sobre actuel, inchange.
abstract final class ExperienceCopy {
  static String greeting(ExperienceProfile profile, String firstName) => switch (profile) {
        ExperienceProfile.pro => 'Bonjour $firstName',
        ExperienceProfile.studentMale => 'Pret pour de bonnes affaires, $firstName ?',
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
