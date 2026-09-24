import 'package:flutter/material.dart';

import '../../core/theme/app_colors.dart';
import '../../core/theme/app_spacing.dart';

enum _Tone { neutral, progress, positive, negative }

/// Pastille de statut reutilisable pour Quote/Order/Payment/Settlement/RateAlert.
///
/// Miroir exact de `StatusBadgeComponent` cote Angular (memes libelles
/// francais, meme mapping de tonalite) — le mobile ne doit jamais inventer
/// un second vocabulaire de statut pour le meme code backend.
class StatusBadge extends StatelessWidget {
  final String status;

  const StatusBadge({super.key, required this.status});

  static const Map<String, String> _labels = {
    'AWAITING_PAYMENT': 'En attente de paiement',
    'PAYMENT_SUBMITTED': 'Paiement soumis',
    'PAYMENT_VERIFIED': 'Paiement verifie',
    'PROCESSING': 'En cours de traitement',
    'COMPLETED': 'Termine',
    'CANCELLED': 'Annule',
    'REJECTED': 'Rejete',
    'EXPIRED': 'Expire',
    'SUBMITTED': 'Soumis',
    'CONFIRMED': 'Confirme',
    'ACTIVE': 'Actif',
    'ACCEPTED': 'Accepte',
    'PENDING': 'En attente',
    'EXECUTED': 'Execute',
    'STARTED': 'Demarre',
    'INACTIVE': 'Desactive',
    'TRIGGERED': 'Declenchee',
  };

  static const Map<String, _Tone> _tones = {
    'AWAITING_PAYMENT': _Tone.neutral,
    'PAYMENT_SUBMITTED': _Tone.progress,
    'PAYMENT_VERIFIED': _Tone.progress,
    'PROCESSING': _Tone.progress,
    'COMPLETED': _Tone.positive,
    'CANCELLED': _Tone.negative,
    'REJECTED': _Tone.negative,
    'EXPIRED': _Tone.negative,
    'SUBMITTED': _Tone.progress,
    'CONFIRMED': _Tone.positive,
    'ACTIVE': _Tone.neutral,
    'ACCEPTED': _Tone.progress,
    'STARTED': _Tone.progress,
    'PENDING': _Tone.neutral,
    'EXECUTED': _Tone.positive,
    'INACTIVE': _Tone.neutral,
    'TRIGGERED': _Tone.positive,
  };

  @override
  Widget build(BuildContext context) {
    final tone = _tones[status] ?? _Tone.neutral;
    final label = _labels[status] ?? status;
    final (background, foreground) = switch (tone) {
      _Tone.neutral => (const Color(0xFFECEFF1), const Color(0xFF37474F)),
      _Tone.progress => (const Color(0xFFE3F2FD), const Color(0xFF0D47A1)),
      _Tone.positive => (AppColors.positiveSurface, AppColors.positive),
      _Tone.negative => (AppColors.negativeSurface, AppColors.negative),
    };

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: AppSpacing.md, vertical: AppSpacing.xs),
      decoration: BoxDecoration(color: background, borderRadius: BorderRadius.circular(AppSpacing.radiusPill)),
      child: Text(
        label,
        style: TextStyle(color: foreground, fontSize: 13, fontWeight: FontWeight.w600),
      ),
    );
  }
}
