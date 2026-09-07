import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../../core/errors/api_exception.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../core/theme/app_typography.dart';
import '../../../shared/widgets/primary_action.dart';
import '../data/pool_api.dart';

/// Saisie d'un code de Ruee recu par un ami (mission "differenciation
/// marketing", Lot 3) — resout le code puis navigue vers le detail, ou
/// l'utilisateur decide de rejoindre. Ne rejoint jamais automatiquement ici :
/// consulter une Ruee et la rejoindre restent deux actions distinctes.
class PoolJoinPage extends StatefulWidget {
  const PoolJoinPage({super.key});

  @override
  State<PoolJoinPage> createState() => _PoolJoinPageState();
}

class _PoolJoinPageState extends State<PoolJoinPage> {
  final _codeController = TextEditingController();
  bool _submitting = false;
  String? _errorMessage;

  @override
  void dispose() {
    _codeController.dispose();
    super.dispose();
  }

  Future<void> _lookup() async {
    final code = _codeController.text.trim();
    if (code.isEmpty || _submitting) return;
    setState(() {
      _submitting = true;
      _errorMessage = null;
    });
    try {
      final pool = await context.read<PoolApi>().getByCode(code);
      if (!mounted) return;
      context.pushReplacement('/pay/pools/${pool.id}');
    } on ApiException catch (error) {
      setState(() => _errorMessage = error.message);
    } finally {
      if (mounted) {
        setState(() => _submitting = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Rejoindre une Ruee')),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(AppSpacing.lg),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'Un ami t\'a envoye un code ? Entre-le ici pour rejoindre sa Ruee.',
                style: AppTypography.caption,
              ),
              const SizedBox(height: AppSpacing.xl),
              TextField(
                controller: _codeController,
                textCapitalization: TextCapitalization.characters,
                inputFormatters: [UpperCaseTextFormatter()],
                style: AppTypography.metricMedium,
                decoration: const InputDecoration(labelText: 'Code de la Ruee', hintText: 'EX. AB12CD'),
                onSubmitted: (_) => _lookup(),
              ),
              if (_errorMessage != null) ...[
                const SizedBox(height: AppSpacing.md),
                Text(_errorMessage!, style: AppTypography.body.copyWith(color: AppColors.negative)),
              ],
              const SizedBox(height: AppSpacing.xl),
              PrimaryAction(label: 'Continuer', loading: _submitting, onPressed: _lookup),
            ],
          ),
        ),
      ),
    );
  }
}

class UpperCaseTextFormatter extends TextInputFormatter {
  @override
  TextEditingValue formatEditUpdate(TextEditingValue oldValue, TextEditingValue newValue) {
    return newValue.copyWith(text: newValue.text.toUpperCase(), selection: newValue.selection);
  }
}
