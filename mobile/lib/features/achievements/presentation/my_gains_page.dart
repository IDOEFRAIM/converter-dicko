import 'dart:io';

import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../../core/auth/auth_session.dart';
import '../../../core/storage/memory_book_store.dart';
import '../../../core/storage/pro_objective_store.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../core/theme/app_surfaces.dart';
import '../../../core/theme/app_typography.dart';
import '../../../core/theme/experience_theme.dart';
import '../../../shared/models/current_user.dart';
import '../../../shared/models/money.dart';
import '../../../shared/utils/date_formatting.dart';
import '../../../shared/utils/file_share.dart';
import '../../../shared/widgets/count_up_text.dart';
import '../../../shared/widgets/empty_state.dart';
import '../../../shared/widgets/error_state.dart';
import '../../../shared/widgets/grain.dart';
import '../../../shared/widgets/loading_view.dart';
import '../../../shared/widgets/objective_dial.dart';
import '../../../shared/widgets/pressable.dart';
import '../../../shared/widgets/rank_seal.dart';
import '../../../shared/widgets/status_badge.dart';
import '../../../shared/widgets/xp_meridian.dart';
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
        memoryBook: context.read<MemoryBookStore>(),
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
        ExperienceProfile.studentMale => 'REGISTRE DES OPERATIONS',
        ExperienceProfile.studentFemale => 'REGISTRE DES OPERATIONS',
      };
}

class _SummaryHero extends StatelessWidget {
  final MyGainsController controller;
  final ExperienceProfile profile;

