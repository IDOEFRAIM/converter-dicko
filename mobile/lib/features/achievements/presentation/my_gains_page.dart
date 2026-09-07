import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../../core/auth/auth_session.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../core/theme/app_typography.dart';
import '../../../core/theme/experience_theme.dart';
import '../../../shared/models/current_user.dart';
import '../../../shared/models/money.dart';
import '../../../shared/utils/date_formatting.dart';
import '../../../shared/utils/file_share.dart';
import '../../../shared/widgets/empty_state.dart';
import '../../../shared/widgets/error_state.dart';
import '../../../shared/widgets/loading_view.dart';
import '../../../shared/widgets/status_badge.dart';
import '../../orders/data/order_api.dart';
import '../../orders/models/order_models.dart';
import '../application/my_gains_controller.dart';
import '../application/pro_activity_report.dart';
import '../data/achievement_api.dart';

/// "Mes gains" (mission "differenciation marketing", Lot 2) : un compteur
/// sobre pour PRO, un badge + XP pour STUDENT_MALE/FEMALE — memes donnees
/// reelles (volume transfere, transferts termines), jamais un chiffre
/// invente ni presente comme une "economie" vs un concurrent (voir
/// AchievementSummary).
class MyGainsPage extends StatelessWidget {
  const MyGainsPage({super.key});

  @override
  Widget build(BuildContext context) {
    return ChangeNotifierProvider(
      create: (context) => MyGainsController(
        achievementApi: context.read<AchievementApi>(),
        orderApi: context.read<OrderApi>(),
      )..load(),
      child: const _MyGainsView(),
    );
  }
}

class _MyGainsView extends StatelessWidget {
  const _MyGainsView();

  @override
  Widget build(BuildContext context) {
    final controller = context.watch<MyGainsController>();
    final profile = context.watch<AuthSession>().experienceProfile;

    return Scaffold(
      appBar: AppBar(title: const Text('Mes gains')),
      body: SafeArea(
        child: RefreshIndicator(
          onRefresh: controller.load,
          color: Theme.of(context).colorScheme.primary,
          child: ListView(
            padding: const EdgeInsets.all(AppSpacing.lg),
            children: [
              _SummaryHero(controller: controller, profile: profile),
              const SizedBox(height: AppSpacing.xl),
              Text(_historyTitle(profile), style: AppTypography.eyebrow),
              const SizedBox(height: AppSpacing.sm),
              _HistorySection(controller: controller, profile: profile),
            ],
          ),
        ),
      ),
    );
  }

  String _historyTitle(ExperienceProfile profile) => switch (profile) {
        ExperienceProfile.pro => 'HISTORIQUE',
        ExperienceProfile.studentMale => 'LIVRE DES GAINS',
        ExperienceProfile.studentFemale => 'CARNET DE ROUTE',
      };
}

class _SummaryHero extends StatelessWidget {
  final MyGainsController controller;
  final ExperienceProfile profile;

  const _SummaryHero({required this.controller, required this.profile});

