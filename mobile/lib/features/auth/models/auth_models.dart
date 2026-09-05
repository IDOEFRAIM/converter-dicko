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

  const RegisterRequest({
    required this.phone,
    required this.password,
    required this.firstName,
    required this.lastName,
  });

  Map<String, dynamic> toJson() => {
        'phone': phone,
        'password': password,
        'firstName': firstName,
        'lastName': lastName,
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
