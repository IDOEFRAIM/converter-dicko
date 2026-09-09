import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../core/theme/app_colors.dart';
import '../../core/theme/app_motion.dart';
import '../../core/theme/app_spacing.dart';
import '../../core/theme/app_surfaces.dart';
import '../../core/theme/app_typography.dart';
import 'grain.dart';
import 'pressable.dart';

/// Zone de dépôt de la preuve de paiement (langage de design « Le Comptoir »,
/// voir docs/MOBILE_DESIGN_LANGUAGE.md, Lot H) : un **cadre de capture** avec
/// repères d'angle plutôt qu'un bouton nu. Un seul **balayage or** doux passe
/// pendant l'envoi (pas un laser stroboscopique) ; une fois reçue, la preuve
/// se présente comme une **miniature scellée** sur laque.
///
/// Purement une vue : ne choisit ni n'envoie aucun fichier — délègue à [onTap]
/// (à `null` tant qu'un envoi est en cours ou terminé).
class ProofDropzone extends StatefulWidget {
  final VoidCallback? onTap;
  final bool uploading;
  final bool done;

  /// Chemin local de l'image choisie, pour la miniature (peut être `null`).
  final String? previewPath;
  final String? fileName;

  const ProofDropzone({
    super.key,
    required this.onTap,
    required this.uploading,
    required this.done,
    this.previewPath,
    this.fileName,
  });

  @override
  State<ProofDropzone> createState() => _ProofDropzoneState();
}

class _ProofDropzoneState extends State<ProofDropzone> with SingleTickerProviderStateMixin {
  late final AnimationController _sweep =
      AnimationController(vsync: this, duration: const Duration(milliseconds: 1300));

  void _syncSweep() {
    if (widget.uploading && !AppMotion.reduceMotion(context)) {
      if (!_sweep.isAnimating) _sweep.repeat();
    } else {
      _sweep.stop();
      _sweep.value = 0;
    }
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _syncSweep();
  }

  @override
  void didUpdateWidget(ProofDropzone oldWidget) {
    super.didUpdateWidget(oldWidget);
    _syncSweep();
  }

  @override
  void dispose() {
    _sweep.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final done = widget.done;
    final uploading = widget.uploading;

    final borderColor = done || uploading ? AppColors.keyline : AppColors.paperEdge;

    final frame = Container(
      height: 156,
      width: double.infinity,
      clipBehavior: Clip.antiAlias,
      decoration: done
          ? AppSurfaces.lacquer()
          : BoxDecoration(
              color: AppColors.paper,
              borderRadius: BorderRadius.circular(AppSpacing.radiusMd),
              border: Border.all(color: borderColor, width: 1.5),
            ),
      child: Stack(
        fit: StackFit.expand,
        children: [
          if (done) const Positioned.fill(child: LedgerGrain(opacity: 0.06)),
          if (done) _doneContent() else _pendingContent(uploading: uploading),
          Positioned.fill(
            child: CustomPaint(
              size: Size.infinite,
              painter: _CornerMarksPainter(
                color: done || uploading ? AppColors.keyline : AppColors.inkFaint,
              ),
            ),
          ),
          if (uploading)
            Positioned.fill(
              child: RepaintBoundary(
                child: CustomPaint(size: Size.infinite, painter: _SweepPainter(progress: _sweep)),
              ),
            ),
        ],
      ),
    );

    if (widget.onTap == null) return frame;
    return Pressable(
      onTap: () {
        HapticFeedback.selectionClick();
        widget.onTap!();
      },
      child: frame,
    );
  }

