import 'package:flutter/material.dart';

/// Indicatif telephonique international pour [PhoneNumberField] -- liste
/// volontairement limitee aux pays les plus pertinents pour cette
/// plateforme (Burkina Faso en premier/par defaut) plutot qu'un annuaire
/// mondial complet, pour garder le selecteur rapide a parcourir.
class _CountryCode {
  final String flag;
  final String dialCode;
  final String name;

  const _CountryCode(this.flag, this.dialCode, this.name);
}

const _countryCodes = <_CountryCode>[
  _CountryCode('🇧🇫', '+226', 'Burkina Faso'),
  _CountryCode('🇨🇮', '+225', "Cote d'Ivoire"),
  _CountryCode('🇲🇱', '+223', 'Mali'),
  _CountryCode('🇸🇳', '+221', 'Senegal'),
  _CountryCode('🇳🇪', '+227', 'Niger'),
  _CountryCode('🇹🇬', '+228', 'Togo'),
  _CountryCode('🇧🇯', '+229', 'Benin'),
  _CountryCode('🇬🇳', '+224', 'Guinee'),
  _CountryCode('🇬🇭', '+233', 'Ghana'),
  _CountryCode('🇳🇬', '+234', 'Nigeria'),
  _CountryCode('🇨🇳', '+86', 'Chine'),
  _CountryCode('🇫🇷', '+33', 'France'),
];

/// Champ telephone compose d'un selecteur d'indicatif pays + d'un numero
/// local : l'utilisateur ne tape jamais lui-meme le "+" ni l'indicatif
/// (retour beta-testeur sept. 2026 : "le fait de devoir ajouter le +...
/// est fatiguant, l'utilisateur rentre juste le code pays plus son
/// numero"). [controller] recoit et garde en permanence le numero complet
/// au format E.164 (ex. "+22670026919"), exactement ce qu'attendent
/// [Validators.phoneE164]/[Validators.normalizePhone] et le backend --
/// aucun autre code appelant (login, inscription) n'a besoin de changer.
class PhoneNumberField extends StatefulWidget {
  final TextEditingController controller;
  final String labelText;
  final TextInputAction? textInputAction;
  final ValueChanged<String>? onFieldSubmitted;
  final String? Function(String? fullE164Value)? validator;
  final AutovalidateMode? autovalidateMode;

  const PhoneNumberField({
    super.key,
    required this.controller,
    this.labelText = 'Numero de telephone',
    this.textInputAction,
    this.onFieldSubmitted,
    this.validator,
    this.autovalidateMode,
  });

  @override
  State<PhoneNumberField> createState() => _PhoneNumberFieldState();
}

class _PhoneNumberFieldState extends State<PhoneNumberField> {
  late _CountryCode _selected;
  late final TextEditingController _localController;

  @override
  void initState() {
    super.initState();
    _selected = _countryCodes.first;
    final existing = widget.controller.text.trim();
    var localDigits = '';
    // Si le controleur porte deja une valeur (ex. re-ouverture d'un formulaire), on retrouve
    // l'indicatif correspondant plutot que d'ecraser silencieusement un numero deja saisi.
    for (final code in _countryCodes) {
      if (existing.startsWith(code.dialCode)) {
        _selected = code;
        localDigits = existing.substring(code.dialCode.length);
        break;
      }
    }
    _localController = TextEditingController(text: localDigits);
    _localController.addListener(_syncFullNumber);
  }

  void _syncFullNumber() {
    final digits = _localController.text.trim();
    widget.controller.text = digits.isEmpty ? '' : '${_selected.dialCode}$digits';
  }

  @override
  void dispose() {
    _localController.removeListener(_syncFullNumber);
    _localController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SizedBox(
          width: 118,
          child: DropdownButtonFormField<_CountryCode>(
            initialValue: _selected,
            decoration: const InputDecoration(labelText: 'Indicatif'),
            isExpanded: true,
            items: [
              for (final code in _countryCodes)
                DropdownMenuItem(
                  value: code,
                  child: Text('${code.flag} ${code.dialCode}', overflow: TextOverflow.ellipsis),
                ),
            ],
            onChanged: (value) {
              if (value == null) return;
              setState(() => _selected = value);
              _syncFullNumber();
            },
          ),
        ),
        const SizedBox(width: 8),
        Expanded(
          child: TextFormField(
            controller: _localController,
            keyboardType: TextInputType.phone,
            textInputAction: widget.textInputAction,
            autovalidateMode: widget.autovalidateMode,
            onFieldSubmitted: widget.onFieldSubmitted,
            decoration: InputDecoration(labelText: widget.labelText, hintText: '70026919'),
            validator: widget.validator == null ? null : (_) => widget.validator!(widget.controller.text),
          ),
        ),
      ],
    );
  }
}
