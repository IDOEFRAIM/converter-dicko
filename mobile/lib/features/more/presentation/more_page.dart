import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../../core/auth/auth_session.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../core/theme/app_typography.dart';
import '../../auth/data/auth_repository.dart';

/// Menu "Plus" : fonctionnalites secondaires (mission section 19) — Taux,
/// Alertes, Wallet, Notifications, Business, Profil — plus la deconnexion.
class MorePage extends StatelessWidget {
  const MorePage({super.key});

  @override
  Widget build(BuildContext context) {
    final user = context.watch<AuthSession>().currentUser;

    return Scaffold(
      appBar: AppBar(title: const Text('Plus')),
      body: ListView(
        padding: const EdgeInsets.symmetric(vertical: AppSpacing.md),
        children: [
          if (user != null)
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: AppSpacing.lg, vertical: AppSpacing.md),
              child: Row(
                children: [
                  CircleAvatar(
                    radius: 22,
                    backgroundColor: AppColors.navy,
                    child: Text(
                      user.firstName.isEmpty ? '?' : user.firstName[0].toUpperCase(),
                      style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w700),
                    ),
                  ),
                  const SizedBox(width: AppSpacing.md),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(user.fullName, style: AppTypography.bodyStrong),
                        Text(user.phone, style: AppTypography.caption),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          const Divider(),
          _MoreTile(icon: Icons.show_chart, label: 'Taux', onTap: () => context.push('/more/rates')),
          _MoreTile(
            icon: Icons.notifications_active_outlined,
            label: 'Alertes de taux',
            onTap: () => context.push('/more/rate-alerts'),
          ),
          _MoreTile(
            icon: Icons.trending_up,
            label: 'Taux preferentiel',
            onTap: () => context.push('/more/preferred-rate'),
          ),
          _MoreTile(
            icon: Icons.account_balance_wallet_outlined,
            label: 'Portefeuille',
            onTap: () => context.push('/more/wallet'),
          ),
          _MoreTile(
            icon: Icons.notifications_outlined,
            label: 'Notifications',
            onTap: () => context.push('/more/notifications'),
          ),
          _MoreTile(
            icon: Icons.business_center_outlined,
            label: 'Espace professionnel',
            onTap: () => context.push('/more/business'),
          ),
          const Divider(),
          _MoreTile(
            icon: Icons.logout,
            label: 'Se deconnecter',
            color: AppColors.negative,
            onTap: () => context.read<AuthRepository>().logout(),
          ),
        ],
      ),
    );
  }
}

class _MoreTile extends StatelessWidget {
  final IconData icon;
  final String label;
  final VoidCallback onTap;
  final Color? color;

  const _MoreTile({required this.icon, required this.label, required this.onTap, this.color});

  @override
  Widget build(BuildContext context) {
    return ListTile(
      leading: Icon(icon, color: color ?? AppColors.navy),
      title: Text(label, style: AppTypography.body.copyWith(color: color)),
      trailing: color == null ? const Icon(Icons.chevron_right, color: AppColors.inkFaint) : null,
      onTap: onTap,
    );
  }
}
