import 'package:flutter/material.dart';

/// Bouton "Continuer avec Google" partage entre connexion et inscription —
/// meme parcours des deux ecrans (voir `runGoogleSignIn`).
///
/// Le "G" en pastille est un repere sobre, pas le logo multicolore officiel
/// Google (aucun asset image dans ce depot) : suffisant pour la
/// reconnaissance immediate du bouton, a remplacer par l'asset officiel lors
/// d'une passe de finition visuelle si souhaite — n'affecte en rien le
/// fonctionnement.
class GoogleSignInButton extends StatelessWidget {
  final VoidCallback? onPressed;
  final bool loading;

  const GoogleSignInButton({super.key, required this.onPressed, this.loading = false});

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: double.infinity,
      child: OutlinedButton.icon(
        onPressed: loading ? null : onPressed,
        icon: loading
            ? const SizedBox(
                width: 16,
                height: 16,
                child: CircularProgressIndicator(strokeWidth: 2),
              )
            : const _GoogleMark(),
        label: Text(loading ? 'Connexion...' : 'Continuer avec Google'),
      ),
    );
  }
}

class _GoogleMark extends StatelessWidget {
  const _GoogleMark();

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 18,
      height: 18,
      alignment: Alignment.center,
      decoration: const BoxDecoration(color: Color(0xFF4285F4), shape: BoxShape.circle),
      child: const Text(
        'G',
        style: TextStyle(color: Colors.white, fontSize: 12, fontWeight: FontWeight.w800, height: 1),
      ),
    );
  }
}
