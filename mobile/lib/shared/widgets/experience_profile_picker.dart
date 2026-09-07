import 'package:flutter/material.dart';

import '../../core/theme/app_spacing.dart';
import '../../core/theme/app_typography.dart';
import '../../core/theme/experience_theme.dart';
import '../models/current_user.dart';

/// Selection de l'habillage marketing (PRO / Mode Epopee / Mode Histoire) —
/// reutilise a l'inscription et dans "Plus" pour en changer plus tard. Purement
/// cosmetique : ne montre jamais de difference de taux/frais entre les choix.
class ExperienceProfilePicker extends StatelessWidget {
  final ExperienceProfile selected;
  final ValueChanged<ExperienceProfile> onChanged;

  const ExperienceProfilePicker({super.key, required this.selected, required this.onChanged});

  static const _options = [
    (
      profile: ExperienceProfile.pro,
      title: 'PRO',
      description: 'Sobre et efficace — pour gerer son business.',
      icon: Icons.business_center_outlined,
    ),
    (
      profile: ExperienceProfile.studentMale,
      title: 'Mode Epopee',
      description: 'Defis, badges, esprit de competition.',
      icon: Icons.local_fire_department_outlined,
    ),
    (
      profile: ExperienceProfile.studentFemale,
      title: 'Mode Histoire',
      description: 'Emotion, partage, esthetique douce.',
      icon: Icons.auto_awesome_outlined,
    ),
  ];

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: _options
          .map(
            (option) => Padding(
              padding: const EdgeInsets.only(bottom: AppSpacing.sm),
              child: _OptionCard(
                title: option.title,
                description: option.description,
                icon: option.icon,
                color: ExperiencePalette.primaryFor(option.profile),
                selected: option.profile == selected,
                onTap: () => onChanged(option.profile),
              ),
            ),
          )
          .toList(growable: false),
    );
  }
}

class _OptionCard extends StatelessWidget {
  final String title;
  final String description;
  final IconData icon;
  final Color color;
  final bool selected;
  final VoidCallback onTap;

  const _OptionCard({
    required this.title,
    required this.description,
    required this.icon,
    required this.color,
    required this.selected,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return InkWell(
      borderRadius: BorderRadius.circular(AppSpacing.radiusMd),
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.all(AppSpacing.md),
        decoration: BoxDecoration(
          border: Border.all(color: selected ? color : const Color(0xFFE6E9F0), width: selected ? 2 : 1),
          borderRadius: BorderRadius.circular(AppSpacing.radiusMd),
          color: selected ? color.withValues(alpha: 0.06) : Colors.white,
        ),
        child: Row(
          children: [
            Icon(icon, color: color),
            const SizedBox(width: AppSpacing.md),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(title, style: AppTypography.bodyStrong.copyWith(color: color)),
                  Text(description, style: AppTypography.caption),
                ],
              ),
            ),
            Icon(
              selected ? Icons.check_circle : Icons.circle_outlined,
              color: selected ? color : const Color(0xFF9AA0AB),
            ),
          ],
        ),
      ),
    );
  }
}
