import 'dart:math' as math;

import 'package:flutter/widgets.dart';

/// Cadran d'objectif — l'instrument sobre de la console PRO (langage de design
/// "Le Comptoir", voir docs/MOBILE_DESIGN_LANGUAGE.md, Lot D). Pas de
/// fioriture : un arc gradue facon jauge d'atelier, une aiguille, un filet
/// laiton pour la portion atteinte. Distinct du `XpMeridian` gamifie : la
/// console PRO ne joue pas, elle mesure.
///
/// [progress] : 0..1 (volume du mois / objectif que l'utilisateur s'est fixe),
/// ou `null` quand aucun objectif n'est defini => l'instrument est dessine
/// vide, sans aiguille. La valeur reelle reste affichee en texte par [center]
/// (le cadran ne remplace jamais le chiffre).
class ObjectiveDial extends StatelessWidget {
  final double? progress;
  final Color track;
  final Color fill;
  final Color tick;
  final Color needle;
  final double size;
  final double thickness;
  final Widget? center;

  const ObjectiveDial({
    super.key,
    required this.progress,
    required this.track,
    required this.fill,
    required this.tick,
    required this.needle,
    this.size = 200,
    this.thickness = 5,
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
            painter: _ObjectiveDialPainter(
              progress: progress == null ? null : progress!.clamp(0.0, 1.0).toDouble(),
              track: track,
              fill: fill,
              tick: tick,
              needle: needle,
              thickness: thickness,
            ),
          ),
          if (center != null)
            Padding(
              padding: EdgeInsets.symmetric(horizontal: size * 0.2),
              child: center,
            ),
        ],
      ),
    );
  }
}

class _ObjectiveDialPainter extends CustomPainter {
  final double? progress;
  final Color track;
  final Color fill;
  final Color tick;
  final Color needle;
  final double thickness;

  // Jauge ouverte en bas : depart 150deg, balayage 240deg (fin a 30deg).
  static const _start = math.pi * 5 / 6;
  static const _sweep = math.pi * 4 / 3;

  _ObjectiveDialPainter({
    required this.progress,
    required this.track,
    required this.fill,
    required this.tick,
    required this.needle,
    required this.thickness,
  });

  @override
  void paint(Canvas canvas, Size size) {
    if (size.isEmpty) return;
    final rect = Offset.zero & size;
    final arcRect = rect.deflate(thickness + 6);
    final radius = arcRect.shortestSide / 2;
    final c = arcRect.center;

    // Rail.
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

    // Graduations : 5 majeures (0/25/50/75/100 %), 4 mineures entre chacune.
    for (var i = 0; i <= 20; i++) {
      final a = _start + _sweep * (i / 20);
      final major = i % 5 == 0;
      final outer = c + Offset(math.cos(a), math.sin(a)) * (radius + 1);
      final inner = c + Offset(math.cos(a), math.sin(a)) * (radius - (major ? 8 : 4));
      canvas.drawLine(
        inner,
        outer,
        Paint()
          ..color = tick.withValues(alpha: major ? 0.9 : 0.45)
          ..strokeWidth = major ? 1.6 : 1
          ..strokeCap = StrokeCap.round,
      );
    }

    if (progress == null) return;

    // Portion atteinte.
    if (progress! > 0) {
      canvas.drawArc(
        arcRect,
        _start,
        _sweep * progress!,
        false,
        Paint()
          ..color = fill
          ..style = PaintingStyle.stroke
          ..strokeWidth = thickness
          ..strokeCap = StrokeCap.round,
      );
    }

    // Aiguille + moyeu.
    final a = _start + _sweep * progress!;
    final dir = Offset(math.cos(a), math.sin(a));
    canvas.drawLine(
      c - dir * 10,
      c + dir * (radius - 10),
      Paint()
        ..color = needle
        ..strokeWidth = 2
        ..strokeCap = StrokeCap.round,
    );
    canvas.drawCircle(c, 4, Paint()..color = needle);
    canvas.drawCircle(
      c,
      4,
      Paint()
        ..color = track
        ..style = PaintingStyle.stroke
        ..strokeWidth = 1.4,
    );
  }

  @override
  bool shouldRepaint(covariant _ObjectiveDialPainter oldDelegate) =>
      oldDelegate.progress != progress ||
      oldDelegate.track != track ||
      oldDelegate.fill != fill ||
      oldDelegate.tick != tick ||
      oldDelegate.needle != needle ||
      oldDelegate.thickness != thickness;
}
