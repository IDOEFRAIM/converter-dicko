import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../../core/auth/auth_session.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../core/theme/app_surfaces.dart';
import '../../../core/theme/app_typography.dart';
import '../../../core/theme/experience_theme.dart';
import '../../../shared/models/current_user.dart';
import '../../../shared/models/money.dart';
import '../../../shared/utils/async_value.dart';
import '../../../shared/utils/date_formatting.dart';
import '../../../shared/widgets/corridor.dart';
import '../../../shared/widgets/count_up_text.dart';
import '../../../shared/widgets/empty_state.dart';
import '../../../shared/widgets/error_state.dart';
import '../../../shared/widgets/loading_view.dart';
import '../../../shared/widgets/pressable.dart';
import '../../../shared/widgets/quick_actions_row.dart';
import '../../../shared/widgets/rank_seal.dart';
import '../../../shared/widgets/rate_display.dart';
import '../../../shared/widgets/section_header.dart';
import '../../../shared/widgets/status_badge.dart';
import '../../achievements/models/achievement_models.dart';
import '../../orders/models/order_models.dart';
import '../../rates/models/rate_models.dart';
import '../../suppliers/models/supplier_models.dart';
import '../application/home_controller.dart';

/// Ecran le plus important de l'app (mission section 18) : communique
/// immediatement 🇧🇫 -> 🇨🇳, le taux du moment, puis les raccourcis
/// d'action ([QuickActionsRow] — retour client : icones plutot que texte),
/// puis un rappel de la derniere operation et des fournisseurs enregistres.
/// Uniquement des donnees reelles de l'API — aucune valeur fictive
/// (section 37).
class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => context.read<HomeController>().loadAll());
  }

  @override
  Widget build(BuildContext context) {
    final controller = context.watch<HomeController>();
    final authSession = context.watch<AuthSession>();
    final user = authSession.currentUser;
    final profile = authSession.experienceProfile;

    return Scaffold(
      body: SafeArea(
        child: RefreshIndicator(
          onRefresh: controller.loadAll,
          color: Theme.of(context).colorScheme.primary,
          child: ListView(
            padding: const EdgeInsets.fromLTRB(AppSpacing.lg, AppSpacing.lg, AppSpacing.lg, AppSpacing.xxxl),
            children: [
              Text(
                // profile reste PRO tant que user est null (voir AuthSession.experienceProfile),
                // donc le fallback "Bonjour" plus bas ne correspond jamais a un habillage etudiant.
                user == null
                    ? 'Bonjour ${ExperienceCopy.greetingEmoji(profile)}'
                    : '${ExperienceCopy.greeting(profile, user.firstName)} ${ExperienceCopy.greetingEmoji(profile)}',
                style: AppTypography.titleLarge(Theme.of(context).colorScheme.primary),
              ),
              const SizedBox(height: AppSpacing.lg),
              if (user != null && !user.kycVerified) ...[
                _KycBanner(onTap: () => context.push('/more/kyc')),
                const SizedBox(height: AppSpacing.lg),
              ],
              const Corridor(level: CorridorLevel.hero),
              const SizedBox(height: AppSpacing.xl),
              QuickActionsRow(
                actions: [
                  QuickAction(
                    icon: Icons.send_outlined,
                    label: 'Payer',
                    background: ExperiencePalette.accentFor(profile, AccentRole.send).surface,
                    foreground: ExperiencePalette.accentFor(profile, AccentRole.send).color,
                    onTap: () => context.go('/pay'),
                  ),
                  QuickAction(
                    icon: Icons.groups_outlined,
                    label: 'Ruee',
                    background: ExperiencePalette.accentFor(profile, AccentRole.group).surface,
                    foreground: ExperiencePalette.accentFor(profile, AccentRole.group).color,
                    onTap: () => context.push('/pay/pools'),
                  ),
                  QuickAction(
                    icon: Icons.trending_up,
                    label: 'Taux pref.',
                    background: ExperiencePalette.accentFor(profile, AccentRole.rate).surface,
                    foreground: ExperiencePalette.accentFor(profile, AccentRole.rate).color,
                    onTap: () => context.push('/more/preferred-rate'),
                  ),
                  QuickAction(
                    icon: Icons.account_balance_wallet_outlined,
                    label: 'Portefeuille',
                    background: ExperiencePalette.accentFor(profile, AccentRole.premium).surface,
                    foreground: ExperiencePalette.accentFor(profile, AccentRole.premium).color,
                    onTap: () => context.push('/more/wallet'),
                  ),
                ],
              ),
              const SizedBox(height: AppSpacing.xl),
              _RateHero(state: controller.latestRate, onRetry: controller.loadRate, profile: profile),
              const SizedBox(height: AppSpacing.xl),
              _AchievementsCard(state: controller.achievements, profile: authSession.experienceProfile),
              const SizedBox(height: AppSpacing.xxl),
              SectionHeader(
                title: 'DERNIERE OPERATION',
                actionLabel: 'Tout voir',
                onActionTap: () => context.go('/activity'),
              ),
              _LastOperationCard(state: controller.lastOperation, onRetry: controller.loadLastOperation),
              const SizedBox(height: AppSpacing.xxl),
              SectionHeader(
                title: 'MES FOURNISSEURS',
                actionLabel: 'Tout voir',
                onActionTap: () => context.go('/suppliers'),
              ),
              _SuppliersPreview(state: controller.suppliers, onRetry: controller.loadSuppliers),
            ],
          ),
        ),
      ),
    );
  }
}

