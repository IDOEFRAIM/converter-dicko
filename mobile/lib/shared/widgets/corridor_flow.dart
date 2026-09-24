import 'package:flutter/widgets.dart';

import '../../core/theme/app_colors.dart';
import '../../core/theme/app_motion.dart';

/// Le Corridor **vivant** : la route Burkina Faso -> Chine rendue comme un
/// fil le long duquel circule la lumiere (langage de design "Le Comptoir",
/// voir docs/MOBILE_DESIGN_LANGUAGE.md). Colonne vertebrale de la marque —
/// splash, hero d'accueil, suivi d'ordre.
///
/// Purement decoratif : aucune donnee, aucun libelle. Se pose derriere/autour
/// du contenu ([Corridor] pose les drapeaux + libelles par-dessus).
///
/// Reduction des mouvements demandee => seul le trace fixe + les deux
/// extremites sont dessines, sans boucle d'animation.
class CorridorFlow extends StatefulWidget {
  /// Couleur du trace et des extremites (typiquement la couleur de premier
  /// plan de la surface : blanc sur laque, primaire sur clair).
  final Color color;

  /// Couleur des "motes" de lumiere qui parcourent le fil.
  final Color flowColor;

  final double height;
  final int motes;

  const CorridorFlow({
    super.key,
    required this.color,
    this.flowColor = AppColors.signal,
    this.height = 64,
    this.motes = 3,
  });

  @override
  State<CorridorFlow> createState() => _CorridorFlowState();
}

class _CorridorFlowState extends State<CorridorFlow> with SingleTickerProviderStateMixin {
  late final AnimationController _c = AnimationController(vsync: this, duration: AppMotion.ambient);

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (AppMotion.reduceMotion(context)) {
      _c.stop();
      _c.value = 0;
    } else if (!_c.isAnimating) {
      _c.repeat();
    }
  }

  @override
  void dispose() {
    _c.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final animated = !AppMotion.reduceMotion(context);
    return SizedBox(
      height: widget.height,
      width: double.infinity,
      child: RepaintBoundary(
        child: CustomPaint(
          size: Size.infinite,
          painter: _CorridorFlowPainter(
            progress: _c,
            color: widget.color,
            flowColor: widget.flowColor,
            motes: widget.motes,
            animated: animated,
          ),
        ),
      ),
    );
  }
}

class _CorridorFlowPainter extends CustomPainter {
  final Animation<double> progress;
  final Color color;
  final Color flowColor;
  final int motes;
  final bool animated;

  _CorridorFlowPainter({
    required this.progress,
    required this.color,
    required this.flowColor,
    required this.motes,
    required this.animated,
  }) : super(repaint: progress);

  @override
  void paint(Canvas canvas, Size size) {
    if (size.isEmpty) return;
    final midY = size.height * 0.5;
    final amp = size.height * 0.22;
    final path = Path()
      ..moveTo(0, midY + amp * 0.15)
      ..cubicTo(size.width * 0.30, midY + amp, size.width * 0.70, midY - amp, size.width, midY - amp * 0.15);

    final metric = path.computeMetrics().first;
    final length = metric.length;

    // --- Trace de fond (le "rail") ---
    canvas.drawPath(
      path,
      Paint()
        ..color = color.withValues(alpha: 0.22)
        ..style = PaintingStyle.stroke
        ..strokeWidth = 1.5
        ..strokeCap = StrokeCap.round,
    );

    // --- Extremites : anneau depart (BF) / plein arrivee (CN) ---
    final startPos = metric.getTangentForOffset(0)?.position ?? Offset.zero;
    final endPos = metric.getTangentForOffset(length)?.position ?? Offset(size.width, midY);
    canvas.drawCircle(
      startPos,
      4,
      Paint()
        ..color = color.withValues(alpha: 0.9)
        ..style = PaintingStyle.stroke
        ..strokeWidth = 1.6,
    );
    canvas.drawCircle(endPos, 3.5, Paint()..color = color.withValues(alpha: 0.9));

    if (!animated) return;

    // --- Motes de lumiere + trainee ---
    final base = progress.value;
    final glow = Paint()..maskFilter = const MaskFilter.blur(BlurStyle.normal, 6);
    for (var i = 0; i < motes; i++) {
      final head = (base + i / motes) % 1.0;
      // Trainee : 6 points de plus en plus faibles, en arriere de la tete.
      for (var k = 6; k >= 1; k--) {
        final t = head - k * 0.018;
        if (t < 0 || t > 1) continue;
        final p = metric.getTangentForOffset(length * t)?.position;
        if (p == null) continue;
        final a = (1 - k / 7) * 0.5;
        canvas.drawCircle(p, 1.6, Paint()..color = flowColor.withValues(alpha: a));
      }
      final headPos = metric.getTangentForOffset(length * head)?.position;
      if (headPos == null) continue;
      canvas.drawCircle(headPos, 5, glow..color = flowColor.withValues(alpha: 0.45));
      canvas.drawCircle(headPos, 2.4, Paint()..color = flowColor);
    }
  }

  @override
  bool shouldRepaint(covariant _CorridorFlowPainter oldDelegate) =>
      oldDelegate.color != color ||
      oldDelegate.flowColor != flowColor ||
      oldDelegate.motes != motes ||
      oldDelegate.animated != animated;
}
