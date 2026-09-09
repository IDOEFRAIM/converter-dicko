import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../core/auth/auth_session.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../core/theme/app_typography.dart';
import '../../../shared/widgets/corridor_flow.dart';
import '../../../shared/widgets/grain.dart';
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
    // Toujours l'accent PRO ici en pratique : la session (et donc le profil)
    // n'est restauree qu'apres ce premier rendu (voir _bootstrap) -- lu via
    // le Theme malgre tout, jamais AppColors.navy en dur, pour rester
    // coherent si ce timing change un jour.
    return Scaffold(
      body: DecoratedBox(
        decoration: const BoxDecoration(gradient: AppColors.lacquerGradient),
        child: Stack(
          children: [
            const Positioned.fill(child: LedgerGrain(opacity: 0.05)),
            Center(
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: AppSpacing.xxl),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(
                      'CONVERTER',
                      style: AppTypography.eyebrow.copyWith(color: AppColors.keyline, letterSpacing: 4),
                    ),
                    const SizedBox(height: AppSpacing.lg),
                    const CorridorFlow(color: AppColors.onLacquer, height: 96, motes: 4),
                    const SizedBox(height: AppSpacing.md),
                    Text(
                      'Burkina Faso  ·  Chine',
                      style: AppTypography.caption.copyWith(color: AppColors.onLacquerMuted, letterSpacing: 0.5),
                    ),
                    const SizedBox(height: AppSpacing.xxl),
                    const SizedBox(
                      width: 24,
                      height: 24,
                      child: CircularProgressIndicator(strokeWidth: 2, color: AppColors.keyline),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
