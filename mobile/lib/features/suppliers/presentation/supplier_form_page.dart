import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../core/errors/api_exception.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../core/theme/app_typography.dart';
import '../../../shared/models/purpose.dart';
import '../../../shared/utils/validators.dart';
import '../../../shared/widgets/primary_action.dart';
import '../data/supplier_api.dart';
import '../models/supplier_models.dart';

/// Creation OU edition d'un fournisseur — meme formulaire (mission section 21).
/// [existing] non-null => mode edition (PUT), sinon creation (POST).
class SupplierFormPage extends StatefulWidget {
  final SupplierDetail? existing;

  const SupplierFormPage({super.key, this.existing});

  @override
  State<SupplierFormPage> createState() => _SupplierFormPageState();
}

class _SupplierFormPageState extends State<SupplierFormPage> {
  final _formKey = GlobalKey<FormState>();
  late final _displayNameController = TextEditingController(text: widget.existing?.displayName);
  late final _accountNumberController = TextEditingController(text: widget.existing?.accountNumber);
  late final _bankNameController = TextEditingController(text: widget.existing?.bankName);
  late final _bankBranchController = TextEditingController(text: widget.existing?.bankBranch);
  late final _phoneController = TextEditingController(text: widget.existing?.phone);
  late final _emailController = TextEditingController(text: widget.existing?.email);
  late final _countryController = TextEditingController(text: widget.existing?.country);
  late final _cityController = TextEditingController(text: widget.existing?.city);
  late final _notesController = TextEditingController(text: widget.existing?.notes);

  late BeneficiaryType _type = widget.existing?.type ?? BeneficiaryType.alipay;
  late SupplierCurrency _currency = widget.existing?.currency ?? SupplierCurrency.cny;
  Purpose? _purpose;

  bool _submitting = false;
  String? _errorMessage;

  bool get _isEditing => widget.existing != null;

  @override
  void initState() {
    super.initState();
    _purpose = widget.existing?.purpose;
  }

  @override
  void dispose() {
    _displayNameController.dispose();
    _accountNumberController.dispose();
    _bankNameController.dispose();
    _bankBranchController.dispose();
    _phoneController.dispose();
    _emailController.dispose();
    _countryController.dispose();
    _cityController.dispose();
    _notesController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate() || _submitting) return;
    setState(() {
      _submitting = true;
      _errorMessage = null;
    });

    final request = SupplierRequest(
      type: _type,
      displayName: _displayNameController.text.trim(),
      accountNumber: _accountNumberController.text.trim(),
      bankName: _bankNameController.text.trim().isEmpty ? null : _bankNameController.text.trim(),
      bankBranch: _bankBranchController.text.trim().isEmpty ? null : _bankBranchController.text.trim(),
      phone: _phoneController.text.trim().isEmpty ? null : _phoneController.text.trim(),
      email: _emailController.text.trim().isEmpty ? null : _emailController.text.trim(),
      country: _countryController.text.trim().isEmpty ? null : _countryController.text.trim(),
      city: _cityController.text.trim().isEmpty ? null : _cityController.text.trim(),
      currency: _currency,
      purpose: _purpose,
      notes: _notesController.text.trim().isEmpty ? null : _notesController.text.trim(),
    );

