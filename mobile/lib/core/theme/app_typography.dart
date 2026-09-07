import 'package:flutter/material.dart';

import 'app_colors.dart';

/// Echelle typographique unique. Police systeme (pas de police custom
/// chargee pour l'instant — voir mission section 43, ne pas ajouter de
/// dependance sans besoin reel).
abstract final class AppTypography {
  static const _base = TextStyle(color: AppColors.ink, fontFamily: null);

  /// Grand chiffre dominant (taux, montant) — un seul par ecran (meme regle
  /// que `.metric` cote Angular : jamais deux valeurs de ce poids cote a cote).
  static final metricLarge = _base.copyWith(
    fontSize: 32,
    fontWeight: FontWeight.w800,
    letterSpacing: -0.5,
    height: 1.05,
  );

  static final metricMedium = _base.copyWith(
    fontSize: 22,
    fontWeight: FontWeight.w800,
    letterSpacing: -0.3,
    height: 1.1,
  );

  /// Prend l'accent en parametre (jamais `AppColors.navy` en dur) : un titre
  /// de page doit refleter l'habillage du profil courant — passer
  /// `Theme.of(context).colorScheme.primary`, deja le bon accent (voir
  /// `AppTheme.forProfile`).
  static TextStyle titleLarge(Color accent) => _base.copyWith(fontSize: 22, fontWeight: FontWeight.w700, color: accent);

  static final titleMedium = _base.copyWith(fontSize: 17, fontWeight: FontWeight.w700);

  static final body = _base.copyWith(fontSize: 15, fontWeight: FontWeight.w400, height: 1.4);

  static final bodyStrong = _base.copyWith(fontSize: 15, fontWeight: FontWeight.w600);

  static final caption = _base.copyWith(fontSize: 13, color: AppColors.inkMuted, height: 1.3);

  static final eyebrow = _base.copyWith(
    fontSize: 12,
    fontWeight: FontWeight.w700,
    color: AppColors.inkMuted,
    letterSpacing: 0.6,
  );

  static final button = _base.copyWith(fontSize: 15, fontWeight: FontWeight.w700, color: Colors.white);
}
