import 'package:flutter/material.dart';

import '../../core/theme/app_spacing.dart';
import 'empty_state.dart';

/// Placeholder honnete pour une feature pas encore implementee dans ce lot —
/// jamais une donnee fictive (mission section 37), jamais une route morte.
/// A remplacer ecran par ecran au fil des lots suivants (M2-M6).
class ComingSoonPage extends StatelessWidget {
  final String title;
  final IconData icon;

  const ComingSoonPage({super.key, required this.title, this.icon = Icons.construction_outlined});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(title)),
      body: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Center(
          child: EmptyState(
            icon: icon,
            title: 'Bientot disponible',
            description: 'Cette section arrive dans une prochaine mise a jour.',
          ),
        ),
      ),
    );
  }
}
