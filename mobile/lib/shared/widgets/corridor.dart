import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/auth/auth_session.dart';
import '../../core/theme/app_colors.dart';
import '../../core/theme/app_spacing.dart';

/// Niveau de presence visuelle du corridor — ne jamais utiliser [hero]
/// plusieurs fois sur le meme ecran (mission section 8 : sobre, pas repete
/// partout).
enum CorridorLevel { compact, normal, hero }

/// Langage visuel propriétaire du produit : le corridor Burkina Faso -> Chine
/// (🇧🇫 ┅┅➤ 🇨🇳). Miroir conceptuel de `CorridorComponent` cote Angular, avec
/// sa propre traduction Flutter (ligne pointillee + fleche dessinees, pas de
/// widget generique reutilise tel quel — le mobile a sa propre UX, section 5).
///
/// Les libelles restent generiques ("Burkina Faso"/"Chine") par defaut :
/// jamais une ville inventee quand le backend n'en fournit pas.
class Corridor extends StatelessWidget {
  final String leftLabel;
  final String rightLabel;
  final CorridorLevel level;

  const Corridor({
    super.key,
    this.leftLabel = 'Burkina Faso',
    this.rightLabel = 'Chine',
    this.level = CorridorLevel.normal,
  });

  @override
  Widget build(BuildContext context) {
    if (level == CorridorLevel.hero) {
      final experienceGradient = context.watch<AuthSession>().experienceGradient;
      return DecoratedBox(
        decoration: BoxDecoration(
          gradient: experienceGradient.gradient,
          borderRadius: BorderRadius.circular(AppSpacing.radiusMd),
        ),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: AppSpacing.lg, vertical: AppSpacing.lg),
          child: _row(
            flagSize: 26,
            labelSize: 15,
            lineColor: experienceGradient.foreground,
            labelColor: experienceGradient.foreground,
          ),
        ),
      );
    }

    final flagSize = level == CorridorLevel.compact ? 13.0 : 18.0;
    final labelSize = level == CorridorLevel.compact ? 11.0 : 13.0;
    return _row(flagSize: flagSize, labelSize: labelSize, lineColor: AppColors.navy, labelColor: AppColors.ink);
  }

  Widget _row({
    required double flagSize,
    required double labelSize,
    required Color lineColor,
    required Color labelColor,
  }) {
    final labelStyle = TextStyle(fontSize: labelSize, fontWeight: FontWeight.w700, color: labelColor);
    return Row(
      children: [
        Text('🇧🇫', style: TextStyle(fontSize: flagSize)),
        const SizedBox(width: AppSpacing.xs),
        Text(leftLabel, style: labelStyle),
        Expanded(
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: AppSpacing.sm),
            child: SizedBox(
              height: 10,
              child: CustomPaint(painter: _DashedArrowPainter(color: lineColor.withValues(alpha: 0.75))),
            ),
          ),
        ),
        Text(rightLabel, style: labelStyle),
        const SizedBox(width: AppSpacing.xs),
        Text('🇨🇳', style: TextStyle(fontSize: flagSize)),
      ],
    );
  }
}

/// Ligne pointillee se terminant par une fleche — dessinee (pas de widget
/// generique de bordure en pointilles dans le SDK Flutter).
class _DashedArrowPainter extends CustomPainter {
  final Color color;

  const _DashedArrowPainter({required this.color});

  static const _dashWidth = 5.0;
  static const _dashGap = 4.0;
  static const _arrowSize = 6.0;

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = color
      ..strokeWidth = 2
      ..style = PaintingStyle.stroke;
    final midY = size.height / 2;
    final lineEnd = size.width - _arrowSize;

    var x = 0.0;
    while (x < lineEnd) {
      final segmentEnd = (x + _dashWidth).clamp(0.0, lineEnd);
      canvas.drawLine(Offset(x, midY), Offset(segmentEnd, midY), paint);
      x += _dashWidth + _dashGap;
    }

    final arrowPaint = Paint()
      ..color = color
      ..style = PaintingStyle.fill;
    final path = Path()
      ..moveTo(lineEnd, midY - _arrowSize / 1.4)
      ..lineTo(size.width, midY)
      ..lineTo(lineEnd, midY + _arrowSize / 1.4)
      ..close();
    canvas.drawPath(path, arrowPaint);
  }

  @override
  bool shouldRepaint(covariant _DashedArrowPainter oldDelegate) => oldDelegate.color != color;
}
