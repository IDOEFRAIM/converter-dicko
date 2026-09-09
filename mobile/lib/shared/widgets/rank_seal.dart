import 'dart:math' as math;

import 'package:flutter/widgets.dart';

import '../../core/theme/app_colors.dart';
import '../../core/theme/app_motion.dart';
import '../../shared/models/current_user.dart';

/// Sceau de rang grave (langage de design "Le Comptoir", voir
/// docs/MOBILE_DESIGN_LANGUAGE.md) : un medaillon `CustomPainter` — anneaux
/// graves, motif propre au profil, initiale du rang au centre, filet d'or.
/// Jamais un autocollant colore.
///
/// - PRO : guilloche sobre (le serieux EST sa gamification).
/// - STUDENT_MALE ("Epopee") : etoile a huit branches.
/// - STUDENT_FEMALE ("Histoire") : rayons fins + laurier.
/// - [locked] : grave en creux, desature, tirets — palier pas encore atteint.
class RankSeal extends StatelessWidget {
  final ExperienceProfile profile;

  /// Lettre au centre (typiquement l'initiale du rang). Ignoree si [locked].
  final String? initial;
  final double size;
  final bool locked;

  const RankSeal({
    super.key,
    required this.profile,
    this.initial,
    this.size = 96,
    this.locked = false,
  });

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: size,
      height: size,
      child: CustomPaint(
        painter: _RankSealPainter(profile: profile, initial: locked ? null : initial, locked: locked),
      ),
    );
  }
}

/// [RankSeal] qui "se grave" a l'arrivee : leger sur-dimensionnement + rotation
/// qui se resorbe (courbe [AppMotion.emphatic]). Se fige si l'utilisateur a
/// demande la reduction des mouvements.
class RankSealBadge extends StatefulWidget {
  final ExperienceProfile profile;
  final String? initial;
  final double size;
  final bool locked;

  const RankSealBadge({
    super.key,
    required this.profile,
    this.initial,
    this.size = 120,
    this.locked = false,
  });

  @override
  State<RankSealBadge> createState() => _RankSealBadgeState();
}

class _RankSealBadgeState extends State<RankSealBadge> with SingleTickerProviderStateMixin {
  late final AnimationController _c = AnimationController(vsync: this, duration: AppMotion.slow);

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      if (AppMotion.reduceMotion(context)) {
        _c.value = 1;
      } else {
        _c.forward();
      }
    });
  }

  @override
  void dispose() {
    _c.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final curved = CurvedAnimation(parent: _c, curve: AppMotion.emphatic);
    return AnimatedBuilder(
      animation: curved,
      builder: (context, child) {
        final t = curved.value;
        return Opacity(
          // Le controleur est borne 0..1 : pas de clamp necessaire (contrairement
          // a `curved`, dont la courbe emphatique depasse volontairement 1).
          opacity: _c.value,
          child: Transform.rotate(
            angle: (1 - t) * -0.14,
            child: Transform.scale(scale: 0.82 + 0.18 * t, child: child),
          ),
        );
      },
      child: RankSeal(profile: widget.profile, initial: widget.initial, size: widget.size, locked: widget.locked),
    );
  }
}

class _RankSealPainter extends CustomPainter {
  final ExperienceProfile profile;
  final String? initial;
  final bool locked;

  _RankSealPainter({required this.profile, required this.initial, required this.locked});

  @override
  void paint(Canvas canvas, Size size) {
    if (size.isEmpty) return;
    final center = size.center(Offset.zero);
    final r = size.shortestSide / 2;
    final gold = locked ? AppColors.keyline.withValues(alpha: 0.35) : AppColors.keyline;

    // Disque de fond (lit sur toute surface, clair comme sombre).
    canvas.drawCircle(center, r, Paint()..color = AppColors.lacquer.withValues(alpha: locked ? 0.55 : 1));

    // Anneau exterieur.
    canvas.drawCircle(
      center,
      r - 1.5,
      Paint()
        ..color = gold
        ..style = PaintingStyle.stroke
        ..strokeWidth = 2.2,
    );
    // Anneau grave interne.
    canvas.drawCircle(
      center,
      r * 0.74,
      Paint()
        ..color = gold.withValues(alpha: (locked ? 0.2 : 0.5))
        ..style = PaintingStyle.stroke
        ..strokeWidth = 1,
    );

    _paintMotif(canvas, center, r, gold);

    // Pastille centrale + initiale.
    canvas.drawCircle(center, r * 0.5, Paint()..color = AppColors.lacquerEdge.withValues(alpha: locked ? 0.6 : 1));
    canvas.drawCircle(
      center,
      r * 0.5,
      Paint()
        ..color = gold.withValues(alpha: 0.6)
        ..style = PaintingStyle.stroke
        ..strokeWidth = 1,
    );

    if (locked) {
      _paintLock(canvas, center, r, gold);
      return;
    }

    final glyph = (initial == null || initial!.isEmpty) ? '' : initial!.substring(0, 1).toUpperCase();
    if (glyph.isEmpty) return;
    final tp = TextPainter(
      text: TextSpan(
        text: glyph,
        style: TextStyle(
          color: AppColors.onLacquer,
          fontSize: r * 0.62,
          fontWeight: FontWeight.w800,
          letterSpacing: -1,
        ),
      ),
      textDirection: TextDirection.ltr,
    )..layout();
    tp.paint(canvas, center - Offset(tp.width / 2, tp.height / 2));
  }

