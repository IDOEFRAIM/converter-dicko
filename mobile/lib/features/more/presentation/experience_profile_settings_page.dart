import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

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
class ExperienceProfileSettingsPage extends StatefulWidget {
  final ExperienceProfile current;

  const ExperienceProfileSettingsPage({super.key, required this.current});

  @override
  State<ExperienceProfileSettingsPage> createState() => _ExperienceProfileSettingsPageState();
}

class _ExperienceProfileSettingsPageState extends State<ExperienceProfileSettingsPage> {
  late ExperienceProfile _selected = widget.current;
  bool _saving = false;
  String? _errorMessage;

  Future<void> _save() async {
    if (_saving || _selected == widget.current) return;
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
                onPressed: _selected == widget.current ? null : _save,
              ),
            ],
          ),
        ),
      ),
    );
  }
}
