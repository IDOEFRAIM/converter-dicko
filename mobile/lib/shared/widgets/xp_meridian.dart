import 'dart:math' as math;

import 'package:flutter/widgets.dart';

import '../../core/theme/app_colors.dart';

/// Jauge en arc — la progression dans le rang courant, tracee comme un
/// meridien (echo de la courbe du corridor). Un noeud lumineux marque la
/// pointe atteinte (langage de design "Le Comptoir").
///
/// [progress] : 0..1, ou `null` (aucun palier applicable) => seul le rail
/// vide est dessine. Le contenu central ([center]) affiche la valeur reelle
/// en texte (XP, "reste N transferts") — la jauge ne remplace jamais le
/// chiffre.
class XpMeridian extends StatelessWidget {
  final double? progress;
  final Color track;
  final Color fill;
  final Color node;
  final double size;
  final double thickness;
  final Widget? center;

  const XpMeridian({
    super.key,
    required this.progress,
    required this.track,
    required this.fill,
    this.node = AppColors.signal,
    this.size = 176,
    this.thickness = 6,
    this.center,
  });

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: size,
      height: size,
      child: Stack(
        alignment: Alignment.center,
        children: [
          CustomPaint(
            size: Size.square(size),
            painter: _MeridianPainter(
              progress: progress == null ? null : progress!.clamp(0.0, 1.0).toDouble(),
              track: track,
              fill: fill,
              node: node,
              thickness: thickness,
            ),
          ),
          if (center != null)
            Padding(
              padding: EdgeInsets.symmetric(horizontal: size * 0.16),
              child: center,
            ),
        ],
      ),
    );
  }
}

class _MeridianPainter extends CustomPainter {
  final double? progress;
  final Color track;
  final Color fill;
  final Color node;
  final double thickness;

  // Jauge ouverte en bas : depart 135deg, balayage 270deg.
  static const _start = math.pi * 0.75;
  static const _sweep = math.pi * 1.5;

  _MeridianPainter({
    required this.progress,
    required this.track,
    required this.fill,
    required this.node,
    required this.thickness,
  });

  @override
  void paint(Canvas canvas, Size size) {
    if (size.isEmpty) return;
    final rect = Offset.zero & size;
    final arcRect = rect.deflate(thickness);
    final radius = arcRect.shortestSide / 2;
    final center = arcRect.center;

    canvas.drawArc(
      arcRect,
      _start,
      _sweep,
      false,
      Paint()
        ..color = track
        ..style = PaintingStyle.stroke
        ..strokeWidth = thickness
        ..strokeCap = StrokeCap.round,
    );

    if (progress == null) return;

    final swept = _sweep * progress!;
    if (progress! > 0) {
      canvas.drawArc(
        arcRect,
        _start,
        swept,
        false,
        Paint()
          ..color = fill
          ..style = PaintingStyle.stroke
          ..strokeWidth = thickness
          ..strokeCap = StrokeCap.round,
      );
    }

    // Noeud a la pointe.
    final a = _start + swept;
    final tip = center + Offset(math.cos(a), math.sin(a)) * radius;
    canvas.drawCircle(tip, thickness * 1.7, Paint()..color = node.withValues(alpha: 0.25)..maskFilter = const MaskFilter.blur(BlurStyle.normal, 5));
    canvas.drawCircle(tip, thickness * 0.85, Paint()..color = node);
    canvas.drawCircle(tip, thickness * 0.85, Paint()..color = AppColors.onLacquer..style = PaintingStyle.stroke..strokeWidth = 1.5);
  }

  @override
  bool shouldRepaint(covariant _MeridianPainter oldDelegate) =>
      oldDelegate.progress != progress ||
      oldDelegate.track != track ||
      oldDelegate.fill != fill ||
      oldDelegate.node != node ||
      oldDelegate.thickness != thickness;
}