  void _paintMotif(Canvas canvas, Offset c, double r, Color gold) {
    final stroke = Paint()
      ..color = gold.withValues(alpha: locked ? 0.18 : 0.55)
      ..style = PaintingStyle.stroke
      ..strokeCap = StrokeCap.round;

    switch (profile) {
      case ExperienceProfile.studentMale:
        // Etoile a huit branches.
        final fill = Paint()..color = gold.withValues(alpha: locked ? 0.14 : 0.42);
        for (var i = 0; i < 8; i++) {
          final a = i * math.pi / 4;
          final tip = c + Offset(math.cos(a), math.sin(a)) * (r * 0.68);
          final bl = c + Offset(math.cos(a + 0.18), math.sin(a + 0.18)) * (r * 0.5);
          final br = c + Offset(math.cos(a - 0.18), math.sin(a - 0.18)) * (r * 0.5);
          canvas.drawPath(Path()..moveTo(bl.dx, bl.dy)..lineTo(tip.dx, tip.dy)..lineTo(br.dx, br.dy)..close(), fill);
        }
      case ExperienceProfile.studentFemale:
        // Rayons fins.
        stroke.strokeWidth = 0.8;
        for (var i = 0; i < 36; i++) {
          final a = i * math.pi / 18;
          final p1 = c + Offset(math.cos(a), math.sin(a)) * (r * 0.55);
          final p2 = c + Offset(math.cos(a), math.sin(a)) * (r * 0.7);
          canvas.drawLine(p1, p2, stroke);
        }
        // Deux arcs "laurier" en bas.
        final laurel = Paint()
          ..color = gold.withValues(alpha: locked ? 0.2 : 0.6)
          ..style = PaintingStyle.stroke
          ..strokeWidth = 1.6
          ..strokeCap = StrokeCap.round;
        final box = Rect.fromCircle(center: c, radius: r * 0.72);
        canvas.drawArc(box, math.pi * 0.62, math.pi * 0.26, false, laurel);
        canvas.drawArc(box, math.pi * 0.12, math.pi * 0.26, false, laurel);
      case ExperienceProfile.pro:
        // Guilloche sobre : couronne de petits ticks radiaux.
        stroke.strokeWidth = 1;
        for (var i = 0; i < 48; i++) {
          final a = i * math.pi / 24;
          final p1 = c + Offset(math.cos(a), math.sin(a)) * (r * 0.6);
          final p2 = c + Offset(math.cos(a), math.sin(a)) * (r * 0.68);
          canvas.drawLine(p1, p2, stroke);
        }
    }
  }

  void _paintLock(Canvas canvas, Offset c, double r, Color gold) {
    final w = r * 0.34;
    final h = r * 0.28;
    final body = Rect.fromCenter(center: c + Offset(0, h * 0.25), width: w, height: h);
    final p = Paint()
      ..color = gold.withValues(alpha: 0.7)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 2;
    canvas.drawRRect(RRect.fromRectAndRadius(body, const Radius.circular(2)), p);
    canvas.drawArc(
      Rect.fromCircle(center: c - Offset(0, h * 0.35), radius: w * 0.34),
      math.pi,
      math.pi,
      false,
      p,
    );
  }

  @override
  bool shouldRepaint(covariant _RankSealPainter oldDelegate) =>
      oldDelegate.profile != profile || oldDelegate.initial != initial || oldDelegate.locked != locked;
}
