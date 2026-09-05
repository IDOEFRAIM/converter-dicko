import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../core/auth/auth_session.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../data/auth_repository.dart';

/// Ecran de demarrage : tente de restaurer la session a partir d'un jeton
/// deja persiste (`GET /auth/me`), puis laisse le routeur rediriger vers
/// `/home` ou `/login` une fois [AuthSession.initialized] vrai — cet ecran
/// ne navigue jamais lui-meme.
class SplashPage extends StatefulWidget {
  static const routePath = '/splash';

  const SplashPage({super.key});

  @override
  State<SplashPage> createState() => _SplashPageState();
}

class _SplashPageState extends State<SplashPage> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _bootstrap());
  }

  Future<void> _bootstrap() async {
    final authRepository = context.read<AuthRepository>();
    final authSession = context.read<AuthSession>();
    final user = await authRepository.restoreSession();
    if (!mounted) return;
    authSession.completeBootstrap(user: user);
  }

  @override
  Widget build(BuildContext context) {
    return const Scaffold(
      backgroundColor: AppColors.navy,
      body: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text('🇧🇫 → 🇨🇳', style: TextStyle(fontSize: 32)),
            SizedBox(height: AppSpacing.lg),
            SizedBox(
              width: 28,
              height: 28,
              child: CircularProgressIndicator(strokeWidth: 2.5, color: Colors.white),
            ),
          ],
        ),
      ),
    );
  }
}