class _RateHero extends StatelessWidget {
  final AsyncValue<PublicRateHistoryEntry?> state;
  final VoidCallback onRetry;
  final ExperienceProfile profile;

  const _RateHero({required this.state, required this.onRetry, required this.profile});

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(AppSpacing.lg),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(AppSpacing.radiusMd),
        border: Border.all(color: AppColors.outline),
      ),
      child: state.when(
        loading: () => const SizedBox(height: 64, child: LoadingView()),
        error: (message) => ErrorState(message: message, onRetry: onRetry),
        data: (entry) {
          if (entry == null) {
            return const EmptyState(icon: Icons.show_chart, title: 'Aucun taux publie');
          }
          return Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Expanded(
                    child: Text(
                      ExperienceCopy.homeRateEyebrow(profile),
                      style: AppTypography.eyebrow,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                  const SizedBox(width: AppSpacing.sm),
                  FilledButton(
                    onPressed: () => context.go('/pay'),
                    style: FilledButton.styleFrom(
                      backgroundColor: ExperiencePalette.accentFor(profile, AccentRole.send).color,
                      padding: const EdgeInsets.symmetric(horizontal: AppSpacing.md, vertical: 6),
                      minimumSize: Size.zero,
                      tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                      shape: const StadiumBorder(),
                    ),
                    child: const Text('Convertir'),
                  ),
                ],
              ),
              const SizedBox(height: AppSpacing.xs),
              RateDisplay(customerRate: entry.customerRate, large: true, color: Theme.of(context).colorScheme.primary),
              const SizedBox(height: AppSpacing.xs),
              Text('Mis a jour le ${DateFormatting.dayTime(entry.recordedAt)}', style: AppTypography.caption),
            ],
          );
        },
      ),
    );
  }
}

class _LastOperationCard extends StatelessWidget {
  final AsyncValue<OrderHistoryEntry?> state;
  final VoidCallback onRetry;

  const _LastOperationCard({required this.state, required this.onRetry});

  @override
  Widget build(BuildContext context) {
    return state.when(
      loading: () => const LoadingView(),
      error: (message) => ErrorState(message: message, onRetry: onRetry),
      data: (entry) {
        if (entry == null) {
          return const EmptyState(icon: Icons.receipt_long_outlined, title: 'Aucune operation');
        }
        return InkWell(
          borderRadius: BorderRadius.circular(AppSpacing.radiusMd),
          onTap: () => context.go('/activity'),
          child: Container(
            padding: const EdgeInsets.all(AppSpacing.lg),
            decoration: BoxDecoration(
              color: Colors.white,
              borderRadius: BorderRadius.circular(AppSpacing.radiusMd),
              border: Border.all(color: AppColors.outline),
            ),
            child: Row(
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('#${entry.reference}', style: AppTypography.bodyStrong),
                      const SizedBox(height: AppSpacing.xs),
                      Text(
                        Money(entry.amountXof, AppCurrency.xof).formattedWithCurrency(),
                        style: AppTypography.caption,
                      ),
                      Text(DateFormatting.dayOnly(entry.createdAt), style: AppTypography.caption),
                    ],
                  ),
                ),
                StatusBadge(status: entry.status.code),
              ],
            ),
          ),
        );
      },
    );
  }
}

class _SuppliersPreview extends StatelessWidget {
  final AsyncValue<List<SupplierSummary>> state;
  final VoidCallback onRetry;

  const _SuppliersPreview({required this.state, required this.onRetry});

  @override
  Widget build(BuildContext context) {
    return state.when(
      loading: () => const LoadingView(),
      error: (message) => ErrorState(message: message, onRetry: onRetry),
      data: (suppliers) {
        if (suppliers.isEmpty) {
          return const EmptyState(
            icon: Icons.storefront_outlined,
            title: 'Aucun fournisseur',
            description: 'Ajoutez-en un pour payer plus vite.',
          );
        }
        return Column(
          children: suppliers
              .map(
                (supplier) => Padding(
                  padding: const EdgeInsets.only(bottom: AppSpacing.sm),
                  child: Container(
                    padding: const EdgeInsets.all(AppSpacing.md),
                    decoration: BoxDecoration(
                      color: Colors.white,
                      borderRadius: BorderRadius.circular(AppSpacing.radiusMd),
                      border: Border.all(color: AppColors.outline),
                    ),
                    child: Row(
                      children: [
                        CircleAvatar(
                          backgroundColor: AppColors.ivoryDim,
                          foregroundColor: Theme.of(context).colorScheme.primary,
                          child: Text(supplier.displayName.isEmpty ? '?' : supplier.displayName[0].toUpperCase()),
                        ),
                        const SizedBox(width: AppSpacing.md),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(supplier.displayName, style: AppTypography.bodyStrong),
                              Text(
                                '${supplier.type.label} · ${supplier.maskedAccountNumber}',
                                style: AppTypography.caption,
                              ),
                            ],
                          ),
                        ),
                        if (supplier.favorite) const Icon(Icons.star, color: AppColors.ochre, size: 18),
                      ],
                    ),
                  ),
                ),
              )
              .toList(growable: false),
        );
      },
    );
  }
}

