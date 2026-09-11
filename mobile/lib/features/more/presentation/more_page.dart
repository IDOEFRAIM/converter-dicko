import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../../core/auth/auth_session.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../core/theme/app_surfaces.dart';
import '../../../core/theme/app_typography.dart';
import '../../../core/theme/experience_theme.dart';
import '../../../shared/widgets/icon_badge.dart';
import '../../auth/data/auth_repository.dart';

/// Menu "Plus" : fonctionnalites secondaires, regroupees par theme (retour
/// client sept. 2026 : "app intuitive, on doit clairement comprendre
/// comment ca fonctionne") -- une longue liste plate de 7 lignes identiques
/// ne le permettait pas. Chaque tuile porte l'icone coloree de sa famille,
/// meme grammaire que [QuickActionsRow] (accueil) et les cartes de detail
/// de transfert.
class MorePage extends StatelessWidget {
  const MorePage({super.key});

  @override
  Widget build(BuildContext context) {
    final authSession = context.watch<AuthSession>();
    final user = authSession.currentUser;
    final profile = authSession.experienceProfile;

    return Scaffold(
      appBar: AppBar(title: const Text('Plus')),
      body: ListView(
        padding: const EdgeInsets.all(AppSpacing.lg),
        children: [
          if (user != null) ...[
            Row(
              children: [
                CircleAvatar(
                  radius: 24,
                  backgroundColor: Theme.of(context).colorScheme.primary,
                  child: Text(
                    user.firstName.isEmpty ? '?' : user.firstName[0].toUpperCase(),
                    style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w700, fontSize: 18),
                  ),
                ),
                const SizedBox(width: AppSpacing.md),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(user.fullName, style: AppTypography.titleMedium),
                      Text(user.phone, style: AppTypography.caption),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: AppSpacing.xl),
          ],
          _MoreSection(
            title: 'TAUX',
            tiles: [
              _MoreTile(
                icon: Icons.show_chart,
                color: ExperiencePalette.accentFor(profile, AccentRole.rate).color,
                label: 'Historique',
                onTap: () => context.push('/more/rates'),
              ),
              _MoreTile(
                icon: Icons.notifications_active_outlined,
                color: ExperiencePalette.accentFor(profile, AccentRole.rate).color,
                label: 'Alertes',
                onTap: () => context.push('/more/rate-alerts'),
              ),
              _MoreTile(
                icon: Icons.trending_up,
                color: ExperiencePalette.accentFor(profile, AccentRole.rate).color,
                label: 'Taux preferentiel',
                onTap: () => context.push('/more/preferred-rate'),
              ),
            ],
          ),
          const SizedBox(height: AppSpacing.xl),
          _MoreSection(
            title: 'MON COMPTE',
            tiles: [
              _MoreTile(
                icon: Icons.account_balance_wallet_outlined,
                color: ExperiencePalette.accentFor(profile, AccentRole.premium).color,
                label: 'Portefeuille',
                onTap: () => context.push('/more/wallet'),
              ),
              _MoreTile(
                icon: Icons.verified_user_outlined,
                color: AppColors.ochre,
                label: "Verification d'identite",
                onTap: () => context.push('/more/kyc'),
              ),
              _MoreTile(
                icon: Icons.notifications_outlined,
                color: Theme.of(context).colorScheme.primary,
                label: 'Notifications',
                onTap: () => context.push('/more/notifications'),
              ),
              _MoreTile(
                icon: Icons.business_center_outlined,
                color: ExperiencePalette.accentFor(profile, AccentRole.group).color,
                label: 'Espace professionnel',
                onTap: () => context.push('/more/business'),
              ),
            ],
          ),
          const SizedBox(height: AppSpacing.xl),
          ClipRRect(
            borderRadius: BorderRadius.circular(AppSpacing.radiusMd),
            child: Container(
              decoration: AppSurfaces.paper(raised: false),
              child: _MoreTile(
                icon: Icons.logout,
                color: AppColors.negative,
                label: 'Se deconnecter',
                showChevron: false,
                onTap: () => context.read<AuthRepository>().logout(),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _MoreSection extends StatelessWidget {
  final String title;
  final List<_MoreTile> tiles;

  const _MoreSection({required this.title, required this.tiles});

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(title, style: AppTypography.eyebrow),
        const SizedBox(height: AppSpacing.sm),
        ClipRRect(
          borderRadius: BorderRadius.circular(AppSpacing.radiusMd),
          child: Container(
            decoration: AppSurfaces.paper(raised: false),
            child: Column(
              children: [
                for (var i = 0; i < tiles.length; i++) ...[
                  tiles[i],
                  if (i < tiles.length - 1) const Divider(height: 1, indent: AppSpacing.lg + 40 + AppSpacing.md),
                ],
              ],
            ),
          ),
        ),
      ],
    );
  }
}

class _MoreTile extends StatelessWidget {
  final IconData icon;
  final Color color;
  final String label;
  final VoidCallback onTap;
  final bool showChevron;

  const _MoreTile({
    required this.icon,
    required this.color,
    required this.label,
    required this.onTap,
    this.showChevron = true,
  });

  @override
  Widget build(BuildContext context) {
    return ListTile(
      leading: IconBadge(icon: icon, color: color, size: 40),
      title: Text(label, style: AppTypography.body.copyWith(color: showChevron ? null : color)),
      trailing: showChevron ? const Icon(Icons.chevron_right, color: AppColors.inkFaint) : null,
      onTap: onTap,
    );
  }
}
