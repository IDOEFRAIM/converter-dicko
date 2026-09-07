import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../core/theme/app_typography.dart';
import '../../../shared/models/money.dart';
import '../../../shared/utils/file_share.dart';
import '../../../shared/utils/validators.dart';
import '../../../shared/widgets/corridor.dart';
import '../../../shared/widgets/error_state.dart';
import '../../../shared/widgets/loading_view.dart';
import '../../../shared/widgets/money_display.dart';
import '../../../shared/widgets/status_badge.dart';
import '../application/order_detail_controller.dart';
import '../data/order_api.dart';
import '../models/order_models.dart';
import '../models/order_status_messages.dart';

/// Detail d'un transfert (mission section 24) : statut, montants, reglement,
/// fournisseur, motif, acces au suivi et au justificatif.
class OrderDetailPage extends StatelessWidget {
  final String orderId;

  const OrderDetailPage({super.key, required this.orderId});

  @override
  Widget build(BuildContext context) {
    return ChangeNotifierProvider(
      create: (context) => OrderDetailController(orderApi: context.read<OrderApi>(), orderId: orderId)..load(),
      child: const _OrderDetailView(),
    );
  }
}

class _OrderDetailView extends StatelessWidget {
  const _OrderDetailView();

  Future<void> _cancel(BuildContext context, OrderDetailController controller) async {
    final reason = await showDialog<String>(
      context: context,
      builder: (context) => const _CancelOrderDialog(),
    );
    if (reason == null || reason.trim().isEmpty) return;
    final success = await controller.cancel(reason.trim());
    if (success && context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Ordre annule.')));
    } else if (controller.errorMessage != null && context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(controller.errorMessage!)));
    }
  }

  Future<void> _downloadReceipt(BuildContext context, OrderDetailController controller) async {
    final download = await controller.downloadReceipt();
    if (download == null) {
      if (controller.errorMessage != null && context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(controller.errorMessage!)));
      }
      return;
    }
    final reference = controller.order?.reference ?? 'transfert';
    try {
      await saveAndShareBytes(bytes: download.bytes, fileName: 'justificatif-$reference.pdf');
    } catch (error) {
      // Ecriture disque ou feuille de partage native (permission refusee,
      // plugin non enregistre...) peuvent echouer sans jamais lever
      // d'ApiException -- le telechargement reseau avait pourtant reussi, ne
      // jamais laisser ce cas paraitre comme un simple "rien ne s'est passe"
      // (mission section 38, meme defaut que celui deja corrige sur l'upload
      // de preuve de paiement).
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text("Impossible d'ouvrir ou de partager le justificatif. Reessayez.")),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final controller = context.watch<OrderDetailController>();

    return Scaffold(
      appBar: AppBar(title: const Text('Transfert')),
      body: SafeArea(
        child: Builder(
          builder: (context) {
            if (controller.loading) {
              return const LoadingView();
            }
            final order = controller.order;
            if (order == null) {
              return ErrorState(
                message: controller.errorMessage ?? 'Impossible de charger cet ordre.',
                onRetry: controller.load,
              );
            }
            return RefreshIndicator(
              onRefresh: controller.load,
              color: Theme.of(context).colorScheme.primary,
              child: ListView(
                padding: const EdgeInsets.all(AppSpacing.lg),
                children: [
                  Text('#${order.reference}', style: AppTypography.eyebrow),
                  const SizedBox(height: AppSpacing.sm),
                  const Corridor(level: CorridorLevel.compact),
                  const SizedBox(height: AppSpacing.lg),
                  _buildStatusPanel(order),
                  const SizedBox(height: AppSpacing.lg),
                  _buildAmountFlow(context, order),
                  const SizedBox(height: AppSpacing.lg),
                  if (order.status == OrderStatus.awaitingPayment) _buildAwaitingPaymentActions(context, controller),
                  if (order.status == OrderStatus.completed) _buildReceiptPanel(context, controller),
                  const SizedBox(height: AppSpacing.md),
                  _buildTrackingLink(context, order),
                  const SizedBox(height: AppSpacing.lg),
                  if (order.status == OrderStatus.processing || order.status == OrderStatus.completed)
                    _buildSettlementPanel(context, order),
                  const SizedBox(height: AppSpacing.lg),
                  _buildSupplierPanel(order),
                ],
              ),
            );
          },
        ),
      ),
    );
  }

  Widget _buildStatusPanel(OrderDetail order) {
    final message = orderStatusMessages[order.status]!;
    return _panel(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          StatusBadge(status: order.status.code),
          const SizedBox(height: AppSpacing.sm),
          Text(message.description, style: AppTypography.body.copyWith(color: AppColors.inkMuted)),
          if (order.status == OrderStatus.rejected && order.rejectionReason != null) ...[
            const SizedBox(height: AppSpacing.xs),
            Text(order.rejectionReason!, style: AppTypography.body.copyWith(color: AppColors.negative)),
          ],
          if (order.status == OrderStatus.cancelled && order.cancellationReason != null) ...[
            const SizedBox(height: AppSpacing.xs),
            Text(order.cancellationReason!, style: AppTypography.body.copyWith(color: AppColors.negative)),
          ],
        ],
      ),
    );
  }

  Widget _buildAmountFlow(BuildContext context, OrderDetail order) {
    return Column(
      children: [
        Column(
          children: [
            Text('VOUS ENVOYEZ', style: AppTypography.eyebrow),
            const SizedBox(height: AppSpacing.xs),
            MoneyDisplay(money: Money(order.amountXof, AppCurrency.xof), size: MoneyDisplaySize.large),
          ],
        ),
        const SizedBox(height: AppSpacing.md),
        Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Text('Frais ', style: AppTypography.caption),
            Text(Money(order.feeXof, AppCurrency.xof).formattedWithCurrency(), style: AppTypography.bodyStrong),
            Text('  ·  Taux ', style: AppTypography.caption),
            Text('1 CNY = ${order.customerRate} XOF', style: AppTypography.bodyStrong),
          ],
        ),
        const SizedBox(height: AppSpacing.md),
        Column(
          children: [
            Text('LE BENEFICIAIRE RECOIT', style: AppTypography.eyebrow),
            const SizedBox(height: AppSpacing.xs),
            MoneyDisplay(
              money: Money(order.amountCny, AppCurrency.cny),
              size: MoneyDisplaySize.large,
              color: Theme.of(context).colorScheme.primary,
            ),
          ],
        ),
      ],
    );
  }

  Widget _buildAwaitingPaymentActions(BuildContext context, OrderDetailController controller) {
    return Row(
      children: [
        Expanded(
          child: OutlinedButton(
            onPressed: controller.cancelling ? null : () => _cancel(context, controller),
            child: const Text('Annuler'),
          ),
        ),
        const SizedBox(width: AppSpacing.md),
        Expanded(
          child: ElevatedButton(
            onPressed: () => context.push('/activity/orders/${controller.orderId}/payment'),
            child: const Text('Payer maintenant'),
          ),
        ),
      ],
    );
  }

  Widget _buildReceiptPanel(BuildContext context, OrderDetailController controller) {
    return _panel(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(Icons.receipt_long, color: Theme.of(context).colorScheme.primary, size: 18),
              const SizedBox(width: AppSpacing.xs),
              Text('JUSTIFICATIF', style: AppTypography.eyebrow),
            ],
          ),
          const SizedBox(height: AppSpacing.xs),
          Text('Votre transfert est termine.', style: AppTypography.body.copyWith(color: AppColors.inkMuted)),
          const SizedBox(height: AppSpacing.md),
          SizedBox(
            width: double.infinity,
            child: ElevatedButton.icon(
              onPressed: controller.downloadingReceipt ? null : () => _downloadReceipt(context, controller),
              icon: controller.downloadingReceipt
                  ? const SizedBox(
                      width: 16,
                      height: 16,
                      child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                    )
                  : const Icon(Icons.download_outlined, size: 18),
              label: Text(controller.downloadingReceipt ? 'Preparation...' : 'Telecharger le justificatif'),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildTrackingLink(BuildContext context, OrderDetail order) {
    return InkWell(
      borderRadius: BorderRadius.circular(AppSpacing.radiusMd),
      onTap: () => context.push('/activity/orders/${order.id}/tracking'),
      child: _panel(
        child: Row(
          children: [
            Icon(Icons.timeline, color: Theme.of(context).colorScheme.primary),
            const SizedBox(width: AppSpacing.md),
            const Expanded(child: Text('Voir le suivi du transfert')),
            const Icon(Icons.chevron_right, color: AppColors.inkFaint),
          ],
        ),
      ),
    );
  }

  Widget _buildSettlementPanel(BuildContext context, OrderDetail order) {
    final settlement = deriveSettlementView(order.status);
    return _panel(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(Icons.local_shipping_outlined, color: Theme.of(context).colorScheme.primary, size: 18),
              const SizedBox(width: AppSpacing.xs),
              Text('REGLEMENT EN CHINE', style: AppTypography.eyebrow),
            ],
          ),
          const SizedBox(height: AppSpacing.xs),
          Text(settlement.label, style: AppTypography.bodyStrong),
          Text(settlement.description, style: AppTypography.caption),
        ],
      ),
    );
  }

  Widget _buildSupplierPanel(OrderDetail order) {
    return _panel(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('FOURNISSEUR', style: AppTypography.eyebrow),
          const SizedBox(height: AppSpacing.xs),
          Text(order.beneficiary.fullName, style: AppTypography.bodyStrong),
          Text(
            '${order.beneficiary.type.label} · ${order.beneficiary.identifier}'
            '${order.beneficiary.bankName != null ? ' · ${order.beneficiary.bankName}' : ''}',
            style: AppTypography.caption,
          ),
          if (order.purpose != null) ...[
            const SizedBox(height: AppSpacing.sm),
            Text(
              'Motif : ${order.purpose!.label}'
              '${order.purposeDetails != null && order.purposeDetails!.isNotEmpty ? ' — ${order.purposeDetails}' : ''}',
              style: AppTypography.body,
            ),
          ],
          if (order.supplierId != null) ...[
            const SizedBox(height: AppSpacing.md),
            Builder(
              builder: (context) => SizedBox(
                width: double.infinity,
                child: ElevatedButton(
                  onPressed: () => context.push('/suppliers/${order.supplierId}/pay-again'),
                  child: const Text('Payer a nouveau'),
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }

  Widget _panel({required Widget child}) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(AppSpacing.lg),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(AppSpacing.radiusMd),
        border: Border.all(color: AppColors.outline),
      ),
      child: child,
    );
  }
}

class _CancelOrderDialog extends StatefulWidget {
  const _CancelOrderDialog();

  @override
  State<_CancelOrderDialog> createState() => _CancelOrderDialogState();
}

class _CancelOrderDialogState extends State<_CancelOrderDialog> {
  final _formKey = GlobalKey<FormState>();
  final _controller = TextEditingController();

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _confirm() {
    // Bug reel trouve en test : sans validation explicite, confirmer avec un
    // motif vide fermait le dialogue sans rien faire — aucune annulation,
    // aucun message (mission section 38 : jamais un ecran/action muette).
    if (_formKey.currentState!.validate()) {
      Navigator.of(context).pop(_controller.text.trim());
    }
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text("Annuler l'ordre"),
      content: Form(
        key: _formKey,
        autovalidateMode: AutovalidateMode.onUserInteraction,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('Cette action est definitive et libere la reservation de tresorerie associee.'),
            const SizedBox(height: AppSpacing.md),
            TextFormField(
              controller: _controller,
              decoration: const InputDecoration(labelText: "Motif de l'annulation *"),
              maxLines: 2,
              maxLength: 500,
              validator: (value) => Validators.requiredMaxLength(value, 500, label: 'Le motif'),
            ),
          ],
        ),
      ),
      actions: [
        TextButton(onPressed: () => Navigator.of(context).pop(), child: const Text('Retour')),
        TextButton(onPressed: _confirm, child: const Text("Annuler l'ordre")),
      ],
    );
  }
}
