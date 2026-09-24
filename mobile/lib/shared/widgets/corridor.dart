import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/auth/auth_session.dart';
import '../../core/theme/app_colors.dart';
import '../../core/theme/app_spacing.dart';
import 'corridor_flow.dart';
import 'grain.dart';

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
      final fg = experienceGradient.foreground;
      return ClipRRect(
        borderRadius: BorderRadius.circular(AppSpacing.radiusMd),
        child: DecoratedBox(
          decoration: BoxDecoration(
            gradient: experienceGradient.gradient,
            border: Border.all(color: AppColors.keyline.withValues(alpha: 0.45)),
            borderRadius: BorderRadius.circular(AppSpacing.radiusMd),
          ),
          child: Stack(
            children: [
              Positioned.fill(child: LedgerGrain(color: fg, opacity: 0.06)),
              Padding(
                padding: const EdgeInsets.fromLTRB(AppSpacing.lg, AppSpacing.lg, AppSpacing.lg, AppSpacing.md),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    // Extremites nommees, sans ligne : le fil anime la relie.
                    Row(
                      children: [
                        Flexible(child: _endpoint('🇧🇫', leftLabel, fg)),
                        const Spacer(),
                        Flexible(child: _endpoint('🇨🇳', rightLabel, fg, trailing: true)),
                      ],
                    ),
                    CorridorFlow(color: fg, height: 56),
                  ],
                ),
              ),
            ],
          ),
        ),
      );
    }

    final flagSize = level == CorridorLevel.compact ? 13.0 : 18.0;
    final labelSize = level == CorridorLevel.compact ? 11.0 : 13.0;
    return _row(
      flagSize: flagSize,
      labelSize: labelSize,
      lineColor: Theme.of(context).colorScheme.primary,
      labelColor: AppColors.ink,
    );
  }

  /// Extremite nommee du corridor hero (drapeau + libelle).
  Widget _endpoint(String flag, String label, Color color, {bool trailing = false}) {
    final children = <Widget>[
      Text(flag, style: const TextStyle(fontSize: 22)),
      const SizedBox(width: AppSpacing.xs),
      Flexible(
        child: Text(
          label,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: TextStyle(fontSize: 14, fontWeight: FontWeight.w800, letterSpacing: -0.2, color: color),
        ),
      ),
    ];
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: trailing ? children.reversed.toList() : children,
    );
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