    final api = context.read<SupplierApi>();
    try {
      if (_isEditing) {
        await api.update(widget.existing!.id, request);
      } else {
        await api.create(request);
      }
      if (!mounted) return;
      Navigator.of(context).pop(true);
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
      appBar: AppBar(title: Text(_isEditing ? 'Modifier le fournisseur' : 'Nouveau fournisseur')),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(AppSpacing.lg),
          child: Form(
            key: _formKey,
            autovalidateMode: AutovalidateMode.onUserInteraction,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                DropdownButtonFormField<BeneficiaryType>(
                  initialValue: _type,
                  decoration: const InputDecoration(labelText: 'Type de compte'),
                  items: BeneficiaryType.selectableOptions
                      .map((t) => DropdownMenuItem(value: t, child: Text(t.label)))
                      .toList(growable: false),
                  onChanged: (value) => setState(() => _type = value!),
                ),
                const SizedBox(height: AppSpacing.md),
                TextFormField(
                  controller: _displayNameController,
                  maxLength: 120,
                  decoration: const InputDecoration(labelText: 'Nom affiche'),
                  validator: (v) => Validators.requiredMaxLength(v, 120, label: 'Le nom affiche'),
                ),
                const SizedBox(height: AppSpacing.md),
                TextFormField(
                  controller: _accountNumberController,
                  maxLength: 120,
                  decoration: InputDecoration(
                    labelText: _type == BeneficiaryType.chineseBankAccount ? 'Numero de compte' : 'Identifiant du compte',
                  ),
                  validator: (v) => Validators.requiredMaxLength(v, 120, label: 'Ce champ'),
                ),
                if (_type == BeneficiaryType.chineseBankAccount) ...[
                  const SizedBox(height: AppSpacing.md),
                  TextFormField(
                    controller: _bankNameController,
                    maxLength: 120,
                    decoration: const InputDecoration(labelText: 'Nom de la banque'),
                    validator: (v) => Validators.requiredMaxLength(v, 120, label: 'La banque'),
                  ),
                  const SizedBox(height: AppSpacing.md),
                  TextFormField(
                    controller: _bankBranchController,
                    maxLength: 120,
                    decoration: const InputDecoration(labelText: 'Agence (optionnel)'),
                    validator: (v) => Validators.optionalMaxLength(v, 120, label: "L'agence"),
                  ),
                ],
                const SizedBox(height: AppSpacing.md),
                TextFormField(
                  controller: _phoneController,
                  maxLength: 30,
                  decoration: const InputDecoration(labelText: 'Telephone (optionnel)'),
                  keyboardType: TextInputType.phone,
                  validator: (v) => Validators.optionalMaxLength(v, 30, label: 'Le telephone'),
                ),
                const SizedBox(height: AppSpacing.md),
                TextFormField(
                  controller: _emailController,
                  maxLength: 160,
                  decoration: const InputDecoration(labelText: 'Email (optionnel)'),
                  keyboardType: TextInputType.emailAddress,
                  validator: Validators.email,
                ),
                const SizedBox(height: AppSpacing.md),
                Row(
                  children: [
                    Expanded(
                      child: TextFormField(
                        controller: _cityController,
                        maxLength: 100,
                        decoration: const InputDecoration(labelText: 'Ville (optionnel)'),
                        validator: (v) => Validators.optionalMaxLength(v, 100, label: 'La ville'),
                      ),
                    ),
                    const SizedBox(width: AppSpacing.md),
                    Expanded(
                      child: TextFormField(
                        controller: _countryController,
                        maxLength: 100,
                        decoration: const InputDecoration(labelText: 'Pays (optionnel)'),
                        validator: (v) => Validators.optionalMaxLength(v, 100, label: 'Le pays'),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: AppSpacing.md),
                DropdownButtonFormField<SupplierCurrency>(
                  initialValue: _currency,
                  decoration: const InputDecoration(labelText: 'Devise du compte'),
                  items: const [
                    DropdownMenuItem(value: SupplierCurrency.cny, child: Text('CNY')),
                    DropdownMenuItem(value: SupplierCurrency.xof, child: Text('XOF')),
                  ],
                  onChanged: (value) => setState(() => _currency = value!),
                ),
                const SizedBox(height: AppSpacing.md),
                DropdownButtonFormField<Purpose?>(
                  initialValue: _purpose,
                  decoration: const InputDecoration(labelText: 'Motif par defaut (optionnel)'),
                  items: [
                    const DropdownMenuItem<Purpose?>(value: null, child: Text('Non precise')),
                    ...Purpose.options.map((p) => DropdownMenuItem<Purpose?>(value: p, child: Text(p.label))),
                  ],
                  onChanged: (value) => setState(() => _purpose = value),
                ),
                const SizedBox(height: AppSpacing.md),
                TextFormField(
                  controller: _notesController,
                  decoration: const InputDecoration(labelText: 'Notes (optionnel)'),
                  maxLines: 3,
                  maxLength: 1000,
                  validator: (v) => Validators.optionalMaxLength(v, 1000, label: 'Les notes'),
                ),
                if (_errorMessage != null) ...[
                  const SizedBox(height: AppSpacing.md),
                  Text(_errorMessage!, style: AppTypography.body.copyWith(color: AppColors.negative)),
                ],
                const SizedBox(height: AppSpacing.xl),
                PrimaryAction(
                  label: _isEditing ? 'Enregistrer' : 'Creer le fournisseur',
                  loading: _submitting,
                  onPressed: _submit,
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