  Widget _pendingContent({required bool uploading}) {
    return Padding(
      padding: const EdgeInsets.all(AppSpacing.lg),
      child: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              uploading ? Icons.hourglass_top_rounded : Icons.document_scanner_outlined,
              size: 30,
              color: uploading ? AppColors.keyline : AppColors.inkMuted,
            ),
            const SizedBox(height: AppSpacing.sm),
            Text(
              uploading ? 'Analyse de la preuve...' : 'Deposer la preuve de paiement',
              style: AppTypography.bodyStrong,
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 2),
            Text(
              uploading ? 'Ne fermez pas cet ecran' : 'Photo ou capture du recu Mobile Money',
              style: AppTypography.caption,
              textAlign: TextAlign.center,
            ),
          ],
        ),
      ),
    );
  }

  Widget _doneContent() {
    return Padding(
      padding: const EdgeInsets.all(AppSpacing.md),
      child: Row(
        children: [
          Container(
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(AppSpacing.radiusSm),
              border: Border.all(color: AppColors.keyline.withValues(alpha: 0.5)),
            ),
            clipBehavior: Clip.antiAlias,
            child: SizedBox(
              width: 90,
              height: 118,
              child: widget.previewPath != null
                  ? Image.file(
                      File(widget.previewPath!),
                      fit: BoxFit.cover,
                      // Parc Android milieu de gamme : on decode la vignette
                      // reduite plutot que la photo plein format.
                      cacheWidth: 300,
                      errorBuilder: (_, _, _) => _thumbFallback(),
                    )
                  : _thumbFallback(),
            ),
          ),
          const SizedBox(width: AppSpacing.md),
          Expanded(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    const Icon(Icons.verified_rounded, size: 16, color: AppColors.keyline),
                    const SizedBox(width: AppSpacing.xs),
                    Text('PREUVE SCELLEE', style: AppTypography.eyebrow.copyWith(color: AppColors.keyline)),
                  ],
                ),
                const SizedBox(height: AppSpacing.xs),
                Text(
                  widget.fileName ?? 'Recu transmis',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: AppTypography.figureSmall.copyWith(color: AppColors.onLacquer),
                ),
                const SizedBox(height: 2),
                Text(
                  'Recue par notre equipe pour verification',
                  style: AppTypography.caption.copyWith(color: AppColors.onLacquerMuted),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _thumbFallback() {
    return const ColoredBox(
      color: AppColors.lacquerEdge,
      child: Center(child: Icon(Icons.receipt_long_rounded, color: AppColors.onLacquerMuted, size: 26)),
    );
  }
}

class _CornerMarksPainter extends CustomPainter {
  final Color color;

  _CornerMarksPainter({required this.color});

  @override
  void paint(Canvas canvas, Size size) {
    if (size.isEmpty) return;
    const inset = 7.0;
    const len = 14.0;
    final w = size.width;
    final h = size.height;
    final p = Paint()
      ..color = color
      ..strokeWidth = 2
      ..strokeCap = StrokeCap.round
      ..strokeJoin = StrokeJoin.round
      ..style = PaintingStyle.stroke;

    canvas.drawPath(
      Path()
        ..moveTo(inset, inset + len)
        ..lineTo(inset, inset)
        ..lineTo(inset + len, inset),
      p,
    );
    canvas.drawPath(
      Path()
        ..moveTo(w - inset - len, inset)
        ..lineTo(w - inset, inset)
        ..lineTo(w - inset, inset + len),
      p,
    );
    canvas.drawPath(
      Path()
        ..moveTo(inset, h - inset - len)
        ..lineTo(inset, h - inset)
        ..lineTo(inset + len, h - inset),
      p,
    );
    canvas.drawPath(
      Path()
        ..moveTo(w - inset - len, h - inset)
        ..lineTo(w - inset, h - inset)
        ..lineTo(w - inset, h - inset - len),
      p,
    );
  }

  @override
  bool shouldRepaint(covariant _CornerMarksPainter oldDelegate) => oldDelegate.color != color;
}

class _SweepPainter extends CustomPainter {
  final Animation<double> progress;

  _SweepPainter({required this.progress}) : super(repaint: progress);

  @override
  void paint(Canvas canvas, Size size) {
    if (size.isEmpty) return;
    const band = 64.0;
    final x = progress.value * (size.width + band) - band;
    final rect = Rect.fromLTWH(x, 0, band, size.height);
    final shader = LinearGradient(
      colors: [
        AppColors.keyline.withValues(alpha: 0),
        AppColors.keyline.withValues(alpha: 0.4),
        AppColors.keyline.withValues(alpha: 0),
      ],
    ).createShader(rect);
    canvas.drawRect(rect, Paint()..shader = shader);
  }

  @override
  bool shouldRepaint(covariant _SweepPainter oldDelegate) => false;
}
