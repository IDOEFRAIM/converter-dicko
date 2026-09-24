import 'dart:math' as math;

import 'package:flutter/widgets.dart';

import '../../core/theme/app_colors.dart';

/// Grain "gravé" déterministe posé sur une surface — quelques centaines de
/// points fins, jamais du bruit plein écran. Donne aux surfaces laque (navy
/// profond) une matière imprimée impossible à reproduire par un simple
/// dégradé (langage de design "Le Comptoir").
///
/// Usage : dans un `Stack`, sous le contenu, en `Positioned.fill(child:
/// LedgerGrain())`. Ne capte jamais les pointeurs.
class LedgerGrain extends StatelessWidget {
  final Color color;
  final double opacity;

  /// Meme [seed] => meme grain : deux surfaces cote a cote ne "moutonnent"
  /// pas de la meme facon si on leur donne des seeds differentes.
  final int seed;

  const LedgerGrain({
    super.key,
    this.color = AppColors.onLacquer,
    this.opacity = 0.05,
    this.seed = 7,
  });

  @override
  Widget build(BuildContext context) {
    return IgnorePointer(
      child: CustomPaint(
        size: Size.infinite,
        painter: _GrainPainter(color.withValues(alpha: opacity), seed),
      ),
    );
  }
}

class _GrainPainter extends CustomPainter {
  final Color color;
  final int seed;

  _GrainPainter(this.color, this.seed);

  @override
  void paint(Canvas canvas, Size size) {
    if (size.isEmpty) return;
    final rnd = math.Random(seed);
    final count = (size.width * size.height / 850).clamp(24, 700).toInt();
    final paint = Paint()..color = color;
    for (var i = 0; i < count; i++) {
      final dx = rnd.nextDouble() * size.width;
      final dy = rnd.nextDouble() * size.height;
      final radius = rnd.nextDouble() < 0.85 ? 0.6 : 1.1;
      canvas.drawCircle(Offset(dx, dy), radius, paint);
    }
  }

  @override
  bool shouldRepaint(covariant _GrainPainter oldDelegate) =>
      oldDelegate.color != color || oldDelegate.seed != seed;
}
