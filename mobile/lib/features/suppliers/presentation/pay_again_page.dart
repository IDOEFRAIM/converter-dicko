import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../../core/errors/api_exception.dart';
import '../../../core/network/idempotency.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../core/theme/app_typography.dart';
import '../../../shared/models/purpose.dart';
import '../../../shared/utils/validators.dart';
import '../../../shared/widgets/primary_action.dart';
import '../data/supplier_api.dart';
import '../models/supplier_models.dart';

/// "Payer a nouveau" depuis un fournisseur (mission section 22) : montant
/// TOUJOURS resaisi, jamais copie d'un ordre passe. Le backend cree un
/// NOUVEAU devis (pricing courant) puis un NOUVEL ordre — jamais un clone.
class PayAgainPage extends StatefulWidget {
  final String supplierId;

  const PayAgainPage({super.key, required this.supplierId});

  @override
  State<PayAgainPage> createState() => _PayAgainPageState();
}

class _PayAgainPageState extends State<PayAgainPage> {
  final _formKey = GlobalKey<FormState>();
  final _amountController = TextEditingController();
  final _idempotency = IdempotencyAttempt();

  Purpose? _purpose;
  bool _submitting = false;
  String? _errorMessage;

  @override
  void dispose() {
    _amountController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate() || _submitting) return;
    setState(() {
      _submitting = true;
      _errorMessage = null;
    });

    final request = PayAgainRequest(amountXof: _amountController.text.trim(), purpose: _purpose);
    final api = context.read<SupplierApi>();
    try {
      final key = _idempotency.keyFor(request.toJson());
      final order = await api.payAgain(widget.supplierId, request, idempotencyKey: key);
      _idempotency.complete();
      if (!mounted) return;
      // Transaction consideree terminee : on avance vers le detail du
      // nouvel ordre plutot que de revenir en arriere (meme convention que
      // le parcours de devis principal).
      context.go('/activity/orders/${order.id}');
    } on ApiException catch (error) {
      // Pas de complete() ici : un retry avec le meme montant doit rejouer
      // la meme cle d'idempotence (mission section 27).
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
      appBar: AppBar(title: const Text('Payer a nouveau')),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(AppSpacing.lg),
          child: Form(
            key: _formKey,
            autovalidateMode: AutovalidateMode.onUserInteraction,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('MONTANT', style: AppTypography.eyebrow),
                const SizedBox(height: AppSpacing.sm),
                TextFormField(
                  controller: _amountController,
                  keyboardType: const TextInputType.numberWithOptions(decimal: true),
                  style: AppTypography.metricMedium,
                  decoration: const InputDecoration(suffixText: 'XOF', hintText: '0'),
                  validator: Validators.positiveAmount,
                ),
                const SizedBox(height: AppSpacing.lg),
                Text('MOTIF (OPTIONNEL)', style: AppTypography.eyebrow),
                const SizedBox(height: AppSpacing.sm),
                DropdownButtonFormField<Purpose?>(
                  initialValue: _purpose,
                  decoration: const InputDecoration(labelText: 'Motif'),
                  items: [
                    const DropdownMenuItem<Purpose?>(value: null, child: Text('Non precise')),
                    ...Purpose.options.map((p) => DropdownMenuItem<Purpose?>(value: p, child: Text(p.label))),
                  ],
                  onChanged: (value) => setState(() => _purpose = value),
                ),
                if (_errorMessage != null) ...[
                  const SizedBox(height: AppSpacing.md),
                  Text(_errorMessage!, style: AppTypography.body.copyWith(color: AppColors.negative)),
                ],
                const SizedBox(height: AppSpacing.xl),
                PrimaryAction(label: 'Continuer', loading: _submitting, onPressed: _submit),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
