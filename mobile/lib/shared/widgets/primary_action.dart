import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/auth/auth_session.dart';
import '../../core/theme/app_colors.dart';
import '../../core/theme/app_spacing.dart';

/// Action principale (CTA) de l'ecran — degrade de marque, jamais plusieurs
/// boutons de ce poids visuel sur le meme ecran (meme convention que
/// `.brand-button` cote Angular). Gere son propre etat de chargement pour
/// eviter un double-tap pendant un appel reseau en cours.
class PrimaryAction extends StatelessWidget {
  final String label;
  final VoidCallback? onPressed;
  final bool loading;
  final IconData? icon;

  const PrimaryAction({
    super.key,
    required this.label,
    required this.onPressed,
    this.loading = false,
    this.icon,
  });

  @override
  Widget build(BuildContext context) {
    final disabled = loading || onPressed == null;
    final experienceGradient = context.watch<AuthSession>().experienceGradient;
    final foreground = experienceGradient.foreground;
    return DecoratedBox(
      decoration: BoxDecoration(
        gradient: disabled ? null : experienceGradient.gradient,
        color: disabled ? AppColors.navy.withValues(alpha: 0.35) : null,
        borderRadius: BorderRadius.circular(AppSpacing.radiusSm),
      ),
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          borderRadius: BorderRadius.circular(AppSpacing.radiusSm),
          onTap: disabled ? null : onPressed,
          child: SizedBox(
            height: 52,
            child: Center(
              child: loading
                  ? const SizedBox(
                      width: 22,
                      height: 22,
                      // Toujours blanc : `loading` implique `disabled`, et le fond bascule
                      // alors sur un navy translucide (jamais le degrade), quel que soit
                      // le profil — voir la decoration ci-dessus.
                      child: CircularProgressIndicator(strokeWidth: 2.5, color: Colors.white),
                    )
                  : Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        if (icon != null) ...[
                          Icon(icon, color: foreground, size: 20),
                          const SizedBox(width: AppSpacing.sm),
                        ],
                        Text(
                          label,
                          style: TextStyle(color: foreground, fontWeight: FontWeight.w700, fontSize: 15),
                        ),
                      ],
                    ),
            ),
          ),
        ),
      ),
    );
  }
}
