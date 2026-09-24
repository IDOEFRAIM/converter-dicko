import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../core/theme/app_colors.dart';
import '../../core/theme/app_motion.dart';
import '../../core/theme/app_spacing.dart';
import '../../core/theme/app_surfaces.dart';
import '../../core/theme/app_typography.dart';
import '../models/money.dart';
import 'pressable.dart';

/// Une ligne detachable du ticket : libelle + valeur, eventuellement copiable
/// d'un tap (reference, taux, frais).
class TicketRow {
  final String label;
  final String value;
  final bool copyable;

  const TicketRow(this.label, this.value, {this.copyable = false});
}

/// Le « ticket » d'un transfert (langage de design « Le Comptoir », voir
/// docs/MOBILE_DESIGN_LANGUAGE.md, Lot F) : la forme monetaire d'une operation
/// presentee comme une piece physique — surface papier, perforation + ligne de
/// dechire, montants en figures tabulaires, micro-interaction de copie sur les
/// valeurs utiles. Remplace l'empilement generique « VOUS ENVOYEZ / fleche /
/// LE BENEFICIAIRE RECOIT ».
///
/// Purement une vue : formate des donnees deja calculees, ne recalcule aucun
/// montant (les `Money` recus sont affiches tels quels).
class TransferTicket extends StatelessWidget {
  final Money sendAmount;
  final Money receiveAmount;
  final String receiveLabel;

  /// Ajoute « ≈ » devant le montant recu (devis : le CNY est une estimation ;
  /// ordre cree : montant ferme => `false`).
  final bool receiveApprox;

  final List<TicketRow> rows;

  /// Numero de piece, rendu comme le talon d'un ticket (copiable).
  final String? serial;

  /// Mention sous le ticket (validite d'un devis, etc.).
  final Widget? footnote;

  const TransferTicket({
    super.key,
    required this.sendAmount,
    required this.receiveAmount,
    this.receiveLabel = 'LE BENEFICIAIRE RECOIT',
    this.receiveApprox = false,
    this.rows = const [],
    this.serial,
    this.footnote,
  });

  @override
  Widget build(BuildContext context) {
    final accent = Theme.of(context).colorScheme.primary;
    final notch = Theme.of(context).scaffoldBackgroundColor;

    final allRows = <TicketRow>[
      ...rows,
      if (serial != null) TicketRow('Reference', serial!, copyable: true),
    ];

    return Container(
      width: double.infinity,
      decoration: AppSurfaces.paper(),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(AppSpacing.lg, AppSpacing.lg, AppSpacing.lg, AppSpacing.md),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                _stub('VOUS ENVOYEZ', sendAmount, AppColors.ink),
                const SizedBox(height: AppSpacing.sm),
                const Icon(Icons.arrow_downward, size: 16, color: AppColors.inkFaint),
                const SizedBox(height: AppSpacing.sm),
                _stub(receiveLabel, receiveAmount, accent, approx: receiveApprox),
              ],
            ),
          ),
          _Perforation(hole: notch),
          if (allRows.isNotEmpty)
            Padding(
              padding: const EdgeInsets.fromLTRB(AppSpacing.lg, AppSpacing.md, AppSpacing.lg, AppSpacing.md),
              child: Column(
                children: [
                  for (var i = 0; i < allRows.length; i++) ...[
                    if (i > 0) const SizedBox(height: AppSpacing.sm),
                    _TicketRowTile(row: allRows[i]),
                  ],
                ],
              ),
            ),
          if (footnote != null)
            Padding(
              padding: const EdgeInsets.fromLTRB(AppSpacing.lg, 0, AppSpacing.lg, AppSpacing.md),
              child: DefaultTextStyle.merge(style: AppTypography.caption, child: footnote!),
            ),
        ],
      ),
    );
  }

  Widget _stub(String label, Money amount, Color color, {bool approx = false}) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label, style: AppTypography.eyebrow),
        const SizedBox(height: AppSpacing.xs),
        FittedBox(
          fit: BoxFit.scaleDown,
          alignment: Alignment.centerLeft,
          child: Text(
            '${approx ? '≈ ' : ''}${amount.formattedWithCurrency()}',
            style: AppTypography.figureLarge.copyWith(color: color),
          ),
        ),
      ],
    );
  }
}

class _Perforation extends StatelessWidget {
  final Color hole;

  const _Perforation({required this.hole});

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 22,
      width: double.infinity,
      child: CustomPaint(size: Size.infinite, painter: _PerforationPainter(hole: hole)),
    );
  }
}

class _PerforationPainter extends CustomPainter {
  final Color hole;

  _PerforationPainter({required this.hole});

  @override
  void paint(Canvas canvas, Size size) {
    if (size.isEmpty) return;
    final cy = size.height / 2;

    final dash = Paint()
      ..color = AppColors.keyline.withValues(alpha: 0.5)
      ..strokeWidth = 1
      ..strokeCap = StrokeCap.round;
    for (var x = 16.0; x < size.width - 16; x += 9) {
      canvas.drawLine(Offset(x, cy), Offset(x + 5, cy), dash);
    }

    const r = 11.0;
    final holePaint = Paint()..color = hole;
    final ring = Paint()
      ..color = AppColors.paperEdge
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1;
    for (final cx in <double>[0, size.width]) {
      canvas.drawCircle(Offset(cx, cy), r, holePaint);
      canvas.drawCircle(Offset(cx, cy), r, ring);
    }
  }

  @override
  bool shouldRepaint(covariant _PerforationPainter oldDelegate) => oldDelegate.hole != hole;
}

class _TicketRowTile extends StatefulWidget {
  final TicketRow row;

  const _TicketRowTile({required this.row});

  @override
  State<_TicketRowTile> createState() => _TicketRowTileState();
}

class _TicketRowTileState extends State<_TicketRowTile> {
  bool _copied = false;

  Future<void> _copy() async {
    await Clipboard.setData(ClipboardData(text: widget.row.value));
    await HapticFeedback.selectionClick();
    if (!mounted) return;
    setState(() => _copied = true);
    await Future<void>.delayed(const Duration(milliseconds: 1400));
    if (mounted) setState(() => _copied = false);
  }

  @override
  Widget build(BuildContext context) {
    final content = Row(
      children: [
        Text(widget.row.label, style: AppTypography.caption),
        const SizedBox(width: AppSpacing.md),
        Expanded(
          child: Text(
            widget.row.value,
            textAlign: TextAlign.right,
            overflow: TextOverflow.ellipsis,
            style: AppTypography.figureSmall.copyWith(color: AppColors.ink, fontWeight: FontWeight.w700),
          ),
        ),
        if (widget.row.copyable) ...[
          const SizedBox(width: AppSpacing.xs),
          AnimatedSwitcher(
            duration: AppMotion.quick,
            child: _copied
                ? const Icon(Icons.check_rounded, key: ValueKey('ok'), size: 15, color: AppColors.positive)
                : const Icon(Icons.copy_rounded, key: ValueKey('copy'), size: 15, color: AppColors.inkFaint),
          ),
        ],
      ],
    );

    if (!widget.row.copyable) return content;
    return Pressable(onTap: _copy, child: content);
  }
}
