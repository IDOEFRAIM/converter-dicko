import 'package:flutter/material.dart';

/// Ferme le clavier au lieu de laisser le systeme quitter l'app (retour
/// client : "le clavier ne descend pas, quand il est ouvert on ne peut pas
/// revenir en arriere" -- reproduit sur l'onglet Messagerie : une seule
/// pression retour, clavier ouvert, fermait l'application entiere).
///
/// Sur un ecran qui n'a rien a "pop" au sens de go_router (ex. la racine
/// d'un onglet de la coquille), Android/Flutter considerent qu'il n'y a
/// rien d'autre a faire que fermer l'app -- l'etape habituelle "le systeme
/// ferme le clavier avant de transmettre le retour a l'appli" est alors
/// sautee (predictive back + `OnBackInvokedCallback`, contrairement a
/// l'ancien dispatch clavier-d'abord implicite). Ce garde intercepte le
/// retour UNIQUEMENT quand le clavier est visible, pour le fermer a la
/// place ; sinon (clavier ferme), la navigation normale (y compris quitter
/// l'app s'il n'y a vraiment rien a faire d'autre) n'est jamais alteree.
class DismissKeyboardOnBack extends StatelessWidget {
  final Widget? child;

  const DismissKeyboardOnBack({super.key, required this.child});

  @override
  Widget build(BuildContext context) {
    final keyboardVisible = MediaQuery.of(context).viewInsets.bottom > 0;
    return PopScope(
      canPop: !keyboardVisible,
      onPopInvokedWithResult: (didPop, result) {
        if (!didPop) {
          FocusManager.instance.primaryFocus?.unfocus();
        }
      },
      child: child ?? const SizedBox.shrink(),
    );
  }
}
