import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../../core/theme/app_colors.dart';
import '../../core/theme/app_motion.dart';
import '../../core/theme/app_spacing.dart';
import '../../core/theme/app_surfaces.dart';
import '../../core/theme/app_typography.dart';
import 'grain.dart';

/// Tonalite d'une station du suivi — derivee cote appelant a partir du seul
/// `code` de l'evenement (jamais une machine d'etat independante).
enum StationTone { done, current, negative, refund }

/// Une etape du transfert, prete a afficher (libelle deja traduit, horodatage
/// deja formate). [CorridorTimeline] ne connait rien du modele metier.
class CorridorStation {
  final String label;
  final String timestamp;
  final StationTone tone;

  const CorridorStation({required this.label, required this.timestamp, required this.tone});
}

/// Suivi d'un transfert rendu *le long du corridor* (langage de design
/// "Le Comptoir", voir docs/MOBILE_DESIGN_LANGUAGE.md, Lots C & G) : la route
/// 🇧🇫 -> 🇨🇳 tracee sur une surface laque, chaque evenement pose comme une
/// jauge circulaire le long de la courbe, la portion parcourue en filet d'or,
/// et — tant que l'ordre est en cours — une **lumiere qui avance en temps
/// reel** le long du corridor jusqu'a l'etape courante (facon suivi de vol).
/// Sous la carte, la meme liste en clair (libelles + horodatages en figures
/// tabulaires) pour la lisibilite et l'accessibilite.
///
/// Purement une vue : aucune logique d'etat, aucune donnee inventee. Toute
/// l'animation se fige si l'utilisateur a demande la reduction des mouvements.
class CorridorTimeline extends StatefulWidget {
  final List<CorridorStation> stations;

  /// L'ordre a-t-il atteint la Chine (statut `COMPLETED`) : le filet d'or va
  /// alors jusqu'au bout de la courbe.
  final bool reachedDestination;

  const CorridorTimeline({
    super.key,
    required this.stations,
    required this.reachedDestination,
  });

  @override
  State<CorridorTimeline> createState() => _CorridorTimelineState();
}

class _CorridorTimelineState extends State<CorridorTimeline> with TickerProviderStateMixin {
  // Lumiere qui parcourt le corridor (boucle continue).
  late final AnimationController _flow =
      AnimationController(vsync: this, duration: const Duration(milliseconds: 3800));

  // Halo qui bat sur la station courante.
  late final AnimationController _pulse =
      AnimationController(vsync: this, duration: const Duration(milliseconds: 1700));

  bool get _hasCurrent => widget.stations.any((s) => s.tone == StationTone.current);

  void _syncAnimations() {
    final run = _hasCurrent && !AppMotion.reduceMotion(context);
    for (final c in [_flow, _pulse]) {
      if (run) {
        if (!c.isAnimating) c.repeat();
      } else {
        c.stop();
        c.value = 0;
      }
    }
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _syncAnimations();
  }

  @override
  void didUpdateWidget(CorridorTimeline oldWidget) {
    super.didUpdateWidget(oldWidget);
    // Un rafraichissement a pu faire passer l'ordre a un etat terminal :
    // plus de station "courante" => on coupe les boucles.
    _syncAnimations();
  }

