import 'package:flutter/material.dart';

/// Courbe minimaliste dessinee (CustomPainter), sans dependance de
/// graphique — miroir du sparkline SVG deja utilise cote Angular (mission
/// section 6 : "le sparkline actuel suffit probablement", ne pas ajouter de
/// librairie lourde). Purement decoratif/illustratif : la valeur exacte
/// reste affichee en texte a cote (accessibilite, mission section 29).
class Sparkline extends StatelessWidget {
  final List<double> values;
  final Color color;
  final double height;

  const Sparkline({super.key, required this.values, required this.color, this.height = 90});

  @override
  Widget build(BuildContext context) {
    if (values.length < 2) {
      return SizedBox(height: height);
    }
    return SizedBox(
      height: height,
      width: double.infinity,
      child: CustomPaint(painter: _SparklinePainter(values: values, color: color)),
    );
  }
}

class _SparklinePainter extends CustomPainter {
  final List<double> values;
  final Color color;

  const _SparklinePainter({required this.values, required this.color});

  @override
  void paint(Canvas canvas, Size size) {
    final min = values.reduce((a, b) => a < b ? a : b);
    final max = values.reduce((a, b) => a > b ? a : b);
    final span = (max - min).abs() < 1e-9 ? 1.0 : (max - min);
    const verticalPadding = 6.0;
    final stepX = size.width / (values.length - 1);

    final path = Path();
    for (var i = 0; i < values.length; i++) {
      final x = i * stepX;
      final normalized = (values[i] - min) / span;
      final y = size.height - verticalPadding - normalized * (size.height - verticalPadding * 2);
      if (i == 0) {
        path.moveTo(x, y);
      } else {
        path.lineTo(x, y);
      }
    }

    final linePaint = Paint()
      ..color = color
      ..style = PaintingStyle.stroke
      ..strokeWidth = 2
      ..strokeCap = StrokeCap.round
      ..strokeJoin = StrokeJoin.round;
    canvas.drawPath(path, linePaint);
  }

  @override
  bool shouldRepaint(covariant _SparklinePainter oldDelegate) =>
      oldDelegate.values != values || oldDelegate.color != color;
}
