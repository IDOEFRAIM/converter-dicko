import 'dart:io';

import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
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

  /// Code QR nouvellement choisi (pas encore televerse) — remplace ou
  /// complete `widget.existing?.qrCodeUploaded` une fois l'envoi reussi.
  XFile? _pickedQrCode;
  bool _pickingQrCode = false;

  /// Rempli des qu'un premier essai de {@link _submit} cree reellement le
  /// fournisseur -- si le seul l'envoi du QR echoue ensuite (reseau...), un
  /// nouvel essai met a jour ce MEME fournisseur au lieu d'en creer un
  /// second en double.
  String? _createdSupplierId;

  bool _submitting = false;
  String? _errorMessage;

  bool get _isEditing => widget.existing != null;

  /// Fournisseur deja persiste a mettre a jour plutot que (re)creer : celui
  /// en edition, ou celui cree par un essai precedent de {@link _submit}.
  String? get _existingSupplierId => widget.existing?.id ?? _createdSupplierId;

  bool get _hasAnyQrCode => _pickedQrCode != null || (widget.existing?.qrCodeUploaded ?? false);

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

  Future<void> _pickQrCode() async {
    if (_pickingQrCode) return;
    setState(() => _pickingQrCode = true);
    try {
      final picked = await ImagePicker().pickImage(source: ImageSource.gallery, imageQuality: 90);
      if (picked != null && mounted) {
        setState(() => _pickedQrCode = picked);
      }
    } catch (_) {
      // Permission refusee, appel concurrent... jamais un tap muet (meme discipline
      // que PaymentSubmitController.pickAndUploadProof, mission section 38).
      if (mounted) {
        setState(() => _errorMessage = "Impossible de selectionner cette image. Reessayez.");
      }
    } finally {
      if (mounted) {
        setState(() => _pickingQrCode = false);
      }
    }
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate() || _submitting) return;
    // Le code QR est l'identifiant reel d'un Alipay/WeChat Pay (jamais un texte) : un fournisseur
    // qu'on vient de creer sans QR ne pourrait servir a aucun ordre (Supplier#isReadyForPayment
    // cote backend) -- on l'exige donc a la creation. En edition, un QR deja televerse suffit.
    if (_type.requiresQrCode && !_hasAnyQrCode) {
      setState(() => _errorMessage = 'Le code QR ${_type.label} est obligatoire.');
      return;
    }
    setState(() {
      _submitting = true;
      _errorMessage = null;
    });

    final accountNumber = _accountNumberController.text.trim();
    final request = SupplierRequest(
      type: _type,
      displayName: _displayNameController.text.trim(),
      accountNumber: accountNumber.isEmpty ? null : accountNumber,
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
      final targetId = _existingSupplierId;
      final supplier = targetId != null ? await api.update(targetId, request) : await api.create(request);
      _createdSupplierId = supplier.id;
      if (_pickedQrCode != null) {
        await api.uploadQrCode(supplier.id, _pickedQrCode!.path, _pickedQrCode!.name);
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
                  decoration: const InputDecoration(labelText: 'Nom affiche *'),
                  validator: (v) => Validators.requiredMaxLength(v, 120, label: 'Le nom affiche'),
                ),
                const SizedBox(height: AppSpacing.md),
                if (_type.requiresQrCode) ...[
                  Text('${_type.identifierLabel} *', style: AppTypography.bodyStrong),
                  const SizedBox(height: AppSpacing.xs),
                  _QrCodePicker(
                    picking: _pickingQrCode,
                    pickedFile: _pickedQrCode,
                    existingFileName: widget.existing?.qrCodeFileName,
                    onTap: _pickQrCode,
                  ),
                  const SizedBox(height: AppSpacing.md),
                  TextFormField(
                    controller: _accountNumberController,
                    maxLength: 120,
                    decoration: InputDecoration(labelText: _type.supplementaryReferenceLabel),
                    validator: (v) => Validators.optionalMaxLength(v, 120, label: 'Ce champ'),
                  ),
                ] else
                  TextFormField(
                    controller: _accountNumberController,
                    maxLength: 120,
                    decoration: InputDecoration(labelText: '${_type.identifierLabel} *'),
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
                    decoration: const InputDecoration(labelText: 'Agence'),
                    validator: (v) => Validators.optionalMaxLength(v, 120, label: "L'agence"),
                  ),
                ],
                const SizedBox(height: AppSpacing.md),
                TextFormField(
                  controller: _phoneController,
                  maxLength: 30,
                  decoration: const InputDecoration(labelText: 'Telephone'),
                  keyboardType: TextInputType.phone,
                  validator: (v) => Validators.optionalMaxLength(v, 30, label: 'Le telephone'),
                ),
                const SizedBox(height: AppSpacing.md),
                TextFormField(
                  controller: _emailController,
                  maxLength: 160,
                  decoration: const InputDecoration(labelText: 'Email'),
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
                        decoration: const InputDecoration(labelText: 'Ville'),
                        validator: (v) => Validators.optionalMaxLength(v, 100, label: 'La ville'),
                      ),
                    ),
                    const SizedBox(width: AppSpacing.md),
                    Expanded(
                      child: TextFormField(
                        controller: _countryController,
                        maxLength: 100,
                        decoration: const InputDecoration(labelText: 'Pays'),
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
                  decoration: const InputDecoration(labelText: 'Motif par defaut'),
                  items: [
                    const DropdownMenuItem<Purpose?>(value: null, child: Text('Non precise')),
                    ...Purpose.options.map((p) => DropdownMenuItem<Purpose?>(value: p, child: Text(p.label))),
                  ],
                  onChanged: (value) => setState(() => _purpose = value),
                ),
                const SizedBox(height: AppSpacing.md),
                TextFormField(
                  controller: _notesController,
                  decoration: const InputDecoration(labelText: 'Notes'),
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

/// Zone de televersement du code QR — un QR est une IMAGE, jamais un champ
/// texte (retour client) : ni regex ni saisie clavier possible pour ce
/// contenu, seul un choix de photo a un sens.
class _QrCodePicker extends StatelessWidget {
  final bool picking;
  final XFile? pickedFile;
  final String? existingFileName;
  final VoidCallback onTap;

  const _QrCodePicker({
    required this.picking,
    required this.pickedFile,
    required this.existingFileName,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final hasImage = pickedFile != null || existingFileName != null;
    return InkWell(
      onTap: picking ? null : onTap,
      borderRadius: BorderRadius.circular(AppSpacing.radiusMd),
      child: Container(
        height: 140,
        width: double.infinity,
        decoration: BoxDecoration(
          color: AppColors.paper,
          borderRadius: BorderRadius.circular(AppSpacing.radiusMd),
          border: Border.all(color: AppColors.outline, width: 1.5),
        ),
        child: picking
            ? const Center(child: CircularProgressIndicator())
            : pickedFile != null
                ? Row(
                    children: [
                      Padding(
                        padding: const EdgeInsets.all(AppSpacing.md),
                        child: ClipRRect(
                          borderRadius: BorderRadius.circular(AppSpacing.radiusSm),
                          child: Image.file(File(pickedFile!.path), width: 90, height: 108, fit: BoxFit.cover),
                        ),
                      ),
                      Expanded(
                        child: Padding(
                          padding: const EdgeInsets.only(right: AppSpacing.md),
                          child: Column(
                            mainAxisSize: MainAxisSize.min,
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              const Row(
                                children: [
                                  Icon(Icons.check_circle, size: 16, color: AppColors.positive),
                                  SizedBox(width: AppSpacing.xs),
                                  Text('Nouveau code QR pret', style: AppTypography.bodyStrong),
                                ],
                              ),
                              const SizedBox(height: 2),
                              Text('Touchez pour changer', style: AppTypography.caption),
                            ],
                          ),
                        ),
                      ),
                    ],
                  )
                : Padding(
                    padding: const EdgeInsets.all(AppSpacing.lg),
                    child: Center(
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Icon(
                            hasImage ? Icons.qr_code_2 : Icons.add_a_photo_outlined,
                            size: 28,
                            color: AppColors.inkMuted,
                          ),
                          const SizedBox(height: AppSpacing.sm),
                          Text(
                            hasImage ? 'Code QR enregistre' : 'Ajouter une photo du code QR',
                            style: AppTypography.bodyStrong,
                            textAlign: TextAlign.center,
                          ),
                          const SizedBox(height: 2),
                          Text(
                            hasImage ? 'Touchez pour le remplacer' : 'Capture d\'ecran ou photo, pas de saisie manuelle',
                            style: AppTypography.caption,
                            textAlign: TextAlign.center,
                          ),
                        ],
                      ),
                    ),
                  ),
      ),
    );
  }
}
