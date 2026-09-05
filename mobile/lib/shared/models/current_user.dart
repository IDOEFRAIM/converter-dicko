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
    );
  }
}