  Future<void> _exportPdf(BuildContext context) async {
    final summary = controller.summary;
    if (summary == null) return;
    final fullName = context.read<AuthSession>().currentUser?.fullName ?? 'Client';
    try {
      final bytes = await buildProActivityReportPdf(
        fullName: fullName,
        summary: summary,
        completedTransfers: controller.completedTransfers,
      );
      await saveAndShareBytes(bytes: bytes, fileName: 'rapport-activite.pdf');
    } catch (_) {
      // Generation locale (calcul de mise en page) ou feuille de partage native
      // peuvent echouer sans lien avec les donnees, deja chargees avec succes —
      // ne jamais laisser ce cas paraitre comme un simple "rien ne s'est passe"
      // (meme discipline que le telechargement de justificatif, order_detail_page.dart).
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text("Impossible de generer ou de partager le rapport. Reessayez.")),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    if (controller.loadingSummary) {
      return const LoadingView();
    }
    final summary = controller.summary;
    if (summary == null) {
      return ErrorState(
        message: controller.summaryErrorMessage ?? 'Impossible de charger vos gains.',
        onRetry: controller.loadSummary,
      );
    }

    if (profile == ExperienceProfile.pro) {
      return Container(
        width: double.infinity,
        padding: const EdgeInsets.all(AppSpacing.lg),
        decoration: BoxDecoration(
          color: Colors.white,
          border: Border.all(color: AppColors.outline),
          borderRadius: BorderRadius.circular(AppSpacing.radiusMd),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('VOLUME TRANSFERE CE MOIS-CI', style: AppTypography.eyebrow),
            const SizedBox(height: AppSpacing.xs),
            Text(
              Money(summary.currentMonthAmountXofCompleted, AppCurrency.xof).formattedWithCurrency(),
              style: AppTypography.metricLarge,
            ),
            const SizedBox(height: AppSpacing.sm),
            Text(
              '${summary.completedTransferCount} transfert(s) termine(s) au total.',
              style: AppTypography.caption,
            ),
            const SizedBox(height: AppSpacing.md),
            OutlinedButton.icon(
              onPressed: () => _exportPdf(context),
              icon: const Icon(Icons.picture_as_pdf_outlined),
              label: const Text('Exporter le rapport (PDF)'),
            ),
          ],
        ),
      );
    }

    final gradient = ExperiencePalette.gradientFor(profile);
    final foreground = gradient.foreground;
    final badgeLabel = summary.badgeLabel;
    final subtitle = profile == ExperienceProfile.studentMale
        ? 'Chaque transfert termine te rapproche du prochain rang.'
        : 'Chaque transfert termine ecrit une nouvelle page de ton carnet.';

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(AppSpacing.lg),
      decoration: BoxDecoration(gradient: gradient.gradient, borderRadius: BorderRadius.circular(AppSpacing.radiusMd)),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            badgeLabel == null ? 'AUCUN BADGE ENCORE' : 'TON RANG',
            style: AppTypography.eyebrow.copyWith(color: foreground.withValues(alpha: 0.85)),
          ),
          const SizedBox(height: AppSpacing.xs),
          Text(
            badgeLabel ?? 'A toi de jouer',
            style: AppTypography.metricLarge.copyWith(color: foreground),
          ),
          const SizedBox(height: AppSpacing.sm),
          Text('${summary.xp} XP', style: AppTypography.bodyStrong.copyWith(color: foreground)),
          const SizedBox(height: AppSpacing.xs),
          Text(subtitle, style: AppTypography.caption.copyWith(color: foreground.withValues(alpha: 0.85))),
          if (summary.nextBadgeLabel != null) ...[
            const SizedBox(height: AppSpacing.sm),
            Text(
              'Encore ${summary.transfersUntilNextBadge} transfert(s) pour devenir ${summary.nextBadgeLabel} !',
              style: AppTypography.caption.copyWith(color: foreground, fontWeight: FontWeight.w700),
            ),
          ],
          if (summary.poolsSucceededCount > 0) ...[
            const SizedBox(height: AppSpacing.sm),
            Text(
              '🏆 ${summary.poolsSucceededCount} Ruee(s) collective(s) remportee(s)',
              style: AppTypography.caption.copyWith(color: foreground, fontWeight: FontWeight.w700),
            ),
          ],
        ],
      ),
    );
  }
}

class _HistorySection extends StatelessWidget {
  final MyGainsController controller;
  final ExperienceProfile profile;

  const _HistorySection({required this.controller, required this.profile});

