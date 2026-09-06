/// Validateurs de formulaire miroir des contraintes Bean Validation reelles
/// du backend (DTOs sous `backend/src/main/java/com/converter/**/dto`) —
/// jamais une regle inventee ou plus laxiste que le serveur. Le backend
/// reste la seule autorite finale (toute regle est re-validee cote serveur),
/// mais l'utilisateur doit voir la meme contrainte ici, pas apres un aller-
/// retour reseau.
abstract final class Validators {
  /// Format E.164 : miroir exact de `PhoneNumberValidator.E164` backend
  /// (`^\+[1-9][0-9]{7,14}$`), applique apres avoir retire les separateurs de
  /// saisie courants — memes deux etapes que `PhoneNumberValidator.normalize`.
  static final _e164 = RegExp(r'^\+[1-9][0-9]{7,14}$');
  static final _phoneSeparators = RegExp(r'[\s.()\-]');

  /// Email HTML5 (RFC 5322 simplifie) — plus strict qu'un simple "contient un
  /// @", coherent avec l'intention de `@Email` (Hibernate Validator) cote
  /// backend sans en dupliquer l'implementation exacte (le serveur reste
  /// l'autorite finale).
  static final _email = RegExp(
    r"^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?"
    r'(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)+$',
  );

  /// Retire les separateurs de saisie courants avant validation/envoi — miroir
  /// de `PhoneNumberValidator.normalize` : "+225 07 00 00 00 00" et
  /// "+2250700000000" doivent designer le meme numero.
  static String normalizePhone(String raw) => raw.replaceAll(_phoneSeparators, '');

  static String? required(String? value, String message) =>
      (value == null || value.trim().isEmpty) ? message : null;

  /// Numero de telephone au format international E.164 — utilise pour le
  /// numero de COMPTE d'un utilisateur (connexion/inscription), la seule
  /// place ou le backend applique `@PhoneNumber` (contrairement au telephone
  /// d'un fournisseur ou au numero du payeur d'un paiement, qui ne sont que
  /// des `@Size` libres cote backend — ne jamais sur-contraindre ceux-la).
  static String? phoneE164(String? value) {
    final trimmed = value?.trim() ?? '';
    if (trimmed.isEmpty) {
      return 'Le numero de telephone est obligatoire.';
    }
    if (!_e164.hasMatch(normalizePhone(trimmed))) {
      return 'Numero invalide : format international attendu, ex. +2250700000000.';
    }
    return null;
  }

  /// Email — miroir de `@Email` + `@Size(max: maxLength)`. `required: false`
  /// par defaut car chaque email de ce backend (fournisseur...) est optionnel.
  static String? email(String? value, {bool required = false, int maxLength = 160}) {
    final trimmed = value?.trim() ?? '';
    if (trimmed.isEmpty) {
      return required ? "L'email est obligatoire." : null;
    }
    if (trimmed.length > maxLength) {
      return "L'email ne doit pas depasser $maxLength caracteres.";
    }
    if (!_email.hasMatch(trimmed)) {
      return 'Adresse email invalide.';
    }
    return null;
  }

  /// Mot de passe — miroir exact de `@Size(min: 8, max: 72)` sur
  /// `RegisterRequest.password` (aucune regle de "complexite" : le backend
  /// documente explicitement ce choix, ne pas en ajouter une cote mobile).
  static String? password(String? value) {
    final v = value ?? '';
    if (v.isEmpty) {
      return 'Le mot de passe est obligatoire.';
    }
    if (v.length < 8 || v.length > 72) {
      return 'Le mot de passe doit contenir entre 8 et 72 caracteres.';
    }
    return null;
  }

  /// Chaine obligatoire avec longueur maximale — miroir de
  /// `@NotBlank @Size(max: maxLength)`.
  static String? requiredMaxLength(String? value, int maxLength, {required String label}) {
    final trimmed = value?.trim() ?? '';
    if (trimmed.isEmpty) {
      return '$label est obligatoire.';
    }
    if (trimmed.length > maxLength) {
      return '$label ne doit pas depasser $maxLength caracteres.';
    }
    return null;
  }

  /// Chaine optionnelle avec longueur maximale — miroir de `@Size(max:
  /// maxLength)` seul. Utile en filet de securite meme quand le widget
  /// utilise deja `maxLength:` (saisie bloquee au-dela), par exemple pour un
  /// champ pre-rempli par une valeur existante deja trop longue.
  static String? optionalMaxLength(String? value, int maxLength, {required String label}) {
    if (value != null && value.length > maxLength) {
      return '$label ne doit pas depasser $maxLength caracteres.';
    }
    return null;
  }

  /// Montant decimal positif — miroir de `@Positive @Digits(integer: 17,
  /// fraction: 2)` (Quote/PayAgain/SubmitPayment). Jamais de conversion en
  /// `double` : uniquement une verification syntaxique par regex + parse
  /// (mission section 34).
  static String? positiveAmount(String? value, {String label = 'Le montant'}) {
    final trimmed = value?.trim() ?? '';
    if (trimmed.isEmpty) {
      return '$label est obligatoire.';
    }
    if (!RegExp(r'^\d{1,17}(\.\d{1,2})?$').hasMatch(trimmed)) {
      return '$label doit etre un nombre positif avec au plus 2 decimales.';
    }
    if (num.parse(trimmed) <= 0) {
      return '$label doit etre strictement positif.';
    }
    return null;
  }

  /// Taux cible d'une alerte — miroir de `@DecimalMin("0.000001")` sur
  /// `CreateRateAlertRequest.targetRate` (pas de contrainte `@Digits`
  /// explicite cote backend pour ce champ, seulement une positivite stricte).
  static String? positiveRate(String? value, {String label = 'Le taux cible'}) {
    final trimmed = value?.trim() ?? '';
    if (trimmed.isEmpty) {
      return '$label est obligatoire.';
    }
    final parsed = num.tryParse(trimmed);
    if (parsed == null || parsed < 0.000001) {
      return '$label doit etre strictement positif.';
    }
    return null;
  }
}
