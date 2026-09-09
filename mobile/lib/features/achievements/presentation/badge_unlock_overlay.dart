import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../../core/auth/auth_session.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../core/theme/app_surfaces.dart';
import '../../../core/theme/app_typography.dart';
import '../../../shared/models/current_user.dart';
import '../../../shared/widgets/grain.dart';
import '../../../shared/widgets/rank_seal.dart';
import '../../notifications/models/notification_models.dart';
import '../application/badge_celebration_controller.dart';

/// Enveloppe la coquille a onglets et declenche, une seule fois, un overlay
/// de celebration plein ecran quand un franchissement de palier de badge est
/// en attente (mission "differenciation marketing" : celebrer le moment, pas
/// seulement l'afficher au prochain "Mes gains").
///
/// Ne s'affiche jamais pour PRO : le backend ne cree aucune notification
/// `BADGE_UNLOCKED` pour ce profil, et [BadgeCelebrationController.check]
/// filtre de toute facon sur le type.
class BadgeCelebrationHost extends StatefulWidget {
  final Widget child;

  const BadgeCelebrationHost({super.key, required this.child});

  @override
  State<BadgeCelebrationHost> createState() => _BadgeCelebrationHostState();
}

class _BadgeCelebrationHostState extends State<BadgeCelebrationHost> with WidgetsBindingObserver {
  bool _presenting = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) context.read<BadgeCelebrationController>().check();
    });
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    // Un ordre passe COMPLETED cote back-office pendant que l'app est en
    // arriere-plan : on reverifie au retour au premier plan.
    if (state == AppLifecycleState.resumed && mounted) {
      context.read<BadgeCelebrationController>().check();
    }
  }

  Future<void> _present(AppNotification notification) async {
    _presenting = true;
    // Capture avant tout await : le controleur doit etre "consomme" meme si
    // ce widget est demonte pendant que l'overlay est ouvert, sinon la
    // celebration se rejouerait au prochain montage.
    final controller = context.read<BadgeCelebrationController>();
    final profile = context.read<AuthSession>().experienceProfile;

    final viewGains = await showGeneralDialog<bool>(
      context: context,
      barrierDismissible: false,
      barrierLabel: 'Nouveau rang',
      barrierColor: Colors.black.withValues(alpha: 0.55),
      transitionDuration: const Duration(milliseconds: 260),
      pageBuilder: (_, _, _) => _BadgeUnlockDialog(notification: notification, profile: profile),
      transitionBuilder: (_, animation, _, child) {
        final curved = CurvedAnimation(parent: animation, curve: Curves.easeOutBack);
        return FadeTransition(
          opacity: animation,
          child: ScaleTransition(scale: Tween<double>(begin: 0.85, end: 1).animate(curved), child: child),
        );
      },
    );

    await controller.consume();
    _presenting = false;

    if (viewGains == true && mounted) {
      context.push('/home/gains');
    }
  }

  @override
  Widget build(BuildContext context) {
    final pending = context.watch<BadgeCelebrationController>().pending;
    if (pending != null && !_presenting) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted || _presenting) return;
        final current = context.read<BadgeCelebrationController>().pending;
        if (current != null) _present(current);
      });
    }
    return widget.child;
  }
}

class _BadgeUnlockDialog extends StatelessWidget {
  final AppNotification notification;
  final ExperienceProfile profile;

  const _BadgeUnlockDialog({required this.notification, required this.profile});

  /// L'initiale du rang, extraite du message deja pret du backend
  /// ("... tu es maintenant Guerrier !") — pour graver la lettre au centre du
  /// sceau. Nul si le format evolue : le sceau reste lisible sans lettre.
  String? get _rankInitial {
    final match = RegExp(r'maintenant\s+([^\s!.,]+)').firstMatch(notification.message);
    return match?.group(1);
  }

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.xl),
        child: Material(
          color: Colors.transparent,
          child: ClipRRect(
            borderRadius: BorderRadius.circular(AppSpacing.radiusMd),
            child: Container(
              constraints: const BoxConstraints(maxWidth: 380),
              padding: const EdgeInsets.all(AppSpacing.xl),
              decoration: AppSurfaces.lacquer(),
              child: Stack(
                children: [
                  const Positioned.fill(child: LedgerGrain(opacity: 0.06)),
                  Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      RankSealBadge(profile: profile, initial: _rankInitial, size: 116),
                      const SizedBox(height: AppSpacing.lg),
                      Text(
                        notification.title,
                        textAlign: TextAlign.center,
                        style: AppTypography.metricMedium.copyWith(color: AppColors.onLacquer),
                      ),
                      const SizedBox(height: AppSpacing.sm),
                      Text(
                        notification.message,
                        textAlign: TextAlign.center,
                        style: AppTypography.body.copyWith(color: AppColors.onLacquerMuted),
                      ),
                      const SizedBox(height: AppSpacing.xl),
                      SizedBox(
                        width: double.infinity,
                        child: FilledButton(
                          style: FilledButton.styleFrom(
                            backgroundColor: AppColors.keyline,
                            foregroundColor: AppColors.lacquer,
                          ),
                          onPressed: () => Navigator.of(context).pop(true),
                          child: const Text('Voir mes gains'),
                        ),
                      ),
                      const SizedBox(height: AppSpacing.sm),
                      TextButton(
                        style: TextButton.styleFrom(foregroundColor: AppColors.onLacquer),
                        onPressed: () => Navigator.of(context).pop(false),
                        child: const Text('Super !'),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
