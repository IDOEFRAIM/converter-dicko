import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../../core/errors/api_exception.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../core/theme/app_typography.dart';
import '../../../shared/utils/validators.dart';
import '../../../shared/widgets/primary_action.dart';
import '../data/pool_api.dart';
import '../models/pool_models.dart';

/// Creation d'une Ruee collective (mission "differenciation marketing", Lot
/// 3) : un objectif de volume et une echeance, rien d'autre — le createur
/// devient automatiquement le premier participant.
class PoolCreatePage extends StatefulWidget {
  const PoolCreatePage({super.key});

  @override
  State<PoolCreatePage> createState() => _PoolCreatePageState();
}

class _PoolCreatePageState extends State<PoolCreatePage> {
  final _formKey = GlobalKey<FormState>();
  final _targetController = TextEditingController();
  int _durationMinutes = 30;

  bool _submitting = false;
  String? _errorMessage;

  static const _durationOptions = [15, 30, 60, 120];

  @override
  void dispose() {
    _targetController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (_submitting || !_formKey.currentState!.validate()) {
      return;
    }
    setState(() {
      _submitting = true;
      _errorMessage = null;
    });
    try {
      final pool = await context.read<PoolApi>().create(CreatePoolRequest(
            targetAmountXof: _targetController.text.trim(),
            durationMinutes: _durationMinutes,
          ));
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
      appBar: AppBar(title: const Text('Lancer une Ruee')),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(AppSpacing.lg),
          child: Form(
            key: _formKey,
            autovalidateMode: AutovalidateMode.onUserInteraction,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Objectif atteint a temps = reduction pour chaque participant sur son prochain transfert.',
                  style: AppTypography.caption,
                ),
                const SizedBox(height: AppSpacing.xl),
                Text('OBJECTIF DE VOLUME', style: AppTypography.eyebrow),
                const SizedBox(height: AppSpacing.sm),
                TextFormField(
                  controller: _targetController,
                  keyboardType: const TextInputType.numberWithOptions(decimal: true),
                  style: AppTypography.metricMedium,
                  decoration: const InputDecoration(suffixText: 'XOF', hintText: '0'),
                  validator: Validators.positiveAmount,
                ),
                const SizedBox(height: AppSpacing.xl),
                Text('DUREE', style: AppTypography.eyebrow),
                const SizedBox(height: AppSpacing.sm),
                Wrap(
                  spacing: AppSpacing.sm,
                  children: _durationOptions
                      .map(
                        (minutes) => ChoiceChip(
                          label: Text(minutes < 60 ? '$minutes min' : '${minutes ~/ 60} h'),
                          selected: _durationMinutes == minutes,
                          onSelected: (_) => setState(() => _durationMinutes = minutes),
                        ),
                      )
                      .toList(growable: false),
                ),
                if (_errorMessage != null) ...[
                  const SizedBox(height: AppSpacing.md),
                  Text(_errorMessage!, style: AppTypography.body.copyWith(color: AppColors.negative)),
                ],
                const SizedBox(height: AppSpacing.xxl),
                PrimaryAction(label: 'Lancer la Ruee', loading: _submitting, onPressed: _submit),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
