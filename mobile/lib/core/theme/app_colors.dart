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

  // ---- Matiere "Registre" (langage de design "Le Comptoir", voir
  // docs/MOBILE_DESIGN_LANGUAGE.md) ---------------------------------------
  /// Base chaude des cartes — remplace le blanc pur, evoque le papier relie.
  static const paper = Color(0xFFFBF8F1);
  /// Regle "en creux" sur le papier (separateurs internes d'une carte).
  static const paperEdge = Color(0xFFEDE7D9);
  /// Surface premium (hero, rang debloque, recu) — navy laque profond.
  static const lacquer = Color(0xFF10203F);
  static const lacquerEdge = Color(0xFF24365C);
  /// Filet d'or, un seul trait, jamais un aplat — laiton plus rare que l'ochre.
  static const keyline = Color(0xFFC7A54B);
  /// Textes / icones poses sur la laque.
  static const onLacquer = Color(0xFFF2F1EA);
  static const onLacquerMuted = Color(0xFF9FB0CE);
  /// Accent "vivant" — reserve au taux qui bat, jamais un statut metier.
  static const signal = Color(0xFF2FB8A6);
  static const signalGlow = Color(0x332FB8A6);

  static const lacquerGradient = LinearGradient(
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
    colors: [lacquerEdge, lacquer, Color(0xFF0A1730)],
    stops: [0.0, 0.5, 1.0],
  );

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

  // ---- Raccourcis d'accueil (retour client, sept. 2026 : app trop bavarde,
  // veut des pastilles d'icones colorees plutot que du texte — capture de
  // reference fournie). Elargissement delibere et STRICTEMENT LOCALISE aux 4
  // pastilles [QuickActionsRow] de l'accueil : la discipline "le rouge est un
  // accent rare, pas une couleur d'interface" (section 6/7) reste intacte
  // partout ailleurs. Seules 2 teintes sont reellement nouvelles ; les deux
  // autres pastilles reutilisent [signal] et [keyline], deja porteurs de sens
  // (taux vivant, or de marque) plutot que d'inventer une 3e/4e couleur.
  static const shortcutOrange = Color(0xFFFF8A3D);
  static const shortcutOrangeSurface = Color(0xFFFFE9DA);
  static const shortcutViolet = Color(0xFF8B5CF6);
  static const shortcutVioletSurface = Color(0xFFEDE4FB);
  static const signalSurfaceSoft = Color(0xFFDFF3F0);
  static const keylineSurfaceSoft = Color(0xFFFBF0DC);

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
