import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../core/errors/api_exception.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../core/theme/app_typography.dart';
import '../../../shared/models/current_user.dart';
import '../../../shared/utils/validators.dart';
import '../../../shared/widgets/experience_profile_picker.dart';
import '../../../shared/widgets/primary_action.dart';
import '../application/google_sign_in_flow.dart';
import '../data/auth_repository.dart';
import '../models/auth_models.dart';

/// Deuxieme et derniere etape de "Continuer avec Google" quand aucun compte
/// n'est encore associe (voir [GoogleSignInNeedsPhone]) : Google ne
/// transmet jamais de numero de telephone, obligatoire sur cette
/// plateforme (KYC, recherche admin, unicite) — voir
/// `CompleteGoogleSignUpRequest` backend. Prenom/nom sont pre-remplis par
/// Google mais restent modifiables (certains comptes Google ne les
/// exposent pas).
class CompleteGoogleSignupPage extends StatefulWidget {
  final GoogleSignInNeedsPhone args;

  const CompleteGoogleSignupPage({super.key, required this.args});

  @override
  State<CompleteGoogleSignupPage> createState() => _CompleteGoogleSignupPageState();
}

class _CompleteGoogleSignupPageState extends State<CompleteGoogleSignupPage> {
  final _formKey = GlobalKey<FormState>();
  late final _firstNameController = TextEditingController(text: widget.args.suggestedFirstName ?? '');
  late final _lastNameController = TextEditingController(text: widget.args.suggestedLastName ?? '');
  final _phoneController = TextEditingController();

  bool _submitting = false;
  String? _errorMessage;
  ExperienceProfile _experienceProfile = ExperienceProfile.pro;

  @override
  void dispose() {
    _firstNameController.dispose();
    _lastNameController.dispose();
    _phoneController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (_submitting || !_formKey.currentState!.validate()) {
      return;
    }
    setState(() {
      _submitting = true;
      _errorMessage = null;
    });

    final authRepository = context.read<AuthRepository>();
    try {
      await authRepository.completeGoogleSignUp(
        CompleteGoogleSignUpRequest(
          idToken: widget.args.idToken,
          phone: Validators.normalizePhone(_phoneController.text.trim()),
          firstName: _firstNameController.text.trim(),
          lastName: _lastNameController.text.trim(),
          experienceProfile: _experienceProfile,
        ),
      );
      // Session ouverte par AuthRepository -> redirection vers l'accueil geree
      // par le routeur (meme principe que login/googleSignIn).
    } on ApiException catch (error) {
      if (!mounted) return;
      setState(() => _errorMessage = error.message);
    } finally {
      if (mounted) {
        setState(() => _submitting = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Votre numero')),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.symmetric(horizontal: AppSpacing.xl, vertical: AppSpacing.xl),
          child: Form(
            key: _formKey,
            autovalidateMode: AutovalidateMode.onUserInteraction,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  widget.args.email != null
                      ? 'Compte Google : ${widget.args.email}'
                      : 'Compte Google verifie.',
                  style: AppTypography.caption,
                ),
                const SizedBox(height: AppSpacing.xs),
                Text(
                  'Un numero de telephone est necessaire pour finaliser votre compte.',
                  style: AppTypography.caption,
                ),
                const SizedBox(height: AppSpacing.lg),
                if (_errorMessage != null) ...[
                  Container(
                    padding: const EdgeInsets.all(AppSpacing.md),
                    decoration: BoxDecoration(
                      color: AppColors.negativeSurface,
                      borderRadius: BorderRadius.circular(AppSpacing.radiusSm),
                    ),
                    child: Text(_errorMessage!, style: AppTypography.body.copyWith(color: AppColors.negative)),
                  ),
                  const SizedBox(height: AppSpacing.lg),
                ],
                Row(
                  children: [
                    Expanded(
                      child: TextFormField(
                        controller: _firstNameController,
                        textInputAction: TextInputAction.next,
                        maxLength: 80,
                        decoration: const InputDecoration(labelText: 'Prenom'),
                        validator: (v) => Validators.requiredMaxLength(v, 80, label: 'Le prenom'),
                      ),
                    ),
                    const SizedBox(width: AppSpacing.md),
                    Expanded(
                      child: TextFormField(
                        controller: _lastNameController,
                        textInputAction: TextInputAction.next,
                        maxLength: 80,
                        decoration: const InputDecoration(labelText: 'Nom'),
                        validator: (v) => Validators.requiredMaxLength(v, 80, label: 'Le nom'),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: AppSpacing.lg),
                TextFormField(
                  controller: _phoneController,
                  keyboardType: TextInputType.phone,
                  textInputAction: TextInputAction.done,
                  decoration: const InputDecoration(
                    labelText: 'Numero de telephone',
                    hintText: '+2250700000000',
                  ),
                  validator: Validators.phoneE164,
                ),
                const SizedBox(height: AppSpacing.xl),
                Text('QUI ETES-VOUS ?', style: AppTypography.eyebrow),
                const SizedBox(height: AppSpacing.xs),
                Text(
                  'Adapte l\'interface. Memes taux et frais pour tous. Fixe a l\'inscription.',
                  style: AppTypography.caption,
                ),
                const SizedBox(height: AppSpacing.sm),
                ProfileIdentityPicker(
                  onChanged: (profile) => setState(() => _experienceProfile = profile),
                ),
                const SizedBox(height: AppSpacing.xl),
                PrimaryAction(label: 'Creer mon compte', onPressed: _submit, loading: _submitting),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
