import 'package:flutter/material.dart';

import '../../core/theme/app_colors.dart';
import '../../core/theme/app_spacing.dart';
import '../../core/theme/app_typography.dart';
import '../models/current_user.dart';

/// Determine le profil d'experience **a partir de qui est l'utilisateur**
/// (remarque produit #1) — ce n'est jamais un choix d'habillage esthetique.
/// Deux questions : l'usage (activite pro vs perso / etudes), puis la civilite
/// pour le profil perso. Le profil pilote ensuite les couleurs de l'interface,
/// mais l'utilisateur ne "choisit pas un theme".
///
/// Fixe une fois pour toutes a l'inscription : il n'existe plus aucun ecran
/// pour le modifier ensuite.
class ProfileIdentityPicker extends StatefulWidget {
  final ValueChanged<ExperienceProfile> onChanged;

  const ProfileIdentityPicker({super.key, required this.onChanged});

  @override
  State<ProfileIdentityPicker> createState() => _ProfileIdentityPickerState();
}

enum _Usage { professional, personal }

enum _Civility { madame, monsieur }

class _ProfileIdentityPickerState extends State<ProfileIdentityPicker> {
  _Usage? _usage;
  _Civility? _civility;

  void _emit() {
    if (_usage == _Usage.professional) {
      widget.onChanged(ExperienceProfile.pro);
    } else if (_usage == _Usage.personal && _civility == _Civility.madame) {
      widget.onChanged(ExperienceProfile.studentFemale);
    } else if (_usage == _Usage.personal && _civility == _Civility.monsieur) {
      widget.onChanged(ExperienceProfile.studentMale);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text('Vous utiliserez Converter surtout pour…', style: AppTypography.bodyStrong),
        const SizedBox(height: AppSpacing.sm),
        _choice(
          label: 'Mon activite (commerce, import, entreprise)',
          selected: _usage == _Usage.professional,
          onTap: () => setState(() {
            _usage = _Usage.professional;
            _civility = null;
            _emit();
          }),
        ),
        _choice(
          label: 'Mes besoins personnels ou mes etudes',
          selected: _usage == _Usage.personal,
          onTap: () => setState(() {
            _usage = _Usage.personal;
            _emit();
          }),
        ),
        if (_usage == _Usage.personal) ...[
          const SizedBox(height: AppSpacing.md),
          Text('Civilite', style: AppTypography.bodyStrong),
          const SizedBox(height: AppSpacing.sm),
          _choice(
            label: 'Madame',
            selected: _civility == _Civility.madame,
            onTap: () => setState(() {
              _civility = _Civility.madame;
              _emit();
            }),
          ),
          _choice(
            label: 'Monsieur',
            selected: _civility == _Civility.monsieur,
            onTap: () => setState(() {
              _civility = _Civility.monsieur;
              _emit();
            }),
          ),
        ],
      ],
    );
  }

  Widget _choice({required String label, required bool selected, required VoidCallback onTap}) {
    final accent = Theme.of(context).colorScheme.primary;
    return Padding(
      padding: const EdgeInsets.only(bottom: AppSpacing.sm),
      child: InkWell(
        borderRadius: BorderRadius.circular(AppSpacing.radiusMd),
        onTap: onTap,
        child: Container(
          padding: const EdgeInsets.all(AppSpacing.md),
          decoration: BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.circular(AppSpacing.radiusMd),
            border: Border.all(color: selected ? accent : AppColors.outline, width: selected ? 1.6 : 1),
          ),
          child: Row(
            children: [
              Icon(
                selected ? Icons.radio_button_checked : Icons.radio_button_unchecked,
                color: selected ? accent : AppColors.inkFaint,
                size: 20,
              ),
              const SizedBox(width: AppSpacing.md),
              Expanded(child: Text(label, style: AppTypography.body)),
            ],
          ),
        ),
      ),
    );
  }
}
