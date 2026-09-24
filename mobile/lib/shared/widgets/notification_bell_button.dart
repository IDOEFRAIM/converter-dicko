import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

/// Cloche de notifications (retour client sept. 2026 : "le bouton doit etre
/// sur toutes les pages, pas seulement sur Plus -- sinon l'utilisateur peut
/// ne pas savoir [qu'il existe]"). Un seul widget partage, pose dans
/// l'AppBar de chaque onglet racine, plutot que sept copies divergentes du
/// meme IconButton.
class NotificationBellButton extends StatelessWidget {
  const NotificationBellButton({super.key});

  @override
  Widget build(BuildContext context) {
    return IconButton(
      icon: const Icon(Icons.notifications_outlined),
      tooltip: 'Notifications',
      onPressed: () => context.push('/more/notifications'),
    );
  }
}
