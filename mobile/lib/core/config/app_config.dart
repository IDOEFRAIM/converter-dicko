import 'app_environment.dart';

/// Configuration centrale de l'application (une seule source de verite pour
/// l'URL de base de l'API). Aucun service metier ne doit construire ou
/// connaitre une URL en dur — tout passe par [AppConfig.apiBaseUrl].
///
/// [apiBaseUrl] est l'ORIGINE seule (schema + hote), sans le prefixe `/api` —
/// c'est [ApiClient] qui l'ajoute une seule fois. Les couches "data" de
/// chaque feature n'ecrivent donc que le chemin relatif au prefixe `/api`
/// (ex. `/v1/rates/history`, `/auth/login`), exactement comme
/// `environment.apiBaseUrl = '/api'` cote Angular.
///
/// La valeur DEV pointe vers un tunnel ngrok temporaire (backend local
/// expose pour developpement mobile). Elle est deliberement ISOLEE ici : la
/// remplacer (changement de tunnel, passage a un backend deploye) ne touche
/// aucun autre fichier du projet.
class AppConfig {
  final AppEnvironment environment;
  final String apiBaseUrl;

  const AppConfig._({required this.environment, required this.apiBaseUrl});

  /// Construit la configuration active a partir de `--dart-define` :
  /// ```
  /// flutter run --dart-define=APP_ENV=dev
  /// flutter run --dart-define=APP_ENV=production --dart-define=API_BASE_URL=https://api.example.com
  /// ```
  /// Sans definition explicite, retombe sur DEV avec le tunnel ngrok temporaire.
  factory AppConfig.fromDefine() {
    const envName = String.fromEnvironment('APP_ENV', defaultValue: 'dev');
    const overrideUrl = String.fromEnvironment('API_BASE_URL');
    final environment = AppEnvironment.fromName(envName);

    if (overrideUrl.isNotEmpty) {
      return AppConfig._(environment: environment, apiBaseUrl: overrideUrl);
    }
    return AppConfig._(environment: environment, apiBaseUrl: _defaultBaseUrlFor(environment));
  }

  static String _defaultBaseUrlFor(AppEnvironment environment) {
    switch (environment) {
      case AppEnvironment.production:
        // A renseigner lors du deploiement reel — volontairement laisse invalide
        // pour ne jamais pointer silencieusement vers un mauvais backend.
        return 'https://CHANGE_ME.production.invalid';
      case AppEnvironment.staging:
        return 'https://CHANGE_ME.staging.invalid';
      case AppEnvironment.dev:
        // DEV UNIQUEMENT : tunnel ngrok temporaire vers le backend local. Remplacer
        // via --dart-define=API_BASE_URL=... quand le tunnel change, sans toucher au code.
        return 'https://shortness-expensive-fidgety.ngrok-free.dev';
    }
  }

  bool get isProduction => environment == AppEnvironment.production;
}
