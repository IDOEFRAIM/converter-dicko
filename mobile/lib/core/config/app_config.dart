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

  /// Client ID Web OAuth 2.0 Google — le MEME que `GOOGLE_OAUTH_CLIENT_ID`
  /// cote backend (voir `GoogleAuthProperties`), attendu par le SDK Google
  /// Sign-In comme "Server Client ID" (c'est ce qui permet d'obtenir un ID
  /// token verifiable par le serveur, pas seulement un access token local).
  /// Vide par defaut : [googleSignInAvailable] vaut alors `false` et l'UI
  /// masque le bouton "Continuer avec Google" plutot que d'exposer une
  /// fonctionnalite non configuree qui echouerait au premier tap.
  final String googleServerClientId;

  const AppConfig._({required this.environment, required this.apiBaseUrl, required this.googleServerClientId});

  /// Construit la configuration active a partir de `--dart-define` :
  /// ```
  /// flutter run --dart-define=APP_ENV=dev
  /// flutter run --dart-define=APP_ENV=production --dart-define=API_BASE_URL=https://api.example.com \
  ///   --dart-define=GOOGLE_SERVER_CLIENT_ID=xxxxxxxx.apps.googleusercontent.com
  /// ```
  /// Sans definition explicite, retombe sur DEV avec le tunnel ngrok temporaire.
  factory AppConfig.fromDefine() {
    const envName = String.fromEnvironment('APP_ENV', defaultValue: 'dev');
    const overrideUrl = String.fromEnvironment('API_BASE_URL');
    const googleServerClientId = String.fromEnvironment('GOOGLE_SERVER_CLIENT_ID');
    final environment = AppEnvironment.fromName(envName);

    final apiBaseUrl = overrideUrl.isNotEmpty ? overrideUrl : _defaultBaseUrlFor(environment);
    return AppConfig._(
      environment: environment,
      apiBaseUrl: apiBaseUrl,
      googleServerClientId: googleServerClientId,
    );
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

  /// Desactive volontairement (retour client sept. 2026 : les ANR observes
  /// pendant les tests -- dus a la machine de dev sursollicitee, pas a la
  /// config Google elle-meme -- ont pousse a retirer le bouton plutot que
  /// continuer a s'en inquieter). `GoogleAuthClient`/`GOOGLE_SERVER_CLIENT_ID`
  /// restent en place tels quels pour une reactivation en changeant
  /// uniquement cette ligne, sans autre travail de configuration.
  bool get googleSignInAvailable => false;
}