  const _SummaryHero({required this.controller, required this.profile});

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
      return _ProConsole(controller: controller);
    }

    const subtitle = 'Chaque operation terminee te rapproche du palier suivant.';

    return ClipRRect(
      borderRadius: BorderRadius.circular(AppSpacing.radiusMd),
      child: Container(
        width: double.infinity,
        padding: const EdgeInsets.fromLTRB(AppSpacing.lg, AppSpacing.xl, AppSpacing.lg, AppSpacing.lg),
        decoration: AppSurfaces.lacquer(),
        child: Stack(
          children: [
            const Positioned.fill(child: LedgerGrain(opacity: 0.06)),
            Column(
              children: [
                Text(
                  summary.hasBadge ? 'TON PALIER' : 'AUCUN PALIER ENCORE',
                  style: AppTypography.eyebrow.copyWith(color: AppColors.keyline),
                ),
                const SizedBox(height: AppSpacing.md),
                XpMeridian(
                  progress: summary.tierProgress,
                  track: AppColors.onLacquer.withValues(alpha: 0.14),
                  fill: AppColors.keyline,
                  size: 196,
                  center: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      RankSealBadge(
                        profile: profile,
                        initial: summary.badgeLabel,
                        size: 96,
                        locked: !summary.hasBadge,
                      ),
                      const SizedBox(height: AppSpacing.sm),
                      CountUpText(
                        value: summary.xp.toDouble(),
                        formatter: (v) => '${v.round()} XP',
                        style: AppTypography.figureMedium.copyWith(color: AppColors.onLacquer),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: AppSpacing.sm),
                Text(
                  summary.badgeLabel ?? 'Pas encore de palier',
                  textAlign: TextAlign.center,
                  style: AppTypography.metricMedium.copyWith(color: AppColors.onLacquer),
                ),
                const SizedBox(height: AppSpacing.xs),
                Text(
                  subtitle,
                  textAlign: TextAlign.center,
                  style: AppTypography.caption.copyWith(color: AppColors.onLacquerMuted),
                ),
                if (summary.nextBadgeLabel != null) ...[
                  const SizedBox(height: AppSpacing.sm),
                  Text(
                    'Encore ${summary.transfersUntilNextBadge} operation(s) pour le palier ${summary.nextBadgeLabel}',
                    textAlign: TextAlign.center,
                    style: AppTypography.caption.copyWith(color: AppColors.keyline, fontWeight: FontWeight.w800),
                  ),
                ],
                if (summary.poolsSucceededCount > 0) ...[
                  const SizedBox(height: AppSpacing.sm),
                  Text(
                    '${summary.poolsSucceededCount} Ruee(s) collective(s) reussie(s)',
                    textAlign: TextAlign.center,
                    style: AppTypography.caption.copyWith(color: AppColors.onLacquer, fontWeight: FontWeight.w700),
                  ),
                ],
                const SizedBox(height: AppSpacing.md),
                Divider(color: AppColors.onLacquer.withValues(alpha: 0.12), height: 1),
                const SizedBox(height: AppSpacing.md),
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text('VOLUME TRANSFERE', style: AppTypography.eyebrow.copyWith(color: AppColors.onLacquerMuted)),
                    Text(
                      Money(summary.totalAmountXofCompleted, AppCurrency.xof).formattedWithCurrency(),
                      style: AppTypography.figureSmall.copyWith(color: AppColors.onLacquer),
                    ),
                  ],
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

/// Console d'operateur PRO (langage de design "Le Comptoir", Lot D) : le
/// serieux *est* la gamification. Un cadran d'objectif mensuel (l'objectif est
/// saisi par l'utilisateur, jamais impose ni invente), une serie de mois
/// actifs derivee de l'historique reel, et l'export du rapport PDF. Surface
/// "papier" sobre — aucun sceau, aucune laque.
class _ProConsole extends StatefulWidget {
  final MyGainsController controller;

  const _ProConsole({required this.controller});

  @override
  State<_ProConsole> createState() => _ProConsoleState();
}

class _ProConsoleState extends State<_ProConsole> {
  static const _store = ProObjectiveStore();

  int? _objective;
  bool _loadingObjective = true;

  @override
  void initState() {
    super.initState();
    _store.read().then((value) {
      if (!mounted) return;
      setState(() {
        _objective = value;
        _loadingObjective = false;
      });
    });
  }

  Future<void> _editObjective() async {
    final result = await showDialog<int>(
      context: context,
      builder: (_) => _ObjectiveDialog(initial: _objective),
    );
    if (result == null) return; // annule
    await _store.write(result);
    if (!mounted) return;
    setState(() => _objective = result > 0 ? result : null);
  }

  Future<void> _exportPdf() async {
    final summary = widget.controller.summary;
    if (summary == null) return;
    final fullName = context.read<AuthSession>().currentUser?.fullName ?? 'Client';
    try {
      final bytes = await buildProActivityReportPdf(
        fullName: fullName,
        summary: summary,
        completedTransfers: widget.controller.completedTransfers,
      );
      await saveAndShareBytes(bytes: bytes, fileName: 'rapport-activite.pdf');
    } catch (_) {
      // Generation locale (mise en page) ou feuille de partage native peuvent
      // echouer sans lien avec les donnees, deja chargees avec succes — ne
      // jamais laisser ce cas paraitre comme un simple "rien ne s'est passe"
      // (meme discipline que le telechargement de justificatif).
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text("Impossible de generer ou de partager le rapport. Reessayez.")),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final controller = widget.controller;
    final summary = controller.summary!; // loading/erreur deja geres par _SummaryHero
    final ratio = _objective == null
        ? null
        : _fillRatio(summary.currentMonthAmountXofCompleted, _objective!);
    final streak = _streak(controller);

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(AppSpacing.lg),
      decoration: AppSurfaces.paper(),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Icon(Icons.speed_outlined, size: 16, color: AppColors.inkMuted),
              const SizedBox(width: AppSpacing.xs),
              Text('CONSOLE — VOLUME DU MOIS', style: AppTypography.eyebrow),
            ],
          ),
          const SizedBox(height: AppSpacing.md),
          Center(
            child: ObjectiveDial(
              progress: ratio,
              track: AppColors.outline,
              fill: AppColors.keyline,
              tick: AppColors.inkFaint,
              needle: AppColors.navy,
              size: 208,
              center: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  CountUpText(
                    value: _intXof(summary.currentMonthAmountXofCompleted).toDouble(),
                    formatter: (v) =>
                        Money(v.round().toString(), AppCurrency.xof).formattedWithCurrency(),
                    style: AppTypography.figureMedium,
                    textAlign: TextAlign.center,
                  ),
                  const SizedBox(height: 2),
                  Text('ce mois-ci', style: AppTypography.caption),
                ],
              ),
            ),
          ),
          const SizedBox(height: AppSpacing.sm),
          Center(child: _objectiveLine(ratio)),
          const SizedBox(height: AppSpacing.lg),
          Row(
            children: [
              Expanded(child: _instrument('SERIE', streak.label, streak.caption)),
              const SizedBox(width: AppSpacing.md),
              Expanded(
                child: _instrument(
                  'TRANSFERTS',
                  '${summary.completedTransferCount}',
                  'termines au total',
                ),
              ),
            ],
          ),
          const SizedBox(height: AppSpacing.lg),
          const Divider(color: AppColors.outline, height: 1),
          const SizedBox(height: AppSpacing.md),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text('VOLUME TOTAL', style: AppTypography.eyebrow),
              Text(
                Money(summary.totalAmountXofCompleted, AppCurrency.xof).formattedWithCurrency(),
                style: AppTypography.figureSmall.copyWith(color: AppColors.ink),
              ),
            ],
          ),
          const SizedBox(height: AppSpacing.md),
          SizedBox(
            width: double.infinity,
            child: OutlinedButton.icon(
              onPressed: _exportPdf,
              icon: const Icon(Icons.picture_as_pdf_outlined, size: 18),
              label: const Text('Exporter le rapport (PDF)'),
            ),
          ),
        ],
      ),
    );
  }

  Widget _objectiveLine(double? ratio) {
    if (_loadingObjective) {
      return Text('…', style: AppTypography.caption);
    }
    if (_objective == null) {
      return TextButton.icon(
        onPressed: _editObjective,
        icon: const Icon(Icons.add_chart_outlined, size: 16),
        label: const Text('Definir un objectif mensuel'),
      );
    }
    final pct = ((ratio ?? 0) * 100).round();
    return Wrap(
      alignment: WrapAlignment.center,
      crossAxisAlignment: WrapCrossAlignment.center,
      children: [
        Text(
          'Objectif ${Money(_objective!.toString(), AppCurrency.xof).formattedWithCurrency()}  ·  $pct %',
          style: AppTypography.caption.copyWith(fontWeight: FontWeight.w700),
        ),
        const SizedBox(width: AppSpacing.sm),
        InkWell(
          onTap: _editObjective,
          child: Text(
            'Modifier',
            style: AppTypography.caption.copyWith(color: AppColors.navy, fontWeight: FontWeight.w800),
          ),
        ),
      ],
    );
  }

  Widget _instrument(String label, String value, String caption) {
    return Container(
      padding: const EdgeInsets.all(AppSpacing.md),
      decoration: BoxDecoration(
        border: Border.all(color: AppColors.outline),
        borderRadius: BorderRadius.circular(AppSpacing.radiusSm),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label, style: AppTypography.eyebrow),
          const SizedBox(height: AppSpacing.xs),
          Text(value, style: AppTypography.figureMedium),
          const SizedBox(height: 2),
          Text(caption, style: AppTypography.caption),
        ],
      ),
    );
  }

  /// Serie = nombre de mois calendaires consecutifs avec au moins un transfert
  /// termine, ancree sur le mois courant (ou le mois precedent s'il n'y a pas
  /// encore d'activite ce mois-ci). Derivee de l'historique reel deja charge
  /// (`completedTransfers`, plafonne a 20) : c'est donc un plancher honnete,
  /// jamais un compteur invente.
  _Streak _streak(MyGainsController controller) {
    if (controller.loadingHistory) return const _Streak('…', 'calcul en cours');
    final entries = controller.completedTransfers;
    if (entries.isEmpty) return const _Streak('0', 'relancez une serie');

    final active = <int>{};
    for (final entry in entries) {
      final d = entry.createdAt.toUtc();
      active.add(d.year * 12 + (d.month - 1));
    }

    final nowUtc = DateTime.now().toUtc();
    final currentKey = nowUtc.year * 12 + (nowUtc.month - 1);

    var anchor = currentKey;
    var securedThisMonth = true;
    if (!active.contains(anchor)) {
      securedThisMonth = false;
      if (active.contains(anchor - 1)) {
        anchor -= 1;
      } else {
        return const _Streak('0', 'aucun transfert ce mois-ci');
      }
    }

    var count = 0;
    for (var k = anchor; active.contains(k); k--) {
      count++;
    }
    return _Streak('$count', securedThisMonth ? "mois d'affilee" : "mois · a confirmer");
  }

  int _intXof(String decimal) {
    final dot = decimal.indexOf('.');
    final intPart = dot == -1 ? decimal : decimal.substring(0, dot);
    return int.tryParse(intPart.trim()) ?? 0;
  }

  /// Fraction de remplissage du cadran — une proportion d'affichage, jamais un
  /// montant : volume du mois / objectif, en arithmetique entiere sur la
  /// partie entiere XOF (le XOF n'a pas de sous-unite). Le `double` obtenu ne
  /// sert qu'a l'angle de l'aiguille, il n'est ni stocke ni compare comme une
  /// valeur financiere.
  double _fillRatio(String monthDecimal, int objectiveXof) {
    if (objectiveXof <= 0) return 0;
    return (_intXof(monthDecimal) / objectiveXof).clamp(0.0, 1.0).toDouble();
  }
}

