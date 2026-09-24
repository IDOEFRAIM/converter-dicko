import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../../core/theme/app_colors.dart';
import '../../core/theme/app_spacing.dart';
import '../../core/theme/app_typography.dart';
import 'objective_dial.dart';

/// Jauge de collecte d'une « Ruée collective » (langage de design
/// « Le Comptoir », voir docs/MOBILE_DESIGN_LANGUAGE.md, Lot I) : un
/// **manomètre** (réutilise [ObjectiveDial], la même famille d'instruments que
/// la console PRO et le méridien XP) plutôt qu'une barre linéaire plate. À
/// l'objectif, l'aiguille se fige et un **sceau se frappe** au centre — jamais
/// de confettis.
///
/// Purement une vue : reçoit des montants déjà formatés, ne calcule rien.
class CollectionGauge extends StatelessWidget {
  final double progress;
  final String currentLabel;
  final String targetLabel;
  final bool succeeded;

  const CollectionGauge({
    super.key,
    required this.progress,
    required this.currentLabel,
    required this.targetLabel,
    required this.succeeded,
  });

  @override
  Widget build(BuildContext context) {
    return ObjectiveDial(
      progress: succeeded ? 1.0 : progress,
      track: AppColors.onLacquer.withValues(alpha: 0.14),
      fill: AppColors.keyline,
      tick: AppColors.onLacquerMuted,
      needle: AppColors.onLacquer,
      size: 208,
      center: succeeded
          ? Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const PoolSeal(size: 46),
                const SizedBox(height: AppSpacing.sm),
                Text('OBJECTIF SCELLE', style: AppTypography.eyebrow.copyWith(color: AppColors.keyline)),
              ],
            )
          : Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                FittedBox(
                  fit: BoxFit.scaleDown,
                  child: Text(
                    currentLabel,
                    style: AppTypography.figureMedium.copyWith(color: AppColors.onLacquer),
                  ),
                ),
                const SizedBox(height: 2),
                FittedBox(
                  fit: BoxFit.scaleDown,
                  child: Text(
                    'sur $targetLabel',
                    style: AppTypography.caption.copyWith(color: AppColors.onLacquerMuted),
                  ),
                ),
              ],
            ),
    );
  }
}

/// Grappe de monogrammes gravés qui se chevauchent — les participants d'une
/// Ruée en un coup d'œil (jamais de photos : uniquement l'initiale du prénom,
/// seule donnée exposée par le backend).
class ParticipantMonograms extends StatelessWidget {
  final List<String> names;
  final Color foreground;
  final Color background;
  final double size;
  final int max;

  const ParticipantMonograms({
    super.key,
    required this.names,
    required this.foreground,
    required this.background,
    this.size = 30,
    this.max = 5,
  });

  @override
  Widget build(BuildContext context) {
    if (names.isEmpty) return const SizedBox.shrink();

    final shown = names.take(max).toList(growable: false);
    final overflow = names.length - shown.length;
    final slots = shown.length + (overflow > 0 ? 1 : 0);
    final step = size * 0.68;
    final width = size + (slots - 1) * step;

    return SizedBox(
      width: width,
      height: size,
      child: Stack(
        children: [
          for (var i = 0; i < shown.length; i++)
            Positioned(
              left: i * step,
              child: Monogram(name: shown[i], size: size, foreground: foreground, background: background),
            ),
          if (overflow > 0)
            Positioned(
              left: shown.length * step,
              child: Container(
                width: size,
                height: size,
                alignment: Alignment.center,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  color: background,
                  border: Border.all(color: foreground.withValues(alpha: 0.6)),
                ),
                child: Text(
                  '+$overflow',
                  style: TextStyle(fontSize: size * 0.34, fontWeight: FontWeight.w800, color: foreground),
                ),
              ),
            ),
        ],
      ),
    );
  }
}

/// Un monogramme gravé : l'initiale d'un prénom dans un jeton cerclé.
class Monogram extends StatelessWidget {
  final String name;
  final double size;
  final Color foreground;
  final Color background;

  const Monogram({
    super.key,
    required this.name,
    required this.foreground,
    required this.background,
    this.size = 32,
  });

  String get _initial {
    final trimmed = name.trim();
    return trimmed.isEmpty ? '?' : trimmed.substring(0, 1).toUpperCase();
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      width: size,
      height: size,
      alignment: Alignment.center,
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        color: background,
        border: Border.all(color: foreground.withValues(alpha: 0.6)),
      ),
      child: Text(
        _initial,
        style: TextStyle(
          fontSize: size * 0.42,
          fontWeight: FontWeight.w800,
          letterSpacing: -0.5,
          color: foreground,
        ),
      ),
    );
  }
}

/// Sceau « Ruée scellée » — disque laqué, double filet d'or, guilloché radial,
/// coche gravée au centre. Frappé quand l'objectif est atteint.
class PoolSeal extends StatelessWidget {
  final double size;

  const PoolSeal({super.key, this.size = 48});

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: size,
      height: size,
      child: CustomPaint(size: Size.square(size), painter: _PoolSealPainter()),
    );
  }
}

class _PoolSealPainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    if (size.isEmpty) return;
    final c = size.center(Offset.zero);
    final r = size.shortestSide / 2;

    canvas.drawCircle(c, r, Paint()..color = AppColors.lacquer);
    canvas.drawCircle(
      c,
      r - 1.5,
      Paint()
        ..color = AppColors.keyline
        ..style = PaintingStyle.stroke
        ..strokeWidth = 2,
    );
    canvas.drawCircle(
      c,
      r * 0.72,
      Paint()
        ..color = AppColors.keyline.withValues(alpha: 0.5)
        ..style = PaintingStyle.stroke
        ..strokeWidth = 1,
    );

    final guilloche = Paint()
      ..color = AppColors.keyline.withValues(alpha: 0.5)
      ..strokeWidth = 0.8
      ..strokeCap = StrokeCap.round;
    for (var i = 0; i < 40; i++) {
      final a = i * math.pi / 20;
      final dir = Offset(math.cos(a), math.sin(a));
      canvas.drawLine(c + dir * (r * 0.74), c + dir * (r * 0.86), guilloche);
    }

    canvas.drawPath(
      Path()
        ..moveTo(c.dx - r * 0.28, c.dy)
        ..lineTo(c.dx - r * 0.06, c.dy + r * 0.24)
        ..lineTo(c.dx + r * 0.34, c.dy - r * 0.26),
      Paint()
        ..color = AppColors.keyline
        ..style = PaintingStyle.stroke
        ..strokeWidth = math.max(2.0, r * 0.14)
        ..strokeCap = StrokeCap.round
        ..strokeJoin = StrokeJoin.round,
    );
  }

  @override
  bool shouldRepaint(covariant _PoolSealPainter oldDelegate) => false;
}
