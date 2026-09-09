import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../core/auth/auth_session.dart';
import '../../../core/errors/api_exception.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../core/theme/app_typography.dart';
import '../../../shared/models/current_user.dart';
import '../../../shared/widgets/experience_profile_picker.dart';
import '../../../shared/widgets/primary_action.dart';
import '../../auth/data/auth_repository.dart';

/// Changer d'habillage apres l'inscription (mission "differenciation
/// marketing") — purement cosmetique, jamais un changement de taux/frais.
///
/// Lit le profil courant directement depuis [AuthSession] (jamais via
/// `extra` de go_router) : `updateExperienceProfile` declenche
/// `AuthSession.notifyListeners()`, ce qui force go_router (dont le
/// `refreshListenable` observe cette meme session) a reconstruire la route
/// courante -- `extra` ne survit pas a cette reconstruction (il n'est jamais
/// re-encode dans l'URL), ce qui provoquait un crash ("Null is not a subtype
/// of ExperienceProfile") pile au moment ou l'enregistrement reussissait.
class ExperienceProfileSettingsPage extends StatefulWidget {
  const ExperienceProfileSettingsPage({super.key});

  @override
  State<ExperienceProfileSettingsPage> createState() => _ExperienceProfileSettingsPageState();
}

class _ExperienceProfileSettingsPageState extends State<ExperienceProfileSettingsPage> {
  late final ExperienceProfile _initial = context.read<AuthSession>().experienceProfile;
  late ExperienceProfile _selected = _initial;
  bool _saving = false;
  String? _errorMessage;

  Future<void> _save() async {
    if (_saving || _selected == _initial) return;
    setState(() {
      _saving = true;
      _errorMessage = null;
    });
    try {
      await context.read<AuthRepository>().updateExperienceProfile(_selected);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Habillage mis a jour.')));
      Navigator.of(context).pop();
    } on ApiException catch (error) {
      setState(() => _errorMessage = error.message);
    } finally {
      if (mounted) {
        setState(() => _saving = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Habillage')),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(AppSpacing.lg),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'Purement esthetique — meme taux, memes frais pour tout le monde, quel que soit ton choix.',
                style: AppTypography.caption,
              ),
              const SizedBox(height: AppSpacing.lg),
              ExperienceProfilePicker(
                selected: _selected,
                onChanged: (profile) => setState(() => _selected = profile),
              ),
              if (_errorMessage != null) ...[
                const SizedBox(height: AppSpacing.md),
                Text(_errorMessage!, style: AppTypography.body.copyWith(color: AppColors.negative)),
              ],
              const SizedBox(height: AppSpacing.xl),
              PrimaryAction(
                label: 'Enregistrer',
                loading: _saving,
                onPressed: _selected == _initial ? null : _save,
              ),
            ],
          ),
        ),
      ),
    );
  }
}
