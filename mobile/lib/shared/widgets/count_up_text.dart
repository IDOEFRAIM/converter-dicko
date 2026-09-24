import 'package:flutter/widgets.dart';

import '../../core/theme/app_motion.dart';

/// Chiffre qui s'incremente en animation jusqu'a sa valeur — le tic d'un
/// tableau de cotation (langage de design "Le Comptoir").
///
/// A utiliser avec un style `AppTypography.figure*` (figures tabulaires : la
/// largeur ne bouge pas pendant le comptage). Le [formatter] recoit la valeur
/// intermediaire et rend la chaine finale (ex. `Money(...).formatted()`,
/// `'${v.round()} XP'`).
///
/// - Premiere apparition : compte depuis 0 (effet de revelation).
/// - Changement ulterieur : compte depuis l'ancienne valeur.
/// - Reduction des mouvements demandee : affiche directement la valeur finale.
class CountUpText extends StatefulWidget {
  final double value;
  final String Function(double value) formatter;
  final TextStyle? style;
  final TextAlign? textAlign;
  final Duration duration;

  const CountUpText({
    super.key,
    required this.value,
    required this.formatter,
    this.style,
    this.textAlign,
    this.duration = AppMotion.slow,
  });

  @override
  State<CountUpText> createState() => _CountUpTextState();
}

class _CountUpTextState extends State<CountUpText> {
  double _from = 0;

  @override
  void didUpdateWidget(CountUpText oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.value != widget.value) {
      _from = oldWidget.value;
    }
  }

  @override
  Widget build(BuildContext context) {
    if (AppMotion.reduceMotion(context) || _from == widget.value) {
      return Text(widget.formatter(widget.value), style: widget.style, textAlign: widget.textAlign);
    }
    return TweenAnimationBuilder<double>(
      tween: Tween<double>(begin: _from, end: widget.value),
      duration: widget.duration,
      curve: AppMotion.enter,
      builder: (context, value, _) => Text(
        widget.formatter(value),
        style: widget.style,
        textAlign: widget.textAlign,
      ),
    );
  }
}
