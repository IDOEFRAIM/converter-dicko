import 'package:flutter/material.dart';

import '../../../core/errors/api_exception.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../core/theme/app_typography.dart';
import '../../auth/data/auth_repository.dart';

/// Confirmation de suppression de compte (retour client : "est-ce que
/// l'utilisateur a la possibilite de supprimer ses donnees dans
/// l'application ?"). Un seul ecran gere la confirmation ET l'appel reseau
/// (au lieu d'un simple `showDialog` a deux boutons) : il faut pouvoir
/// afficher l'erreur backend (transfert en cours, mot de passe incorrect)
/// SANS fermer la boite de dialogue, comme les formulaires de l'app le font
/// deja ailleurs (voir LoginPage._errorMessage).
class DeleteAccountDialog extends StatefulWidget {
  final AuthRepository authRepository;

  /// Faux pour un compte cree via Google Sign-In (voir `CurrentUser.hasPassword`) :
  /// aucun champ mot de passe n'a alors de sens, le backend l'ignore de toute facon.
  final bool requiresPassword;

  const DeleteAccountDialog({super.key, required this.authRepository, required this.requiresPassword});

  @override
  State<DeleteAccountDialog> createState() => _DeleteAccountDialogState();
}

class _DeleteAccountDialogState extends State<DeleteAccountDialog> {
  final _passwordController = TextEditingController();
  bool _submitting = false;
  String? _errorMessage;

  @override
  void dispose() {
    _passwordController.dispose();
    super.dispose();
  }

  Future<void> _confirm() async {
    setState(() {
      _submitting = true;
      _errorMessage = null;
    });
    try {
      await widget.authRepository.deleteAccount(
        currentPassword: widget.requiresPassword ? _passwordController.text : null,
      );
      if (mounted) Navigator.of(context).pop(true);
      // La session est deja purgee par AuthRepository.deleteAccount : le routeur
      // redirige seul vers /login (refreshListenable), meme principe que logout().
    } on ApiException catch (error) {
      if (!mounted) return;
      setState(() {
        _errorMessage = error.message;
        _submitting = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Supprimer votre compte ?'),
      content: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Cette action est definitive : vos informations personnelles (nom, telephone, '
              "piece d'identite) seront effacees. Vos transferts deja effectues restent "
              'visibles par notre equipe, comme l\'exige la reglementation.\n\n'
              "Impossible si un transfert est encore en cours : terminez-le, annulez-le ou "
              "attendez son rejet d'abord.",
            ),
            if (widget.requiresPassword) ...[
              const SizedBox(height: AppSpacing.lg),
              TextField(
                controller: _passwordController,
                obscureText: true,
                autofocus: true,
                decoration: const InputDecoration(labelText: 'Confirmez votre mot de passe'),
                onSubmitted: (_) => _submitting ? null : _confirm(),
              ),
            ],
            if (_errorMessage != null) ...[
              const SizedBox(height: AppSpacing.md),
              Text(_errorMessage!, style: AppTypography.body.copyWith(color: AppColors.negative)),
            ],
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: _submitting ? null : () => Navigator.of(context).pop(false),
          child: const Text('Annuler'),
        ),
        TextButton(
          // Un mot de passe vide/incorrect est refuse par le backend avec un message clair
          // (_errorMessage) -- pas besoin de dupliquer cette validation ici (et un simple
          // TextEditingController sans listener ne reconstruirait pas ce bouton a chaque frappe).
          onPressed: _submitting ? null : _confirm,
          child: _submitting
              ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2))
              : const Text('Supprimer', style: TextStyle(color: AppColors.negative)),
        ),
      ],
    );
  }
}
