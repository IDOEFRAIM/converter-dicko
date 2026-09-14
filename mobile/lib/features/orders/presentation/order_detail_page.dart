import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../../core/auth/auth_session.dart';
import '../../../core/storage/memory_book_store.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../core/theme/app_typography.dart';
import '../../../core/theme/experience_theme.dart';
import '../../../shared/models/current_user.dart';
import '../../../shared/models/money.dart';
import '../../../shared/utils/date_formatting.dart';
import '../../../shared/utils/file_share.dart';
import '../../../shared/utils/validators.dart';
import '../../../shared/widgets/corridor.dart';
import '../../../shared/widgets/error_state.dart';
import '../../../shared/widgets/icon_badge.dart';
import '../../../shared/widgets/loading_view.dart';
import '../../../shared/widgets/memory_frame.dart';
import '../../../shared/widgets/status_badge.dart';
import '../../../shared/widgets/transfer_ticket.dart';
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
      create: (context) => OrderDetailController(
        orderApi: context.read<OrderApi>(),
        memoryBook: context.read<MemoryBookStore>(),
        orderId: orderId,
      )..load(),
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
      await saveAndOpenBytes(bytes: download.bytes, fileName: 'justificatif-$reference.pdf');
    } catch (error) {
      // Ecriture disque ou ouverture systeme (aucun lecteur, permission
      // refusee...) peuvent echouer sans jamais lever d'ApiException -- le
      // telechargement reseau avait pourtant reussi, ne jamais laisser ce cas
      // paraitre comme un simple "rien ne s'est passe" (mission section 38,
      // meme defaut que celui deja corrige sur l'upload de preuve de paiement).
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text("Impossible d'ouvrir le justificatif. Reessayez.")),
        );
      }
    }
  }

  Future<void> _downloadProforma(BuildContext context, OrderDetailController controller) async {
    final download = await controller.downloadProforma();
    if (download == null) {
      if (controller.errorMessage != null && context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(controller.errorMessage!)));
      }
      return;
    }
    final reference = controller.order?.reference ?? 'transfert';
    try {
      await saveAndOpenBytes(bytes: download.bytes, fileName: 'proforma-$reference.pdf');
    } catch (_) {
      // Meme discipline que le justificatif : le telechargement reseau a reussi,
      // seule l'ecriture disque / l'ouverture systeme a echoue.
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text("Impossible d'ouvrir la proforma. Reessayez.")),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final controller = context.watch<OrderDetailController>();
    final profile = context.watch<AuthSession>().experienceProfile;

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
                  const Corridor(level: CorridorLevel.compact),
                  const SizedBox(height: AppSpacing.lg),
                  _buildStatusPanel(order),
                  const SizedBox(height: AppSpacing.lg),
                  _buildAmountFlow(order),
                  const SizedBox(height: AppSpacing.lg),
                  if (order.proformaAvailable) ...[
                    _buildProformaPanel(context, controller, profile),
                    const SizedBox(height: AppSpacing.lg),
                  ],
                  if (order.status == OrderStatus.awaitingPayment) _buildAwaitingPaymentActions(context, controller),
                  if (order.status == OrderStatus.rejected) _buildRejectedActions(context, controller),
                  // "Livre memoire" (selfie souvenir) retire de l'ecran (retour client :
                  // "ce n'est pas une fonctionnalite utile pour le moment, on va voir ca
                  // apres") -- _buildMemoryPanel/captureSelfie/MemoryBookStore conserves
                  // pour reactivation future, meme convention que Portefeuille (accueil).
                  if (order.status == OrderStatus.completed) _buildReceiptPanel(context, controller, profile),
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

  Widget _buildAmountFlow(OrderDetail order) {
    return TransferTicket(
      sendAmount: Money(order.amountXof, AppCurrency.xof),
      receiveAmount: Money(order.amountCny, AppCurrency.cny),
      rows: [
        TicketRow('Taux', '1 CNY = ${order.customerRate} XOF'),
        TicketRow('Frais', Money(order.feeXof, AppCurrency.xof).formattedWithCurrency()),
      ],
      serial: order.reference,
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

  /// Un paiement rejete se resoumet sur ce MEME ordre (retour client : forcer la creation d'un
  /// nouvel ordre pour reessayer etait un contournement, jamais une solution) -- meme ecran de
  /// declaration que pour un premier paiement, le backend distingue lui-meme premiere soumission
  /// et resoumission (voir PaymentService#submit).
  Widget _buildRejectedActions(BuildContext context, OrderDetailController controller) {
    return SizedBox(
      width: double.infinity,
      child: ElevatedButton(
        onPressed: () => context.push('/activity/orders/${controller.orderId}/payment'),
        child: const Text('Resoumettre le paiement'),
      ),
    );
  }

  /// Recu du transfert termine — pastille d'icone coloree sur carte claire
  /// (meme logique que [QuickActionsRow] de l'accueil, retour client sept.
  /// 2026 : moins de texte, moins de blocs sombres empiles). Remplace
  /// l'ancienne presentation "laque + or".
  Widget _buildReceiptPanel(BuildContext context, OrderDetailController controller, ExperienceProfile profile) {
    final reference = controller.order?.reference;
    final accent = ExperiencePalette.accentFor(profile, AccentRole.premium);
    return _buildDownloadCard(
      icon: Icons.workspace_premium_outlined,
      accent: accent.color,
      accentSurface: accent.surface,
      title: 'JUSTIFICATIF OFFICIEL',
      description: reference != null ? 'Transfert #$reference — termine' : 'Transfert termine',
      buttonLabel: 'Telecharger le recu',
      busy: controller.downloadingReceipt,
      onPressed: controller.downloadingReceipt ? null : () => _downloadReceipt(context, controller),
    );
  }

  /// Facture proforma (remarque produit #3) : parcours "payer un fournisseur".
  /// Piece descriptive emise avant paiement, sans valeur d'acquittement —
  /// meme carte claire que le recu.
  Widget _buildProformaPanel(BuildContext context, OrderDetailController controller, ExperienceProfile profile) {
    final busy = controller.downloadingProforma;
    final accent = ExperiencePalette.accentFor(profile, AccentRole.send);
    return _buildDownloadCard(
      icon: Icons.description_outlined,
      accent: accent.color,
      accentSurface: accent.surface,
      title: 'FACTURE PROFORMA',
      description: 'Generee automatiquement. Pour votre banque ou le dedouanement.',
      buttonLabel: 'Telecharger la proforma',
      busy: busy,
      onPressed: busy ? null : () => _downloadProforma(context, controller),
    );
  }

  /// Carte commune aux deux documents telechargeables : pastille d'icone
  /// coloree + titre + une ligne de description + bouton pilule.
  Widget _buildDownloadCard({
    required IconData icon,
    required Color accent,
    required Color accentSurface,
    required String title,
    required String description,
    required String buttonLabel,
    required bool busy,
    required VoidCallback? onPressed,
    IconData buttonIcon = Icons.download_outlined,
    String busyLabel = 'Preparation...',
  }) {
    return _panel(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              IconBadge(icon: icon, color: accent, background: accentSurface),
              const SizedBox(width: AppSpacing.md),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(title, style: AppTypography.eyebrow),
                    const SizedBox(height: 2),
                    Text(description, style: AppTypography.caption),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: AppSpacing.md),
          SizedBox(
            width: double.infinity,
            child: FilledButton.icon(
              style: FilledButton.styleFrom(
                backgroundColor: accent,
                foregroundColor: Colors.white,
                shape: const StadiumBorder(),
              ),
              onPressed: onPressed,
              icon: busy
                  ? const SizedBox(
                      width: 16,
                      height: 16,
                      child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                    )
                  : Icon(buttonIcon, size: 18),
              label: Text(busy ? busyLabel : buttonLabel),
            ),
          ),
        ],
      ),
    );
  }

  /// Livre memoire (remarque produit #5) : un selfie souvenir apres un
  /// transfert termine, stocke **uniquement sur cet appareil**.
  ///
  /// Temporairement retire de l'ecran (retour client sept. 2026 : "ce n'est
  /// pas une fonctionnalite utile pour le moment") -- plus aucun appelant,
  /// conserve tel quel pour reactivation rapide.
  // ignore: unused_element
  Widget _buildMemoryPanel(
    BuildContext context,
    OrderDetailController controller,
    OrderDetail order,
    ExperienceProfile profile,
  ) {
    final path = controller.selfiePath;
    if (path != null) {
      return _panel(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('SOUVENIR', style: AppTypography.eyebrow),
            const SizedBox(height: AppSpacing.sm),
            MemoryFrame(
              imagePath: path,
              dateLabel: DateFormatting.dayOnly(order.completedAt ?? order.createdAt),
              amountLabel: Money(order.amountXof, AppCurrency.xof).formattedWithCurrency(),
              height: 200,
            ),
            Align(
              alignment: Alignment.centerRight,
              child: TextButton(
                onPressed: controller.removeSelfie,
                child: const Text('Retirer', style: TextStyle(color: AppColors.negative)),
              ),
            ),
          ],
        ),
      );
    }
    final accent = ExperiencePalette.accentFor(profile, AccentRole.group);
    return _buildDownloadCard(
      icon: Icons.photo_camera_outlined,
      accent: accent.color,
      accentSurface: accent.surface,
      title: 'SOUVENIR',
      description: 'La photo reste sur cet appareil.',
      buttonLabel: 'Prendre un selfie',
      buttonIcon: Icons.photo_camera_outlined,
      busyLabel: 'Ouverture...',
      busy: controller.capturingSelfie,
      onPressed: controller.capturingSelfie ? null : controller.captureSelfie,
    );
  }

  Widget _buildTrackingLink(BuildContext context, OrderDetail order) {
    return InkWell(
      borderRadius: BorderRadius.circular(AppSpacing.radiusMd),
      onTap: () => context.push('/activity/orders/${order.id}/tracking'),
      child: _panel(
        child: Row(
          children: [
            IconBadge(icon: Icons.timeline, color: Theme.of(context).colorScheme.primary, size: 36),
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
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          IconBadge(icon: Icons.local_shipping_outlined, color: Theme.of(context).colorScheme.primary, size: 36),
          const SizedBox(width: AppSpacing.md),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('REGLEMENT EN CHINE', style: AppTypography.eyebrow),
                const SizedBox(height: AppSpacing.xs),
                Text(settlement.label, style: AppTypography.bodyStrong),
                Text(settlement.description, style: AppTypography.caption),
              ],
            ),
          ),
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
            const Text('Action definitive.'),
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
