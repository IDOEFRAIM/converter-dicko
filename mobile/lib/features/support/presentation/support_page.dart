import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../core/theme/app_typography.dart';
import '../../../shared/widgets/error_state.dart';
import '../../../shared/widgets/loading_view.dart';
import '../../../shared/widgets/notification_bell_button.dart';
import '../application/support_controller.dart';
import '../data/support_api.dart';
import '../models/support_models.dart';

/// Messagerie SAV (retour client : "les utilisateurs doivent pouvoir faire des reclamations") --
/// un seul fil continu, jamais un ticket par reclamation.
class SupportPage extends StatelessWidget {
  const SupportPage({super.key});

  @override
  Widget build(BuildContext context) {
    return ChangeNotifierProvider(
      create: (context) => SupportController(supportApi: context.read<SupportApi>())..load(),
      child: const _SupportView(),
    );
  }
}

class _SupportView extends StatefulWidget {
  const _SupportView();

  @override
  State<_SupportView> createState() => _SupportViewState();
}

class _SupportViewState extends State<_SupportView> {
  final _bodyController = TextEditingController();
  final _scrollController = ScrollController();
  int _lastMessageCount = 0;

  @override
  void dispose() {
    _bodyController.dispose();
    _scrollController.dispose();
    super.dispose();
  }

  void _scrollToBottomIfGrew(int newCount) {
    if (newCount <= _lastMessageCount) return;
    _lastMessageCount = newCount;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_scrollController.hasClients) {
        _scrollController.animateTo(
          _scrollController.position.maxScrollExtent,
          duration: const Duration(milliseconds: 250),
          curve: Curves.easeOut,
        );
      }
    });
  }

  Future<void> _send(SupportController controller) async {
    final text = _bodyController.text.trim();
    if (text.isEmpty) return;
    _bodyController.clear();
    await controller.send(text);
  }

  @override
  Widget build(BuildContext context) {
    final controller = context.watch<SupportController>();
    _scrollToBottomIfGrew(controller.messages.length);

    return Scaffold(
      appBar: AppBar(title: const Text('Messagerie'), actions: const [NotificationBellButton()]),
      body: SafeArea(
        child: Builder(
          builder: (context) {
            if (controller.loading) {
              return const LoadingView();
            }
            if (controller.errorMessage != null && controller.messages.isEmpty) {
              return ErrorState(message: controller.errorMessage!, onRetry: controller.load);
            }
            return Column(
              children: [
                Expanded(
                  child: controller.messages.isEmpty
                      ? Padding(
                          padding: const EdgeInsets.all(AppSpacing.lg),
                          child: Text(
                            "Aucun message pour l'instant. Ecrivez-nous ci-dessous : un membre de "
                            "l'equipe vous repondra ici.",
                            style: AppTypography.body.copyWith(color: AppColors.inkMuted),
                            textAlign: TextAlign.center,
                          ),
                        )
                      : ListView.builder(
                          controller: _scrollController,
                          padding: const EdgeInsets.all(AppSpacing.lg),
                          itemCount: controller.messages.length,
                          itemBuilder: (context, index) => _MessageBubble(message: controller.messages[index]),
                        ),
                ),
                if (controller.errorMessage != null && controller.messages.isNotEmpty)
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: AppSpacing.lg),
                    child: Text(controller.errorMessage!, style: AppTypography.caption.copyWith(color: AppColors.negative)),
                  ),
                _Composer(
                  controller: _bodyController,
                  sending: controller.sending,
                  onSend: () => _send(controller),
                ),
              ],
            );
          },
        ),
      ),
    );
  }
}

class _MessageBubble extends StatelessWidget {
  final SupportMessage message;

  const _MessageBubble({required this.message});

  @override
  Widget build(BuildContext context) {
    final fromAdmin = message.fromAdmin;
    return Align(
      alignment: fromAdmin ? Alignment.centerRight : Alignment.centerLeft,
      child: Container(
        margin: const EdgeInsets.only(bottom: AppSpacing.sm),
        padding: const EdgeInsets.symmetric(horizontal: AppSpacing.md, vertical: AppSpacing.sm),
        constraints: BoxConstraints(maxWidth: MediaQuery.of(context).size.width * 0.75),
        decoration: BoxDecoration(
          color: fromAdmin ? AppColors.lacquer : AppColors.paper,
          borderRadius: BorderRadius.only(
            topLeft: const Radius.circular(AppSpacing.radiusMd),
            topRight: const Radius.circular(AppSpacing.radiusMd),
            bottomLeft: Radius.circular(fromAdmin ? AppSpacing.radiusMd : 2),
            bottomRight: Radius.circular(fromAdmin ? 2 : AppSpacing.radiusMd),
          ),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(
              message.body,
              style: AppTypography.body.copyWith(color: fromAdmin ? AppColors.onLacquer : AppColors.ink),
            ),
            const SizedBox(height: 2),
            Text(
              fromAdmin ? 'Support' : 'Vous',
              style: AppTypography.caption.copyWith(
                color: fromAdmin ? AppColors.onLacquerMuted : AppColors.inkMuted,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _Composer extends StatelessWidget {
  final TextEditingController controller;
  final bool sending;
  final VoidCallback onSend;

  const _Composer({required this.controller, required this.sending, required this.onSend});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(AppSpacing.md),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.end,
        children: [
          Expanded(
            child: TextField(
              controller: controller,
              minLines: 1,
              maxLines: 4,
              maxLength: 2000,
              decoration: const InputDecoration(hintText: 'Votre message', counterText: ''),
              textInputAction: TextInputAction.send,
              onSubmitted: (_) => onSend(),
            ),
          ),
          const SizedBox(width: AppSpacing.sm),
          IconButton.filled(
            onPressed: sending ? null : onSend,
            icon: sending
                ? const SizedBox(
                    width: 18,
                    height: 18,
                    child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                  )
                : const Icon(Icons.send),
          ),
        ],
      ),
    );
  }
}
