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
