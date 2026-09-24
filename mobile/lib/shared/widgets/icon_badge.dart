import 'package:flutter/material.dart';

/// Pastille ronde coloree (icone) — langage de design partage par
/// [QuickActionsRow] (accueil) et toutes les cartes/listes qui en decoulent :
/// une action ou une section se reconnait d'abord a sa couleur et son icone,
/// le texte vient en second (retour client, sept. 2026 : "app intuitive, on
/// doit clairement comprendre comment ca fonctionne", moins de texte).
class IconBadge extends StatelessWidget {
  final IconData icon;
  final Color color;
  final Color? background;
  final double size;

  const IconBadge({super.key, required this.icon, required this.color, this.background, this.size = 40});

  @override
  Widget build(BuildContext context) {
    return Container(
      width: size,
      height: size,
      alignment: Alignment.center,
      decoration: BoxDecoration(color: background ?? color.withValues(alpha: 0.12), shape: BoxShape.circle),
      child: Icon(icon, color: color, size: size * 0.45),
    );
  }
}
