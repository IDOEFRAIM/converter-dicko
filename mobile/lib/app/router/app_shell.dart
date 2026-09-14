import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../features/achievements/presentation/badge_unlock_overlay.dart';

/// Coquille de navigation persistante — 5 destinations principales +
/// "Plus" pour les fonctionnalites secondaires (mission section 19).
/// Chaque branche garde sa propre pile de navigation (`StatefulShellRoute`),
/// donc revenir sur un onglet retrouve son etat de defilement/formulaire.
///
/// "Messagerie" est un onglet de premier niveau, pas une entree dans "Plus"
/// (retour client sept. 2026 : la reclamation doit se voir "du premier
/// coup", sans que l'utilisateur ait a deviner ou elle se trouve).
class AppShell extends StatelessWidget {
  final StatefulNavigationShell navigationShell;

  const AppShell({super.key, required this.navigationShell});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      // Enveloppe unique pour l'overlay "nouveau rang debloque" : il doit
      // pouvoir apparaitre au-dessus de n'importe quel onglet.
      body: BadgeCelebrationHost(child: navigationShell),
      bottomNavigationBar: NavigationBar(
        selectedIndex: navigationShell.currentIndex,
        onDestinationSelected: (index) => navigationShell.goBranch(
          index,
          // Retape sur l'onglet deja actif -> revient a sa racine, comme sur
          // la plupart des apps mobiles (au lieu de rester profondement
          // navigue dans cet onglet).
          initialLocation: index == navigationShell.currentIndex,
        ),
        destinations: const [
          NavigationDestination(icon: Icon(Icons.home_outlined), selectedIcon: Icon(Icons.home), label: 'Accueil'),
          NavigationDestination(icon: Icon(Icons.send_outlined), selectedIcon: Icon(Icons.send), label: 'Payer'),
          NavigationDestination(
            icon: Icon(Icons.storefront_outlined),
            selectedIcon: Icon(Icons.storefront),
            label: 'Fournisseurs',
          ),
          NavigationDestination(
            icon: Icon(Icons.receipt_long_outlined),
            selectedIcon: Icon(Icons.receipt_long),
            label: 'Activite',
          ),
          NavigationDestination(
            icon: Icon(Icons.forum_outlined),
            selectedIcon: Icon(Icons.forum),
            label: 'Messages',
          ),
          NavigationDestination(icon: Icon(Icons.more_horiz), selectedIcon: Icon(Icons.more_horiz), label: 'Plus'),
        ],
      ),
    );
  }
}
