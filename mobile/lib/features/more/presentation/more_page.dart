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
///
/// Reorganisation retour client sept. 2026 : la messagerie/reclamation et
/// les notifications etaient noyees en bas d'une liste de tuiles identiques
/// -- "on voit pas du premier coup". La messagerie a sa propre bannniere en
/// tete de page (meme grammaire que [_KycBanner] sur l'accueil) et les
/// notifications passent d'une tuile enfouie a une cloche dans l'AppBar,
/// visible immediatement. Taux preferentiel et Espace professionnel sont
/// retires de cette page (peu utilises, encombraient la vue) -- routes et
/// pages conservees pour reactivation future, meme convention que
/// Portefeuille sur l'accueil.
class MorePage extends StatelessWidget {
  const MorePage({super.key});

  @override
  Widget build(BuildContext context) {
    final authSession = context.watch<AuthSession>();
    final user = authSession.currentUser;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Plus'),
        actions: [
          IconButton(
            icon: const Icon(Icons.notifications_outlined),
            tooltip: 'Notifications',
            onPressed: () => context.push('/more/notifications'),
          ),
        ],
      ),
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
          _SupportBanner(onTap: () => context.push('/more/support')),
          const SizedBox(height: AppSpacing.xl),
          _MoreSection(
            title: 'TAUX',
            tiles: [
              _MoreTile(
                icon: Icons.show_chart,
                color: ExperiencePalette.accentFor(authSession.experienceProfile, AccentRole.rate).color,
                label: 'Historique',
                onTap: () => context.push('/more/rates'),
              ),
              _MoreTile(
                icon: Icons.notifications_active_outlined,
                color: ExperiencePalette.accentFor(authSession.experienceProfile, AccentRole.rate).color,
                label: 'Alertes',
                onTap: () => context.push('/more/rate-alerts'),
              ),
            ],
          ),
          const SizedBox(height: AppSpacing.xl),
          _MoreSection(
            title: 'MON COMPTE',
            // Portefeuille retire (retour client) : aucun canal de rechargement
            // n'existe encore cote client -- voir la meme note dans home_page.dart.
            tiles: [
              _MoreTile(
                icon: Icons.verified_user_outlined,
                color: AppColors.ochre,
                label: "Verification d'identite",
                onTap: () => context.push('/more/kyc'),
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

/// Bannniere en tete de page (retour client : la messagerie/reclamation
/// doit se voir "du premier coup") -- meme grammaire que [_KycBanner] sur
/// l'accueil, plus visible qu'une tuile parmi d'autres.
class _SupportBanner extends StatelessWidget {
  final VoidCallback onTap;

  const _SupportBanner({required this.onTap});

  @override
  Widget build(BuildContext context) {
    final primary = Theme.of(context).colorScheme.primary;
    return InkWell(
      borderRadius: BorderRadius.circular(AppSpacing.radiusMd),
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.all(AppSpacing.md),
        decoration: AppSurfaces.paper(),
        child: Row(
          children: [
            Container(
              width: 40,
              height: 40,
              alignment: Alignment.center,
              decoration: BoxDecoration(color: primary.withValues(alpha: 0.14), shape: BoxShape.circle),
              child: Icon(Icons.forum_outlined, color: primary, size: 20),
            ),
            const SizedBox(width: AppSpacing.md),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('Messagerie / Reclamation', style: AppTypography.bodyStrong),
                  const SizedBox(height: 2),
                  Text('Une question, un probleme ? Ecrivez-nous.', style: AppTypography.caption),
                ],
              ),
            ),
            const Icon(Icons.chevron_right, color: AppColors.inkFaint),
          ],
        ),
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
