import 'package:flutter/physics.dart';
import 'package:flutter/widgets.dart';

import '../../core/theme/app_motion.dart';

/// Enfoncement au toucher, retour au repos par ressort (`AppMotion.spring`).
///
/// A envelopper autour de toute carte/tuile actionnable : la matiere reagit
/// sous le doigt au lieu de rester inerte. Aucun effet d'encre Material — le
/// mouvement EST le retour tactile (langage de design "Le Comptoir").
///
/// Se fige (aucune animation) si l'utilisateur a demande la reduction des
/// mouvements.
class Pressable extends StatefulWidget {
  final Widget child;
  final VoidCallback? onTap;

  /// Amplitude de l'enfoncement (delta d'echelle). 0.03 = -3 % au repos presse.
  final double depth;

  const Pressable({super.key, required this.child, this.onTap, this.depth = 0.03});

  @override
  State<Pressable> createState() => _PressableState();
}

class _PressableState extends State<Pressable> with SingleTickerProviderStateMixin {
  // value 1 = repos, 0 = totalement enfonce.
  late final AnimationController _c = AnimationController(
    vsync: this,
    value: 1,
    duration: AppMotion.quick,
  );

  void _press() {
    if (AppMotion.reduceMotion(context)) return;
    _c.animateTo(0, duration: AppMotion.instant, curve: AppMotion.enter);
  }

  void _release() {
    if (AppMotion.reduceMotion(context)) {
      _c.value = 1;
      return;
    }
    _c.animateWith(SpringSimulation(AppMotion.spring, _c.value, 1, 0));
  }

  @override
  void dispose() {
    _c.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final enabled = widget.onTap != null;
    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTapDown: enabled ? (_) => _press() : null,
      onTapUp: enabled ? (_) => _release() : null,
      onTapCancel: enabled ? _release : null,
      onTap: widget.onTap,
      child: AnimatedBuilder(
        animation: _c,
        builder: (context, child) => Transform.scale(
          scale: 1 - widget.depth * (1 - _c.value),
          child: child,
        ),
        child: widget.child,
      ),
    );
  }
}