  @override
  void dispose() {
    _flow.dispose();
    _pulse.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final animated = _hasCurrent && !AppMotion.reduceMotion(context);
    return ClipRRect(
      borderRadius: BorderRadius.circular(AppSpacing.radiusMd),
      child: Container(
        width: double.infinity,
        decoration: AppSurfaces.lacquer(),
        child: Stack(
          children: [
            const Positioned.fill(child: LedgerGrain(opacity: 0.05)),
            Padding(
              padding: const EdgeInsets.all(AppSpacing.lg),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('LE CORRIDOR', style: AppTypography.eyebrow.copyWith(color: AppColors.keyline)),
                  const SizedBox(height: AppSpacing.md),
                  SizedBox(
                    height: 118,
                    width: double.infinity,
                    child: RepaintBoundary(
                      child: CustomPaint(
                        size: Size.infinite,
                        painter: _CorridorMapPainter(
                          stations: widget.stations,
                          reachedDestination: widget.reachedDestination,
                          flow: _flow,
                          pulse: _pulse,
                          animated: animated,
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(height: AppSpacing.md),
                  Divider(color: AppColors.onLacquer.withValues(alpha: 0.12), height: 1),
                  const SizedBox(height: AppSpacing.md),
                  for (var i = 0; i < widget.stations.length; i++)
                    _StationTile(
                      station: widget.stations[i],
                      next: i + 1 < widget.stations.length ? widget.stations[i + 1] : null,
                      isLast: i == widget.stations.length - 1,
                    ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _StationTile extends StatelessWidget {
  final CorridorStation station;
  final CorridorStation? next;
  final bool isLast;

  const _StationTile({required this.station, required this.next, required this.isLast});

  @override
  Widget build(BuildContext context) {
    final tone = station.tone;

    final Color dot;
    final IconData icon;
    final bool solid;
    switch (tone) {
      case StationTone.done:
        dot = AppColors.keyline;
        icon = Icons.check;
        solid = false;
      case StationTone.current:
        dot = AppColors.signal;
        icon = Icons.sync;
        solid = true;
      case StationTone.negative:
        dot = AppColors.chinaRed;
        icon = Icons.priority_high;
        solid = true;
      case StationTone.refund:
        dot = AppColors.onLacquerMuted;
        icon = Icons.undo;
        solid = false;
    }

    final connector = next?.tone == StationTone.negative
        ? AppColors.chinaRed.withValues(alpha: 0.4)
        : AppColors.keyline.withValues(alpha: 0.45);

    return IntrinsicHeight(
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Column(
            children: [
              Container(
                width: 26,
                height: 26,
                decoration: BoxDecoration(
                  color: solid ? dot : dot.withValues(alpha: 0.16),
                  shape: BoxShape.circle,
                  border: Border.all(color: dot.withValues(alpha: solid ? 0.0 : 0.8)),
                  boxShadow: tone == StationTone.current
                      ? const [BoxShadow(color: AppColors.signalGlow, blurRadius: 10, spreadRadius: 1)]
                      : null,
                ),
                child: Icon(icon, size: 14, color: solid ? Colors.white : dot),
              ),
              if (!isLast) Expanded(child: Container(width: 2, color: connector)),
            ],
          ),
          const SizedBox(width: AppSpacing.md),
          Expanded(
            child: Padding(
              padding: EdgeInsets.only(bottom: isLast ? 0 : AppSpacing.lg),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  if (tone == StationTone.refund)
                    Text('REMBOURSEMENT',
                        style: AppTypography.eyebrow.copyWith(color: AppColors.onLacquerMuted)),
                  Text(
                    station.label,
                    style: AppTypography.bodyStrong.copyWith(
                      color: tone == StationTone.current ? AppColors.keyline : AppColors.onLacquer,
                    ),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    station.timestamp,
                    style: AppTypography.figureSmall.copyWith(color: AppColors.onLacquerMuted),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _CorridorMapPainter extends CustomPainter {
  final List<CorridorStation> stations;
  final bool reachedDestination;
  final Animation<double> flow;
  final Animation<double> pulse;
  final bool animated;

  _CorridorMapPainter({
    required this.stations,
    required this.reachedDestination,
    required this.flow,
    required this.pulse,
    required this.animated,
  }) : super(repaint: Listenable.merge([flow, pulse]));

  /// Position fractionnaire d'une station le long de la courbe — legerement
  /// rentree des deux bouts pour laisser les drapeaux respirer.
  double _tOf(int i, int n) => n <= 1 ? 0.5 : 0.08 + 0.84 * (i / (n - 1));

  @override
  void paint(Canvas canvas, Size size) {
    if (size.isEmpty || stations.isEmpty) return;

    final midY = size.height * 0.56;
    final amp = size.height * 0.22;
    final path = Path()
      ..moveTo(0, midY + amp * 0.15)
      ..cubicTo(size.width * 0.30, midY + amp, size.width * 0.70, midY - amp, size.width, midY - amp * 0.15);
    final metric = path.computeMetrics().first;
    final length = metric.length;

    final n = stations.length;
    final hasNegative = stations.any((s) => s.tone == StationTone.negative);
    final headT = reachedDestination ? 1.0 : _tOf(n - 1, n);
    final trackColor = hasNegative ? AppColors.chinaRed : AppColors.keyline;

    // --- Ombre portee de l'arc : un leger relief 3D sous le corridor. ---
    canvas.save();
    canvas.translate(0, 2.5);
    canvas.drawPath(
      path,
      Paint()
        ..color = AppColors.navyDark.withValues(alpha: 0.4)
        ..style = PaintingStyle.stroke
        ..strokeWidth = 3
        ..maskFilter = const MaskFilter.blur(BlurStyle.normal, 3),
    );
    canvas.restore();

    // --- Rail complet + graduations « radar ». ---
    canvas.drawPath(
      path,
      Paint()
        ..color = AppColors.onLacquerMuted.withValues(alpha: 0.26)
        ..style = PaintingStyle.stroke
        ..strokeWidth = 1.5
        ..strokeCap = StrokeCap.round,
    );
    final tick = Paint()
      ..color = AppColors.onLacquerMuted.withValues(alpha: 0.14)
      ..strokeWidth = 1
      ..strokeCap = StrokeCap.round;
    for (final f in const [0.12, 0.28, 0.44, 0.6, 0.76, 0.9]) {
      final t = metric.getTangentForOffset(length * f);
      if (t == null) continue;
      final perp = Offset(-t.vector.dy, t.vector.dx);
      canvas.drawLine(t.position - perp * 3, t.position + perp * 3, tick);
    }

    // --- Portion parcourue, en filet d'or (ou rouge si l'ordre a echoue). ---
    canvas.drawPath(
      metric.extractPath(0, length * headT),
      Paint()
        ..color = trackColor
        ..style = PaintingStyle.stroke
        ..strokeWidth = 2.4
        ..strokeCap = StrokeCap.round,
    );

    final startPos = metric.getTangentForOffset(0)?.position ?? Offset.zero;
    final endPos = metric.getTangentForOffset(length)?.position ?? Offset(size.width, midY);

    _flag(canvas, '\u{1F1E7}\u{1F1EB}', Offset(startPos.dx, startPos.dy - 30));
    _flag(canvas, '\u{1F1E8}\u{1F1F3}', Offset(endPos.dx - 18, endPos.dy - 30));

    canvas.drawCircle(
      startPos,
      3.4,
      Paint()
        ..color = AppColors.keyline.withValues(alpha: 0.7)
        ..style = PaintingStyle.stroke
        ..strokeWidth = 1.5,
    );
    canvas.drawCircle(
      endPos,
      3.6,
      Paint()..color = AppColors.keyline.withValues(alpha: reachedDestination ? 1 : 0.35),
    );

    // --- Lumiere qui avance en temps reel sur la portion parcourue. ---
    if (animated && headT > 0) {
      final head = flow.value * headT;
      for (var k = 12; k >= 1; k--) {
        final t = head - k * 0.010;
        if (t <= 0) continue;
        final p = metric.getTangentForOffset(length * t)?.position;
        if (p == null) continue;
        canvas.drawCircle(p, 1.7, Paint()..color = AppColors.signal.withValues(alpha: (1 - k / 13) * 0.6));
      }
      final hp = metric.getTangentForOffset(length * head)?.position;
      if (hp != null) {
        canvas.drawCircle(
          hp,
          6,
          Paint()
            ..color = AppColors.signal.withValues(alpha: 0.5)
            ..maskFilter = const MaskFilter.blur(BlurStyle.normal, 6),
        );
        canvas.drawCircle(hp, 2.6, Paint()..color = AppColors.signal);
        canvas.drawCircle(
          hp,
          2.6,
          Paint()
            ..color = AppColors.onLacquer
            ..style = PaintingStyle.stroke
            ..strokeWidth = 1,
        );
      }
    }

    // --- Stations : jauges circulaires. ---
    for (var i = 0; i < n; i++) {
      final pos = metric.getTangentForOffset(length * _tOf(i, n))?.position;
      if (pos == null) continue;
      _gauge(canvas, pos, stations[i].tone, animated && stations[i].tone == StationTone.current ? pulse.value : null);
    }
  }

  void _gauge(Canvas canvas, Offset c, StationTone tone, double? pulseV) {
    switch (tone) {
      case StationTone.done:
        canvas.drawCircle(
          c,
          4.8,
          Paint()
            ..color = AppColors.keyline
            ..style = PaintingStyle.stroke
            ..strokeWidth = 2,
        );
        canvas.drawCircle(c, 1.6, Paint()..color = AppColors.keyline);
      case StationTone.current:
        if (pulseV != null) {
          canvas.drawCircle(
            c,
            5 + 9 * pulseV,
            Paint()..color = AppColors.signal.withValues(alpha: 0.4 * (1 - pulseV)),
          );
        }
        canvas.drawCircle(
          c,
          7,
          Paint()
            ..color = AppColors.signal.withValues(alpha: 0.28)
            ..maskFilter = const MaskFilter.blur(BlurStyle.normal, 5),
        );
        canvas.drawCircle(
          c,
          5,
          Paint()
            ..color = AppColors.signal
            ..style = PaintingStyle.stroke
            ..strokeWidth = 2,
        );
        canvas.drawCircle(c, 2, Paint()..color = AppColors.signal);
        canvas.drawCircle(
          c,
          5,
          Paint()
            ..color = AppColors.onLacquer
            ..style = PaintingStyle.stroke
            ..strokeWidth = 1,
        );
      case StationTone.negative:
        canvas.drawCircle(
          c,
          4.8,
          Paint()
            ..color = AppColors.chinaRed
            ..style = PaintingStyle.stroke
            ..strokeWidth = 2,
        );
        final x = Paint()
          ..color = AppColors.chinaRed
          ..strokeWidth = 1.6
          ..strokeCap = StrokeCap.round;
        canvas.drawLine(c + const Offset(-2.4, -2.4), c + const Offset(2.4, 2.4), x);
        canvas.drawLine(c + const Offset(-2.4, 2.4), c + const Offset(2.4, -2.4), x);
      case StationTone.refund:
        final rect = Rect.fromCircle(center: c, radius: 4.8);
        final rp = Paint()
          ..color = AppColors.onLacquerMuted
          ..style = PaintingStyle.stroke
          ..strokeWidth = 1.4
          ..strokeCap = StrokeCap.round;
        for (var s = 0; s < 8; s++) {
          canvas.drawArc(rect, s * math.pi / 4 + 0.16, math.pi / 4 - 0.32, false, rp);
        }
    }
  }

  void _flag(Canvas canvas, String flag, Offset at) {
    final tp = TextPainter(
      text: TextSpan(text: flag, style: const TextStyle(fontSize: 15)),
      textDirection: TextDirection.ltr,
    )..layout();
    tp.paint(canvas, at);
  }

  @override
  bool shouldRepaint(covariant _CorridorMapPainter oldDelegate) =>
      oldDelegate.reachedDestination != reachedDestination ||
      oldDelegate.animated != animated ||
      oldDelegate.stations.length != stations.length;
}