/// "Mes gains" en un coup d'oeil (mission "differenciation marketing") : un
/// compteur sobre pour PRO, un badge/XP pour STUDENT_MALE/FEMALE — voir
/// [MyGainsPage] pour le detail complet.
class _AchievementsCard extends StatelessWidget {
  final AsyncValue<AchievementSummary> state;
  final ExperienceProfile profile;

  const _AchievementsCard({required this.state, required this.profile});

  @override
  Widget build(BuildContext context) {
    return state.when(
      loading: () => const SizedBox(height: 72, child: LoadingView()),
      // Purement indicatif sur Home : un echec ici ne doit jamais bloquer le
      // reste de l'ecran (voir MyGainsPage pour un retry dedie).
      error: (_) => const SizedBox.shrink(),
      data: (summary) => Pressable(
        onTap: () => context.push('/home/gains'),
        child: profile == ExperienceProfile.pro ? _proStrip(context, summary) : _studentStrip(context, summary),
      ),
    );
  }

  /// PRO : bande sobre "papier" — volume du mois + nombre de transferts qui
  /// s'incremente. Aucune fioriture (le serieux EST sa gamification).
  Widget _proStrip(BuildContext context, AchievementSummary summary) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(AppSpacing.lg),
      decoration: AppSurfaces.paper(),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('VOLUME CE MOIS-CI', style: AppTypography.eyebrow),
                const SizedBox(height: AppSpacing.xs),
                Text(
                  Money(summary.currentMonthAmountXofCompleted, AppCurrency.xof).formattedWithCurrency(),
                  style: AppTypography.figureMedium,
                ),
                const SizedBox(height: 2),
                CountUpText(
                  value: summary.completedTransferCount.toDouble(),
                  formatter: (v) => '${v.round()} transferts',
                  style: AppTypography.caption,
                ),
              ],
            ),
          ),
          const Icon(Icons.chevron_right, color: AppColors.inkMuted),
        ],
      ),
    );
  }

  /// STUDENT_* : bande "laque" — sceau du palier, XP qui grimpe, distance au
  /// palier suivant.
  Widget _studentStrip(BuildContext context, AchievementSummary summary) {
    return ClipRRect(
      borderRadius: BorderRadius.circular(AppSpacing.radiusMd),
      child: Container(
        width: double.infinity,
        padding: const EdgeInsets.all(AppSpacing.lg),
        decoration: AppSurfaces.lacquer(),
        child: Row(
          children: [
            RankSeal(profile: profile, initial: summary.badgeLabel, size: 46, locked: !summary.hasBadge),
            const SizedBox(width: AppSpacing.md),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    summary.badgeLabel ?? 'AUCUN PALIER',
                    style: AppTypography.eyebrow.copyWith(color: AppColors.keyline),
                  ),
                  const SizedBox(height: 2),
                  CountUpText(
                    value: summary.xp.toDouble(),
                    formatter: (v) => '${v.round()} XP',
                    style: AppTypography.figureMedium.copyWith(color: AppColors.onLacquer),
                  ),
                  if (summary.nextBadgeLabel != null) ...[
                    const SizedBox(height: 2),
                    Text(
                      '${summary.transfersUntilNextBadge} avant ${summary.nextBadgeLabel}',
                      style: AppTypography.caption.copyWith(color: AppColors.onLacquerMuted),
                    ),
                  ],
                ],
              ),
            ),
            const Icon(Icons.chevron_right, color: AppColors.onLacquerMuted),
          ],
        ),
      ),
    );
  }
}

/// Rappel discret et actionnable de la verification d'identite (remarque
/// produit #6) — visible tant que `kycVerified` est faux, disparait des que le
/// compte est verifie. Renvoie vers le parcours `/more/kyc`.
class _KycBanner extends StatelessWidget {
  final VoidCallback onTap;

  const _KycBanner({required this.onTap});

  @override
  Widget build(BuildContext context) {
    return Pressable(
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
              decoration: BoxDecoration(
                color: AppColors.ochre.withValues(alpha: 0.14),
                shape: BoxShape.circle,
              ),
              child: const Icon(Icons.verified_user_outlined, color: AppColors.ochre, size: 20),
            ),
            const SizedBox(width: AppSpacing.md),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text("Verifiez votre identite", style: AppTypography.bodyStrong),
                  const SizedBox(height: 2),
                  Text('Trois photos, deux minutes.', style: AppTypography.caption),
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