class _Streak {
  final String label;
  final String caption;

  const _Streak(this.label, this.caption);
}

/// Saisie de l'objectif mensuel du PRO — un entier XOF strictement positif.
/// "Retirer" (present seulement si un objectif existe deja) renvoie `0`, que
/// `_ProConsoleState` interprete comme un effacement.
class _ObjectiveDialog extends StatefulWidget {
  final int? initial;

  const _ObjectiveDialog({required this.initial});

  @override
  State<_ObjectiveDialog> createState() => _ObjectiveDialogState();
}

class _ObjectiveDialogState extends State<_ObjectiveDialog> {
  final _formKey = GlobalKey<FormState>();
  late final TextEditingController _controller =
      TextEditingController(text: widget.initial?.toString() ?? '');

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _submit() {
    if (!_formKey.currentState!.validate()) return;
    Navigator.of(context).pop(int.parse(_controller.text.trim()));
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Objectif mensuel'),
      content: Form(
        key: _formKey,
        autovalidateMode: AutovalidateMode.onUserInteraction,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Le volume transfere que vous visez ce mois-ci. Sert uniquement de '
              "repere sur votre cadran — il n'affecte aucun tarif.",
            ),
            const SizedBox(height: AppSpacing.md),
            TextFormField(
              controller: _controller,
              keyboardType: TextInputType.number,
              decoration: const InputDecoration(labelText: 'Objectif', suffixText: 'XOF'),
              validator: (value) {
                final n = int.tryParse((value ?? '').trim());
                if (n == null || n <= 0) return 'Entrez un montant en XOF (entier positif).';
                return null;
              },
            ),
          ],
        ),
      ),
      actions: [
        if (widget.initial != null)
          TextButton(
            onPressed: () => Navigator.of(context).pop(0),
            child: const Text('Retirer'),
          ),
        TextButton(onPressed: () => Navigator.of(context).pop(), child: const Text('Annuler')),
        TextButton(onPressed: _submit, child: const Text('Enregistrer')),
      ],
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

    // "Registre des operations" (profils non-PRO) : chaque transfert termine
    // devient une operation numerotee — la plus recente porte le numero le plus
    // eleve (derniere ecrite au registre), jamais un renversement de l'ordre
    // reel deja renvoye par le backend (entries[0] = le plus recent).
    final total = entries.length;
    return Column(
      children: [
        for (var i = 0; i < entries.length; i++)
          _AlbumPageTile(
            entry: entries[i],
            pageNumber: total - i,
            profile: profile,
            selfiePath: controller.memories[entries[i].id],
          ),
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

/// Une entree du "Registre des operations" : chaque transfert termine, numerote,
/// avec l'accent de marque du profil. Aucun asset graphique — la presentation
/// vient du degrade, d'une icone sobre et d'une legende courte ; le montant, la
/// reference et la date restent des donnees reelles inchangees.
class _AlbumPageTile extends StatelessWidget {
  final OrderHistoryEntry entry;
  final int pageNumber;
  final ExperienceProfile profile;

  /// Chemin local du selfie souvenir de cette operation, s'il existe (#5).
  final String? selfiePath;

  const _AlbumPageTile({
    required this.entry,
    required this.pageNumber,
    required this.profile,
    this.selfiePath,
  });

  @override
  Widget build(BuildContext context) {
    final gradient = ExperiencePalette.gradientFor(profile);
    const icon = Icons.receipt_long_outlined;
    const caption = 'Une operation de plus a ton registre.';

    final Widget leading = selfiePath != null
        ? ClipRRect(
            borderRadius: BorderRadius.circular(AppSpacing.radiusSm),
            child: Container(
              width: 44,
              height: 52,
              decoration: BoxDecoration(border: Border.all(color: AppColors.keyline.withValues(alpha: 0.6))),
              child: Image.file(
                File(selfiePath!),
                fit: BoxFit.cover,
                cacheWidth: 120,
                errorBuilder: (context, error, stackTrace) =>
                    const ColoredBox(color: AppColors.ivoryDim, child: Icon(Icons.image_outlined, size: 18)),
              ),
            ),
          )
        : Container(
            width: 44,
            height: 44,
            decoration: BoxDecoration(gradient: gradient.gradient, shape: BoxShape.circle),
            child: Icon(icon, color: gradient.foreground, size: 20),
          );

    return Pressable(
      onTap: () => context.push('/activity/orders/${entry.id}'),
      child: Container(
        margin: const EdgeInsets.only(bottom: AppSpacing.sm),
        padding: const EdgeInsets.all(AppSpacing.md),
        decoration: AppSurfaces.paper(),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            leading,
            const SizedBox(width: AppSpacing.md),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('OPERATION $pageNumber', style: AppTypography.eyebrow),
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
