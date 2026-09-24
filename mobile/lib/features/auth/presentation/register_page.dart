import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../../core/config/app_config.dart';
import '../../../core/errors/api_exception.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../core/theme/app_typography.dart';
import '../../../shared/models/current_user.dart';
import '../../../shared/utils/validators.dart';
import '../../../shared/widgets/experience_profile_picker.dart';
import '../../../shared/widgets/google_sign_in_button.dart';
import '../../../shared/widgets/phone_number_field.dart';
import '../../../shared/widgets/primary_action.dart';
import '../application/google_sign_in_flow.dart';
import '../data/auth_repository.dart';
import '../data/google_auth_client.dart';
import '../models/auth_models.dart';

class RegisterPage extends StatefulWidget {
  static const routeName = 'register';
  static const routePath = '/register';

  const RegisterPage({super.key});

  @override
  State<RegisterPage> createState() => _RegisterPageState();
}

class _RegisterPageState extends State<RegisterPage> {
  final _formKey = GlobalKey<FormState>();
  final _firstNameController = TextEditingController();
  final _lastNameController = TextEditingController();
  final _phoneController = TextEditingController();
  final _passwordController = TextEditingController();

  bool _submitting = false;
  bool _googleSubmitting = false;
  String? _errorMessage;
  bool _obscurePassword = true;
  ExperienceProfile _experienceProfile = ExperienceProfile.pro;

  @override
  void dispose() {
    _firstNameController.dispose();
    _lastNameController.dispose();
    _phoneController.dispose();
    _passwordController.dispose();
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
      await authRepository.register(
        RegisterRequest(
          phone: Validators.normalizePhone(_phoneController.text.trim()),
          password: _passwordController.text,
          firstName: _firstNameController.text.trim(),
          lastName: _lastNameController.text.trim(),
          experienceProfile: _experienceProfile,
        ),
      );
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Compte cree. Connectez-vous.')),
      );
      context.pop();
    } on ApiException catch (error) {
      if (!mounted) return;
      setState(() => _errorMessage = error.message);
    } finally {
      if (mounted) {
        setState(() => _submitting = false);
      }
    }
  }

  Future<void> _continueWithGoogle() async {
    if (_googleSubmitting) return;
    setState(() {
      _googleSubmitting = true;
      _errorMessage = null;
    });

    try {
      final result = await runGoogleSignIn(
        googleAuthClient: context.read<GoogleAuthClient>(),
        authRepository: context.read<AuthRepository>(),
      );
      // GoogleSignInCancelled / GoogleSignInLoggedIn : rien a faire ici, voir
      // la meme note dans login_page.dart.
      if (result is GoogleSignInNeedsPhone && mounted) {
        context.push('/complete-google-signup', extra: result);
      }
    } on ApiException catch (error) {
      if (!mounted) return;
      setState(() => _errorMessage = error.message);
    } finally {
      if (mounted) {
        setState(() => _googleSubmitting = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Creer un compte')),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.symmetric(horizontal: AppSpacing.xl, vertical: AppSpacing.xl),
          child: Form(
            key: _formKey,
            autovalidateMode: AutovalidateMode.onUserInteraction,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
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
                if (context.read<AppConfig>().googleSignInAvailable) ...[
                  GoogleSignInButton(
                    onPressed: _submitting ? null : _continueWithGoogle,
                    loading: _googleSubmitting,
                  ),
                  const SizedBox(height: AppSpacing.lg),
                  Row(
                    children: [
                      const Expanded(child: Divider()),
                      Padding(
                        padding: const EdgeInsets.symmetric(horizontal: AppSpacing.sm),
                        child: Text('OU', style: AppTypography.caption),
                      ),
                      const Expanded(child: Divider()),
                    ],
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
                PhoneNumberField(
                  controller: _phoneController,
                  textInputAction: TextInputAction.next,
                  validator: Validators.phoneE164,
                ),
                const SizedBox(height: AppSpacing.lg),
                TextFormField(
                  controller: _passwordController,
                  obscureText: _obscurePassword,
                  textInputAction: TextInputAction.done,
                  onFieldSubmitted: (_) => _submit(),
                  decoration: InputDecoration(
                    labelText: 'Mot de passe',
                    helperText: '8 caracteres minimum',
                    suffixIcon: IconButton(
                      onPressed: () => setState(() => _obscurePassword = !_obscurePassword),
                      icon: Icon(_obscurePassword ? Icons.visibility_outlined : Icons.visibility_off_outlined),
                    ),
                  ),
                  validator: Validators.password,
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
