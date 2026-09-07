import 'package:flutter/material.dart';

/// Palette de marque — Deep Navy / Warm Ivory / Sand-Ochre / Subtle China Red.
///
/// Deliberement etroite (mission section 6/7) : pas de degrade multicolore,
/// pas de neon, pas de cliches culturels (pas de "rouge + or + dragon"). Le
/// rouge est un ACCENT rare, jamais une couleur d'interface dominante.
///
/// Coherent avec le frontend Angular (`styles.scss` : `--brand-navy`,
/// `--brand-gold`, `--brand-red`) — meme identite, deux implementations.
abstract final class AppColors {
  // ---- Marque ----
  static const navy = Color(0xFF1B325E);
  static const navyDark = Color(0xFF0F1E3B);
  static const navyLight = Color(0xFF2E4C82);
  static const ochre = Color(0xFFD3AF37);
  static const chinaRed = Color(0xFFCE1922);

  // ---- Surfaces ----
  static const ivory = Color(0xFFFFFDF8);
  static const ivoryDim = Color(0xFFF4F1E9);
  static const surface = Color(0xFFFFFFFF);
  static const outline = Color(0xFFE6E9F0);

  // ---- Texte / neutres ----
  static const ink = Color(0xFF1A1C1E);
  static const inkMuted = Color(0xFF616161);
  static const inkFaint = Color(0xFF9AA0AB);

  // ---- Semantique ----
  static const positive = Color(0xFF1B5E20);
  static const positiveSurface = Color(0xFFE8F5E9);
  static const negative = Color(0xFFB71C1C);
  static const negativeSurface = Color(0xFFFFEBEE);
  static const warning = Color(0xFF8D6E00);
  static const warningSurface = Color(0xFFFFF8E1);

  static const gradient = LinearGradient(
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
    colors: [navyLight, navy, navyDark],
    stops: [0.0, 0.55, 1.0],
  );

  // ---- Habillage "Mode Epopee" (STUDENT_MALE) — degrade chaud, reutilise
  // ochre/chinaRed deja dans la palette de marque plutot que d'introduire un
  // vocabulaire de couleurs totalement disjoint (mission section 6/7).
  static const epicPrimary = Color(0xFF7A1E3C);

  static const epicGradient = LinearGradient(
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
    colors: [epicPrimary, ochre, chinaRed],
    stops: [0.0, 0.55, 1.0],
  );

  // ---- Habillage "Mode Histoire" (STUDENT_FEMALE) — degrade pastel.
  static const blushPink = Color(0xFFF3C9D8);
  static const softMauve = Color(0xFFB27DC4);
  static const skyBlue = Color(0xFFAEDFF7);

  static const storyGradient = LinearGradient(
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
    colors: [blushPink, softMauve, skyBlue],
    stops: [0.0, 0.55, 1.0],
  );
}
