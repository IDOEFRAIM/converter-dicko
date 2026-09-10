import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../core/theme/app_surfaces.dart';
import '../../../core/theme/app_typography.dart';
import '../../../shared/models/money.dart';
import '../../../shared/utils/date_formatting.dart';
import '../../../shared/widgets/empty_state.dart';
import '../../../shared/widgets/error_state.dart';
import '../../../shared/widgets/loading_view.dart';
import '../../preferred_rate/data/preferred_rate_api.dart';
import '../application/wallet_controller.dart';
import '../data/wallet_api.dart';
import '../models/wallet_models.dart';

/// "Portefeuille" : solde interne XOF du client sur la plateforme
/// (disponible / reserve / total) et historique des mouvements du ledger.
///
/// Lecture seule — aucun rechargement cote client (meme perimetre que le
/// frontend web). Le solde est alimente/immobilise par le backend : demande
/// de taux preferentiel (RESERVE a la creation, DEBIT au declenchement,
/// RELEASE a l'annulation ou l'expiration).
class WalletPage extends StatelessWidget {
  const WalletPage({super.key});

  @override
  Widget build(BuildContext context) {
    return ChangeNotifierProvider(
      create: (context) => WalletController(
        context.read<WalletApi>(),
        context.read<PreferredRateApi>(),
      )..load(),
      child: const _WalletView(),
    );
  }
}

class _WalletView extends StatelessWidget {
  const _WalletView();

  @override
  Widget build(BuildContext context) {
    final controller = context.watch<WalletController>();

    return Scaffold(
      appBar: AppBar(title: const Text('Portefeuille')),
      body: SafeArea(
        child: RefreshIndicator(
          onRefresh: controller.load,
          color: Theme.of(context).colorScheme.primary,
          child: ListView(
            padding: const EdgeInsets.all(AppSpacing.lg),
            children: [
              Text(
                'Votre solde interne en XOF sur la plateforme. Il alimente vos demandes de taux preferentiel.',
                style: AppTypography.caption,
              ),
              const SizedBox(height: AppSpacing.lg),
              if (controller.wallet != null) _BalanceCard(wallet: controller.wallet!),
              const SizedBox(height: AppSpacing.xl),
              Text('HISTORIQUE DES MOUVEMENTS', style: AppTypography.eyebrow),
              const SizedBox(height: AppSpacing.md),
              _history(controller),
            ],
          ),
        ),
      ),
    );
  }

  Widget _history(WalletController controller) {
    if (controller.loading) {
      return const LoadingView();
    }
    if (controller.errorMessage != null && controller.transactions.isEmpty) {
      return ErrorState(message: controller.errorMessage!, onRetry: controller.load);
    }
    if (controller.transactions.isEmpty) {
      return const EmptyState(
        icon: Icons.swap_horiz,
        title: 'Aucun mouvement',
        description: 'Les depots, reservations et debits apparaitront ici.',
      );
    }
    return Column(
      children: [
        for (final tx in controller.transactions)
          _TxRow(tx: tx, engaged: controller.isEngaged(tx)),
      ],
    );
  }
}

class _BalanceCard extends StatelessWidget {
  final Wallet wallet;

  const _BalanceCard({required this.wallet});

  @override
  Widget build(BuildContext context) {
    final reserved = Money.xof(wallet.reservedBalance);
    final showReservedHint = reserved.formatted() != Money.xof('0').formatted();

    return Container(
      padding: const EdgeInsets.all(AppSpacing.lg),
      decoration: AppSurfaces.lacquer(),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'SOLDE DISPONIBLE',
            style: AppTypography.eyebrow.copyWith(color: AppColors.onLacquerMuted),
          ),
          const SizedBox(height: AppSpacing.sm),
          Text(
            Money.xof(wallet.available).formattedWithCurrency(),
            style: AppTypography.metricLarge.copyWith(color: AppColors.onLacquer),
          ),
          const SizedBox(height: AppSpacing.lg),
          Container(height: 1, color: AppColors.keyline.withValues(alpha: 0.25)),
          const SizedBox(height: AppSpacing.lg),
          Row(
            children: [
              Expanded(child: _MiniStat(label: 'Solde total', value: wallet.balance)),
              Expanded(child: _MiniStat(label: 'Reserve', value: wallet.reservedBalance)),
            ],
          ),
          if (showReservedHint) ...[
            const SizedBox(height: AppSpacing.md),
            Text(
              'Le solde reserve est immobilise par vos demandes de taux preferentiel en cours.',
              style: AppTypography.caption.copyWith(color: AppColors.onLacquerMuted),
            ),
          ],
        ],
      ),
    );
  }
}

class _MiniStat extends StatelessWidget {
  final String label;
  final String value;

  const _MiniStat({required this.label, required this.value});

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label.toUpperCase(), style: AppTypography.eyebrow.copyWith(color: AppColors.onLacquerMuted)),
        const SizedBox(height: AppSpacing.xs),
        Text(
          Money.xof(value).formattedWithCurrency(),
          style: AppTypography.figureMedium.copyWith(color: AppColors.onLacquer),
        ),
      ],
    );
  }
}

class _TxRow extends StatelessWidget {
  final WalletTransaction tx;
  final bool engaged;

  const _TxRow({required this.tx, required this.engaged});

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: AppSpacing.sm),
      padding: const EdgeInsets.all(AppSpacing.md),
      decoration: AppSurfaces.paper(raised: false),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(_icon, size: 18, color: _color),
          const SizedBox(width: AppSpacing.sm),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(_title, style: AppTypography.bodyStrong),
                if (_subtitle != null) ...[
                  const SizedBox(height: AppSpacing.xs),
                  Text(_subtitle!, style: AppTypography.caption),
                ],
                const SizedBox(height: AppSpacing.xs),
                Text(
                  DateFormatting.dayTime(tx.createdAt),
                  style: AppTypography.caption.copyWith(color: AppColors.inkFaint),
                ),
              ],
            ),
          ),
          const SizedBox(width: AppSpacing.md),
          Column(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Text(
                '$_prefix${Money.xof(tx.amount).formattedWithCurrency()}',
                style: AppTypography.figureMedium.copyWith(color: _color),
              ),
              const SizedBox(height: AppSpacing.xs),
              Text(
                'Solde ${Money.xof(tx.balanceAfter).formattedWithCurrency()}',
                style: AppTypography.figureSmall,
              ),
            ],
          ),
        ],
      ),
    );
  }

  String get _title => engaged ? 'Engage pour un echange en cours' : tx.type.label;

  String? get _subtitle {
    if (engaged) {
      return 'Echange declenche, pas encore termine.';
    }
    return tx.reason;
  }

  String get _prefix => switch (tx.type) {
        WalletTransactionType.credit => '+ ',
        WalletTransactionType.debit => '− ',
        _ => '',
      };

  Color get _color => switch (tx.type) {
        WalletTransactionType.credit => AppColors.positive,
        WalletTransactionType.debit => AppColors.negative,
        _ => AppColors.inkMuted,
      };

  IconData get _icon => switch (tx.type) {
        WalletTransactionType.credit => Icons.south_west,
        WalletTransactionType.debit => engaged ? Icons.hourglass_top : Icons.north_east,
        WalletTransactionType.reserve => Icons.lock_outline,
        WalletTransactionType.release => Icons.lock_open_outlined,
        WalletTransactionType.unknown => Icons.swap_horiz,
      };
}