  @override
  Widget build(BuildContext context) {
    if (controller.loadingHistory) {
      return const LoadingView();
    }
    if (controller.historyErrorMessage != null && controller.completedTransfers.isEmpty) {
      return ErrorState(message: controller.historyErrorMessage!, onRetry: controller.loadHistory);
    }
    final entries = controller.completedTransfers;
    if (entries.isEmpty) {
      return const EmptyState(
        icon: Icons.emoji_events_outlined,
        title: 'Aucun transfert termine',
        description: 'Vos transferts termines apparaitront ici.',
      );
    }

    if (profile == ExperienceProfile.pro) {
      return Column(children: entries.map((entry) => _HistoryTile(entry: entry)).toList(growable: false));
    }

    // "Livre des Gains"/"Carnet de route" (STUDENT_MALE/FEMALE) : chaque
    // transfert termine devient une "page" numerotee — la plus recente porte
    // le numero le plus eleve (derniere page ecrite), jamais un renversement
    // de l'ordre reel deja renvoye par le backend (entries[0] = le plus recent).
    final total = entries.length;
    return Column(
      children: [
        for (var i = 0; i < entries.length; i++)
          _AlbumPageTile(entry: entries[i], pageNumber: total - i, profile: profile),
      ],
    );
  }
}

class _HistoryTile extends StatelessWidget {
  final OrderHistoryEntry entry;

  const _HistoryTile({required this.entry});

  @override
  Widget build(BuildContext context) {
    return InkWell(
      borderRadius: BorderRadius.circular(AppSpacing.radiusMd),
      onTap: () => context.push('/activity/orders/${entry.id}'),
      child: Container(
        margin: const EdgeInsets.only(bottom: AppSpacing.sm),
        padding: const EdgeInsets.all(AppSpacing.md),
        decoration: BoxDecoration(
          color: Colors.white,
          border: Border.all(color: AppColors.outline),
          borderRadius: BorderRadius.circular(AppSpacing.radiusMd),
        ),
        child: Row(
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('#${entry.reference}', style: AppTypography.bodyStrong),
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
  }
}

/// "Page" illustree du Livre des Gains/Carnet de route (mission
/// "differenciation marketing" : "Album illustre, chronologie avec photos et
/// montants" / "Carnet de route illustre avec citations et emotions") —
/// aucune veritable photo/illustration (aucun asset graphique fourni), la
/// mise en recit vient du degrade de marque, d'une icone symbolique et d'une
/// legende courte ; le montant/la reference/la date restent des donnees
/// reelles inchangees, jamais une invention.
class _AlbumPageTile extends StatelessWidget {
  final OrderHistoryEntry entry;
  final int pageNumber;
  final ExperienceProfile profile;

  const _AlbumPageTile({required this.entry, required this.pageNumber, required this.profile});

  @override
  Widget build(BuildContext context) {
    final gradient = ExperiencePalette.gradientFor(profile);
    final icon = profile == ExperienceProfile.studentMale ? Icons.military_tech_outlined : Icons.favorite_outline;
    final caption = profile == ExperienceProfile.studentMale
        ? 'Un nouveau trophee ajoute a ta legende.'
        : 'Une nouvelle page de ton carnet de route.';

    return InkWell(
      borderRadius: BorderRadius.circular(AppSpacing.radiusMd),
      onTap: () => context.push('/activity/orders/${entry.id}'),
      child: Container(
        margin: const EdgeInsets.only(bottom: AppSpacing.sm),
        padding: const EdgeInsets.all(AppSpacing.md),
        decoration: BoxDecoration(
          color: Colors.white,
          border: Border.all(color: AppColors.outline),
          borderRadius: BorderRadius.circular(AppSpacing.radiusMd),
          boxShadow: [BoxShadow(color: Colors.black.withValues(alpha: 0.05), blurRadius: 8, offset: const Offset(0, 2))],
        ),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Container(
              width: 44,
              height: 44,
              decoration: BoxDecoration(gradient: gradient.gradient, shape: BoxShape.circle),
              child: Icon(icon, color: gradient.foreground, size: 20),
            ),
            const SizedBox(width: AppSpacing.md),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('PAGE $pageNumber', style: AppTypography.eyebrow),
                  const SizedBox(height: 2),
                  Text(
                    Money(entry.amountXof, AppCurrency.xof).formattedWithCurrency(),
                    style: AppTypography.bodyStrong,
                  ),
                  const SizedBox(height: 2),
                  Text(caption, style: AppTypography.caption),
                  Text(DateFormatting.dayOnly(entry.createdAt), style: AppTypography.caption),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
