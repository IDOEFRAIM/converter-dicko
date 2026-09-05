import 'package:flutter/material.dart';

import '../../core/theme/app_colors.dart';
import '../../core/theme/app_typography.dart';
import '../models/money.dart';

enum MoneyDisplaySize { large, medium, small }

/// Affichage standard d'un montant — jamais un `Text('$amount XOF')` construit
/// a la main dans une page (mission section 17/34). Le formatage passe
/// systematiquement par [Money.formatted], sans arithmetique flottante.
class MoneyDisplay extends StatelessWidget {
  final Money money;
  final MoneyDisplaySize size;
  final Color? color;
  final bool showCurrency;

  const MoneyDisplay({
    super.key,
    required this.money,
    this.size = MoneyDisplaySize.medium,
    this.color,
    this.showCurrency = true,
  });

  @override
  Widget build(BuildContext context) {
    final style = switch (size) {
      MoneyDisplaySize.large => AppTypography.metricLarge,
      MoneyDisplaySize.medium => AppTypography.metricMedium,
      MoneyDisplaySize.small => AppTypography.bodyStrong,
    };
    final text = showCurrency ? money.formattedWithCurrency() : money.formatted();
    return Text(text, style: style.copyWith(color: color ?? AppColors.ink));
  }
}
