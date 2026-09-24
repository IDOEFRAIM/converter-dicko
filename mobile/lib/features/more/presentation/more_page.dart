import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../../core/auth/auth_session.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../core/theme/app_surfaces.dart';
import '../../../core/theme/app_typography.dart';
import '../../../core/theme/experience_theme.dart';
import '../../../shared/utils/legal_links.dart';
import '../../../shared/widgets/icon_badge.dart';
import '../../../shared/widgets/notification_bell_button.dart';
import '../../auth/data/auth_repository.dart';
import 'delete_account_dialog.dart';

/// Menu "Plus" : fonctionnalites secondaires, regroupees par theme (retour
/// client sept. 2026 : "app intuitive, on doit clairement comprendre
/// comment ca fonctionne") -- une longue liste plate de 7 lignes identiques
/// ne le permettait pas. Chaque tuile porte l'icone coloree de sa famille,
/// meme grammaire que [QuickActionsRow] (accueil) et les cartes de detail
/// de transfert.
///
/// Reorganisation retour client sept. 2026 : la messagerie/reclamation et
/// les notifications etaient noyees en bas d'une liste de tuiles identiques
/// -- "on voit pas du premier coup". Un premier essai leur donnait une
/// bannniere/une cloche sur CETTE page, mais le vrai probleme etait un
/// niveau plus haut : l'utilisateur devait deja savoir qu'il fallait ouvrir
/// "Plus" pour les trouver. La messagerie est donc passee a un onglet de
/// premier niveau (voir [AppShell]) -- plus de bannniere ici, un seul
/// endroit ou la chercher. La cloche [NotificationBellButton] a eu le meme
/// probleme (retour client : "le bouton doit etre sur toutes les pages, pas
/// seulement sur Plus") -- elle est donc posee dans l'AppBar de CHAQUE onglet
/// racine, celle-ci n'etant qu'un des six. Taux preferentiel et Espace
/// professionnel sont retires de cette page (peu utilises, encombraient la
/// vue) -- routes et pages conservees pour reactivation future, meme
/// convention que Portefeuille sur l'accueil.
class MorePage extends StatelessWidget {
  const MorePage({super.key});

  @override
  Widget build(BuildContext context) {
    final authSession = context.watch<AuthSession>();
    final user = authSession.currentUser;

    return Scaffold(
      appBar: AppBar(title: const Text('Plus'), actions: const [NotificationBellButton()]),
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
          // Retour client oct. 2026 : un utilisateur qui commence a modeliser un paiement
          // puis quitte l'app par inadvertance peut ne pas savoir ou retrouver ce transfert
          // au retour -- "Activite" est deja un onglet racine (voir AppShell), mais un raccourci
          // redondant ici, dans "Plus", rassure l'utilisateur qui ne l'aurait pas repere.
          _MoreSection(
            title: 'MES TRANSFERTS',
            tiles: [
              _MoreTile(
                icon: Icons.receipt_long_outlined,
                color: ExperiencePalette.accentFor(authSession.experienceProfile, AccentRole.send).color,
                label: 'Voir mes transferts',
                onTap: () => context.go('/activity'),
              ),
            ],
          ),
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
              _MoreTile(
                icon: Icons.delete_outline,
                color: AppColors.negative,
                label: 'Supprimer mon compte',
                onTap: user == null
                    ? () {}
                    : () => showDialog<bool>(
                          context: context,
                          builder: (context) => DeleteAccountDialog(
                            authRepository: context.read<AuthRepository>(),
                            requiresPassword: user.hasPassword,
                          ),
                        ),
              ),
            ],
          ),
          const SizedBox(height: AppSpacing.xl),
          // Mentions legales (retour client : "l'utilisateur n'a pas acces aux CGU et a la
          // politique de confidentialite dans l'app") -- pages deja live et a jour, ouvertes
          // dans le navigateur externe plutot que recopiees ici (voir LegalLinks).
          _MoreSection(
            title: 'LEGAL',
            tiles: [
              _MoreTile(
                icon: Icons.privacy_tip_outlined,
                color: AppColors.inkMuted,
                label: 'Politique de confidentialite',
                onTap: () => LegalLinks.open(LegalLinks.privacyPolicy),
              ),
              _MoreTile(
                icon: Icons.description_outlined,
                color: AppColors.inkMuted,
                label: "Conditions generales d'utilisation",
                onTap: () => LegalLinks.open(LegalLinks.termsOfUse),
              ),
              _MoreTile(
                icon: Icons.gavel_outlined,
                color: AppColors.inkMuted,
                label: 'Contrat de licence utilisateur final',
                onTap: () => LegalLinks.open(LegalLinks.endUserLicense),
              ),
            ],
          ),
          const SizedBox(height: AppSpacing.xl),
          ClipRRect(
            borderRadius: BorderRadius.circular(AppSpacing.radiusMd),
            child: Container(
              decoration: AppSurfaces.paper(raised: false),
              // Material transparent : le ListTile peint son splash/highlight
              // sur le Material le plus proche -- sans lui, le fond colore de
              // ce Container (peint APRES par son propre DecoratedBox) le
              // cacherait entierement (avertissement Flutter "ListTile
              // background color or ink splashes may be invisible").
              child: Material(
                type: MaterialType.transparency,
                child: _MoreTile(
                  icon: Icons.logout,
                  color: AppColors.negative,
                  label: 'Se deconnecter',
                  showChevron: false,
                  onTap: () => context.read<AuthRepository>().logout(),
                ),
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
            // Voir la meme note dans MorePage.build() (bouton deconnexion) :
            // sans ce Material transparent, le splash des ListTile serait
            // cache par le fond colore de ce Container.
            child: Material(
              type: MaterialType.transparency,
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
