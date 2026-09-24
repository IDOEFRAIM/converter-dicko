import 'package:flutter/material.dart';

import '../../core/theme/app_spacing.dart';

/// Indicateur de chargement standard pour un ecran/section reseau. Couleur
/// tiree de `Theme.of(context).colorScheme.primary` (jamais `AppColors.navy`
/// en dur) : deja l'accent du profil courant, voir `AppTheme.forProfile` —
/// omnipresent dans l'app, ce widget est le point de plus fort effet de
/// levier pour l'habillage (mission "differenciation marketing").
class LoadingView extends StatelessWidget {
  const LoadingView({super.key});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: AppSpacing.xxl),
      child: Center(
        child: CircularProgressIndicator(color: Theme.of(context).colorScheme.primary, strokeWidth: 2.5),
      ),
    );
  }
}
