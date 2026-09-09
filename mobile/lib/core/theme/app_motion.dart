import 'package:flutter/animation.dart';
import 'package:flutter/physics.dart';
import 'package:flutter/widgets.dart';

/// Vocabulaire de mouvement unique de l'app (langage de design "Le Comptoir",
/// voir docs/MOBILE_DESIGN_LANGUAGE.md).
///
/// Un widget n'invente jamais sa propre duree/courbe : cartes qui se posent,
/// chiffres qui roulent, corridor qui coule, sceau qui "frappe" a l'ecran —
/// tout partage ces constantes pour que l'app bouge d'une seule main.
abstract final class AppMotion {
  /// Retour tactile immediat (etat presse d'un bouton).
  static const instant = Duration(milliseconds: 90);

  /// Micro-transition (apparition d'un libelle, bascule d'onglet interne).
  static const quick = Duration(milliseconds: 180);

  /// Transition standard (ouverture d'une carte, transition de page).
  static const base = Duration(milliseconds: 280);

  /// Moment appuye (overlay de rang, revelation d'un total).
  static const slow = Duration(milliseconds: 460);

  /// Boucle d'ambiance — la lumiere qui parcourt le corridor.
  static const ambient = Duration(milliseconds: 5200);

  static const enter = Curves.easeOutCubic;
  static const exit = Curves.easeInCubic;

  /// Legerement au-dela puis retour — pour ce qui doit "arriver", pas juste
  /// apparaitre (sceau de rang, badge).
  static const emphatic = Curves.easeOutBack;

  /// Ressort partage : `AnimationController.animateWith(SpringSimulation(...))`
  /// ou `.forward()` sur un `SpringDescription`. Rigide mais amorti — se pose
  /// sans rebondir de facon gadget.
  static const spring = SpringDescription(mass: 1, stiffness: 520, damping: 26);

  /// `true` si l'utilisateur a demande la reduction des animations : le
  /// corridor et les compteurs doivent alors se figer sur leur etat final
  /// plutot que s'animer (accessibilite).
  static bool reduceMotion(BuildContext context) => MediaQuery.maybeOf(context)?.disableAnimations ?? false;
}
