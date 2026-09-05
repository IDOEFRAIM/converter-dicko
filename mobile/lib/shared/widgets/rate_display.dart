import 'package:flutter/material.dart';

import '../../core/theme/app_colors.dart';
import '../../core/theme/app_typography.dart';

/// Affichage du taux client "1 CNY = X XOF" — la valeur `customerRate` est
/// affichee TELLE QUE RENVOYEE par le backend, sans arrondi ni reformatage
/// (meme convention que cote Angular : jamais de calcul de taux cote client).
class RateDisplay extends StatelessWidget {
  final String customerRate;
  final bool large;
  final Color? color;

  const RateDisplay({super.key, required this.customerRate, this.large = false, this.color});

  @override
  Widget build(BuildContext context) {
    final style = large ? AppTypography.metricLarge : AppTypography.metricMedium;
    return RichText(
      text: TextSpan(
        style: style.copyWith(color: color ?? AppColors.ink),
        children: [
          const TextSpan(text: '1 CNY = '),
          TextSpan(text: customerRate),
          const TextSpan(text: ' XOF'),
        ],
      ),
    );
  }
}
