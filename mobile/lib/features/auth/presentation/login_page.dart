import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../../core/errors/api_exception.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../core/theme/app_typography.dart';
import '../../../shared/utils/validators.dart';
import '../../../shared/widgets/primary_action.dart';
import '../data/auth_repository.dart';
import '../models/auth_models.dart';

class LoginPage extends StatefulWidget {
  static const routeName = 'login';
  static const routePath = '/login';

  const LoginPage({super.key});

  @override
  State<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends State<LoginPage> {
  final _formKey = GlobalKey<FormState>();
  final _phoneController = TextEditingController();
  final _passwordController = TextEditingController();

  bool _submitting = false;
  String? _errorMessage;
  bool _obscurePassword = true;

  @override
  void dispose() {
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
      await authRepository.login(
        LoginRequest(
          phone: Validators.normalizePhone(_phoneController.text.trim()),
          password: _passwordController.text,
        ),
      );
      // La navigation vers l'accueil est geree par le routeur (redirection
      // reactive a AuthSession) — aucun push manuel necessaire ici.
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
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.symmetric(horizontal: AppSpacing.xl, vertical: AppSpacing.xxl),
          child: Form(
            key: _formKey,
            autovalidateMode: AutovalidateMode.onUserInteraction,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const SizedBox(height: AppSpacing.xxl),
                Text(
                  '🇧🇫 → 🇨🇳',
                  style: AppTypography.metricMedium.copyWith(color: Theme.of(context).colorScheme.primary),
                ),
                const SizedBox(height: AppSpacing.md),
                Text('Bon retour', style: AppTypography.titleLarge(Theme.of(context).colorScheme.primary)),
                const SizedBox(height: AppSpacing.xs),
                Text(
                  'Connectez-vous pour suivre vos paiements vers la Chine.',
                  style: AppTypography.caption,
                ),
                const SizedBox(height: AppSpacing.xxl),
                if (_errorMessage != null) ...[
                  _ErrorBanner(message: _errorMessage!),
                  const SizedBox(height: AppSpacing.lg),
                ],
                TextFormField(
                  controller: _phoneController,
                  keyboardType: TextInputType.phone,
                  textInputAction: TextInputAction.next,
                  decoration: const InputDecoration(
                    labelText: 'Numero de telephone',
                    hintText: '+2250700000000',
                  ),
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
                    suffixIcon: IconButton(
                      onPressed: () => setState(() => _obscurePassword = !_obscurePassword),
                      icon: Icon(_obscurePassword ? Icons.visibility_outlined : Icons.visibility_off_outlined),
                      tooltip: _obscurePassword ? 'Afficher le mot de passe' : 'Masquer le mot de passe',
                    ),
                  ),
                  validator: (value) {
                    if (value == null || value.isEmpty) {
                      return 'Le mot de passe est obligatoire.';
                    }
                    return null;
                  },
                ),
                const SizedBox(height: AppSpacing.xxl),
                PrimaryAction(label: 'Se connecter', onPressed: _submit, loading: _submitting),
                const SizedBox(height: AppSpacing.xl),
                Center(
                  child: TextButton(
                    onPressed: _submitting ? null : () => context.push('/register'),
                    child: const Text('Creer un compte'),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _ErrorBanner extends StatelessWidget {
  final String message;

  const _ErrorBanner({required this.message});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(AppSpacing.md),
      decoration: BoxDecoration(
        color: AppColors.negativeSurface,
        borderRadius: BorderRadius.circular(AppSpacing.radiusSm),
      ),
      child: Text(message, style: AppTypography.body.copyWith(color: AppColors.negative)),
    );
  }
}
