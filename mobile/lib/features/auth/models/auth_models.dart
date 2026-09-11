import '../../../shared/models/current_user.dart';

class LoginRequest {
  final String phone;
  final String password;

  const LoginRequest({required this.phone, required this.password});

  Map<String, dynamic> toJson() => {'phone': phone, 'password': password};
}

class RegisterRequest {
  final String phone;
  final String password;
  final String firstName;
  final String lastName;

  /// Habillage choisi a l'inscription — `null` => PRO cote backend (design
  /// sobre actuel), jamais une bascule silencieuse vers un theme non choisi.
  final ExperienceProfile? experienceProfile;

  const RegisterRequest({
    required this.phone,
    required this.password,
    required this.firstName,
    required this.lastName,
    this.experienceProfile,
  });

  Map<String, dynamic> toJson() => {
        'phone': phone,
        'password': password,
        'firstName': firstName,
        'lastName': lastName,
        'experienceProfile': experienceProfile?.code,
      };
}

/// Reponse de connexion/inscription — miroir de `AuthResponse` backend.
class AuthResult {
  final String accessToken;
  final String tokenType;
  final int expiresInSeconds;
  final CurrentUser user;

  const AuthResult({
    required this.accessToken,
    required this.tokenType,
    required this.expiresInSeconds,
    required this.user,
  });

  factory AuthResult.fromJson(Map<String, dynamic> json) {
    return AuthResult(
      accessToken: json['accessToken'] as String,
      tokenType: json['tokenType'] as String? ?? 'Bearer',
      expiresInSeconds: json['expiresInSeconds'] as int? ?? 0,
      user: CurrentUser.fromJson(json['user'] as Map<String, dynamic>),
    );
  }
}

/// Reponse de `POST /auth/google` — miroir de `GoogleSignInResponse`
/// backend. Deux issues, jamais melangees (voir sa Javadoc) :
///  - [accountExists] `true` : [auth] porte le jeton, le reste est `null`.
///  - [accountExists] `false` : [auth] est `null` ; [email]/[suggestedFirstName]/
///    [suggestedLastName] pre-remplissent l'ecran "votre numero".
class GoogleSignInResult {
  final bool accountExists;
  final AuthResult? auth;
  final String? email;
  final String? suggestedFirstName;
  final String? suggestedLastName;

  const GoogleSignInResult({
    required this.accountExists,
    this.auth,
    this.email,
    this.suggestedFirstName,
    this.suggestedLastName,
  });

  factory GoogleSignInResult.fromJson(Map<String, dynamic> json) {
    return GoogleSignInResult(
      accountExists: json['accountExists'] as bool? ?? false,
      auth: json['auth'] == null ? null : AuthResult.fromJson(json['auth'] as Map<String, dynamic>),
      email: json['email'] as String?,
      suggestedFirstName: json['suggestedFirstName'] as String?,
      suggestedLastName: json['suggestedLastName'] as String?,
    );
  }
}

/// Finalise un compte apres un `POST /auth/google` sans correspondance —
/// miroir de `CompleteGoogleSignUpRequest`. Reenvoie le MEME jeton Google
/// (revérifié cote backend), jamais un jeton intermediaire maison.
class CompleteGoogleSignUpRequest {
  final String idToken;
  final String phone;
  final String firstName;
  final String lastName;
  final ExperienceProfile? experienceProfile;

  const CompleteGoogleSignUpRequest({
    required this.idToken,
    required this.phone,
    required this.firstName,
    required this.lastName,
    this.experienceProfile,
  });

  Map<String, dynamic> toJson() => {
        'idToken': idToken,
        'phone': phone,
        'firstName': firstName,
        'lastName': lastName,
        'experienceProfile': experienceProfile?.code,
      };
}
