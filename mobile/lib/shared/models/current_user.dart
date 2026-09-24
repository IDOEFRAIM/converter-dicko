/// Habillage mobile choisi par le client — miroir de
/// `com.converter.user.domain.ExperienceProfile`. Pilote uniquement les
/// couleurs/le ton de l'interface, jamais le taux, les frais ni aucune regle
/// metier : memes calculs, meme backend pour les trois valeurs.
enum ExperienceProfile {
  pro,
  studentMale,
  studentFemale;

  String get code => switch (this) {
        ExperienceProfile.pro => 'PRO',
        ExperienceProfile.studentMale => 'STUDENT_MALE',
        ExperienceProfile.studentFemale => 'STUDENT_FEMALE',
      };

  static ExperienceProfile fromCode(String? code) {
    switch (code) {
      case 'STUDENT_MALE':
        return ExperienceProfile.studentMale;
      case 'STUDENT_FEMALE':
        return ExperienceProfile.studentFemale;
      default:
        return ExperienceProfile.pro;
    }
  }
}

/// Compte utilisateur connecte — miroir de `UserResponse` / `CurrentUser`
/// (backend `com.converter.user.dto.UserResponse`, Angular `user.model.ts`).
///
/// La verite sur qui est l'utilisateur et quel role il a vient exclusivement
/// du backend (ce type), jamais decodee ou deduite du contenu du jeton JWT
/// cote client.
class CurrentUser {
  final String id;
  final String phone;
  final String firstName;
  final String lastName;
  final String? email;
  final String status;
  final List<String> roles;
  final DateTime createdAt;
  final DateTime? lastLoginAt;
  final ExperienceProfile experienceProfile;

  /// Identite verifiee (KYC) — requis pour creer un ordre au-dela du seuil
  /// (remarque produit #6, parcours `/more/kyc`).
  final bool kycVerified;

  /// Faux pour un compte cree via Google Sign-In (jamais de mot de passe) —
  /// pilote l'ecran de suppression de compte : demander une re-confirmation
  /// par mot de passe uniquement si vrai (voir `MorePage`/`AccountDeletionService`).
  final bool hasPassword;

  const CurrentUser({
    required this.id,
    required this.phone,
    required this.firstName,
    required this.lastName,
    required this.email,
    required this.status,
    required this.roles,
    required this.createdAt,
    required this.lastLoginAt,
    required this.experienceProfile,
    required this.kycVerified,
    required this.hasPassword,
  });

  bool get isAdmin => roles.contains('ADMIN');

  bool get isActive => status == 'ACTIVE';

  String get fullName => '$firstName $lastName'.trim();

  factory CurrentUser.fromJson(Map<String, dynamic> json) {
    return CurrentUser(
      id: json['id'] as String,
      phone: json['phone'] as String,
      firstName: json['firstName'] as String,
      lastName: json['lastName'] as String,
      email: json['email'] as String?,
      status: json['status'] as String,
      roles: (json['roles'] as List<dynamic>? ?? const []).map((e) => e as String).toList(growable: false),
      createdAt: DateTime.parse(json['createdAt'] as String),
      lastLoginAt: json['lastLoginAt'] == null ? null : DateTime.parse(json['lastLoginAt'] as String),
      experienceProfile: ExperienceProfile.fromCode(json['experienceProfile'] as String?),
      kycVerified: json['kycVerified'] as bool? ?? false,
      hasPassword: json['hasPassword'] as bool? ?? true,
    );
  }
}
