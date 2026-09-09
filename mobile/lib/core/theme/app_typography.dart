import 'dart:ui' show FontFeature;

import 'package:flutter/material.dart';

import 'app_colors.dart';

/// Echelle typographique unique (langage de design "Le Comptoir", voir
/// docs/MOBILE_DESIGN_LANGUAGE.md).
///
/// Ce qui fait la difference meme en police systeme :
///  - **contraste fort** de graisse et de taille entre titres et corps ;
///  - **tracking resserre** sur les titres (allure "gravee", pas "web") ;
///  - **figures tabulaires + zero barre** sur TOUT ce qui est chiffre
///    (`figure*`) : les montants/taux/XP ne "sautent" jamais quand ils
///    changent, comme un tableau de cotation.
///
/// **Lot E — polices de marque.** Tout le texte passe par [_base] (titres,
/// corps, sur-titres) ou [_figure] (chiffres). Ces deux styles portent
/// [displayFontFamily] / [figureFontFamily] : c'est l'unique point de bascule.
/// Ils valent `null` (police systeme) tant que les fichiers `.ttf` ne sont pas
/// deposes dans `assets/fonts/` et declares dans `pubspec.yaml` — declarer une
/// famille sans le fichier casserait le build. Procedure d'activation :
/// `mobile/assets/fonts/README.md`. Cible recommandee : `Sora` (display
/// geometrique) pour [_base], `IBM Plex Mono` (chasse fixe) pour [_figure].
///
/// On **n'ajoute pas** le paquet `google_fonts` : par defaut il telecharge la
/// police au premier usage (reseau) — inacceptable pour une app de paiement
/// utilisee en connectivite intermittente ; et le desactiver impose de toute
/// facon d'embarquer le `.ttf`, ce que fait deja le mecanisme natif ci-dessous
/// sans nouvelle dependance non verifiable ici (`flutter pub get` indisponible).
///
/// Les noms de tokens existants (`metricLarge`, `body`, `eyebrow`...) sont
/// conserves a l'identique : aucun ecran a modifier.
abstract final class AppTypography {
  /// Famille des titres / du corps. `null` => police systeme de la plateforme.
  /// Passer a `'Sora'` apres avoir active le bloc `fonts:` du `pubspec.yaml`.
  static const String? displayFontFamily = null;

  /// Famille des chiffres (chasse fixe). `null` => police systeme.
  /// Passer a `'IBM Plex Mono'` apres activation du bloc `fonts:`.
  static const String? figureFontFamily = null;

  static const _base = TextStyle(color: AppColors.ink, fontFamily: displayFontFamily);

  /// Style de base des chiffres : alignement en colonne + zero barre.
  static const _figure = TextStyle(
    color: AppColors.ink,
    fontFamily: figureFontFamily,
    fontFeatures: [FontFeature.tabularFigures(), FontFeature.slashedZero()],
  );

  // ---- Affichage / hero ---------------------------------------------------

  /// Un seul par ecran, reserve aux moments (hero d'accueil, rang debloque).
  static final displayXl = _base.copyWith(
    fontSize: 40,
    fontWeight: FontWeight.w800,
    letterSpacing: -1.1,
    height: 1.02,
  );

  // ---- Chiffres dominants ----------------------------------------------

  /// Grand chiffre dominant (taux, montant) — un seul par ecran.
  static final metricLarge = _figure.copyWith(
    fontSize: 32,
    fontWeight: FontWeight.w800,
    letterSpacing: -0.8,
    height: 1.04,
  );

  static final metricMedium = _figure.copyWith(
    fontSize: 22,
    fontWeight: FontWeight.w800,
    letterSpacing: -0.4,
    height: 1.1,
  );

  /// Chiffres "tableau de bord" (XP, volume du mois, compteur) — penses pour
  /// [CountUpText] et l'alignement a droite dans une colonne.
  static final figureLarge = _figure.copyWith(fontSize: 34, fontWeight: FontWeight.w800, letterSpacing: -0.9, height: 1);
  static final figureMedium = _figure.copyWith(fontSize: 20, fontWeight: FontWeight.w700, letterSpacing: -0.3);
  static final figureSmall = _figure.copyWith(fontSize: 14, fontWeight: FontWeight.w600, color: AppColors.inkMuted);

  // ---- Titres / corps ---------------------------------------------------

  /// Prend l'accent en parametre (jamais `AppColors.navy` en dur) : un titre
  /// de page reflete l'habillage du profil courant — passer
  /// `Theme.of(context).colorScheme.primary`.
  static TextStyle titleLarge(Color accent) => _base.copyWith(
        fontSize: 22,
        fontWeight: FontWeight.w800,
        letterSpacing: -0.5,
        color: accent,
      );

  static final titleMedium = _base.copyWith(fontSize: 17, fontWeight: FontWeight.w700, letterSpacing: -0.2);

  static final body = _base.copyWith(fontSize: 15, fontWeight: FontWeight.w400, height: 1.45);

  static final bodyStrong = _base.copyWith(fontSize: 15, fontWeight: FontWeight.w600, letterSpacing: -0.1);

  static final caption = _base.copyWith(fontSize: 13, color: AppColors.inkMuted, height: 1.35);

  /// Sur-titre "gravé" — capitales, tracking large, comme une mention portee
  /// sur un registre.
  static final eyebrow = _base.copyWith(
    fontSize: 11.5,
    fontWeight: FontWeight.w800,
    color: AppColors.inkMuted,
    letterSpacing: 1.4,
  );

  static final button = _base.copyWith(fontSize: 15, fontWeight: FontWeight.w800, letterSpacing: 0.1, color: Colors.white);
}
