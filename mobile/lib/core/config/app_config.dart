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
/// Les valeurs DEV/STAGING/PRODUCTION sont deliberement ISOLEES ici (une
/// seule ligne a changer par environnement, voir [_defaultBaseUrlFor]) :
/// les remplacer (nouvelle IP, nouveau domaine) ne touche aucun autre
/// fichier du projet.
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
        // Sous-domaine dedie, en HTTPS, qui pointe directement sur le backend
        // (jamais via le relai nginx du frontend web) -- voir DEPLOY.md §7 et
        // docker-compose.prod.yml (backend publie sur 127.0.0.1:8080, Caddy
        // termine le TLS devant pour ce domaine).
        return 'https://api.yuanpay.space';
      case AppEnvironment.staging:
        return 'http://178.105.95.145:4300';
      case AppEnvironment.dev:
        // DEV UNIQUEMENT : backend deploye sur Hetzner, en HTTP direct via IP (pas
        // encore de domaine/Caddy devant). Voir network_security_config.xml (debug)
        // pour l'autorisation cleartext explicite de cette IP. Remplacer via
        // --dart-define=API_BASE_URL=... si l'IP ou le port changent.
        return 'http://178.105.95.145:4300';
    }
  }

  bool get isProduction => environment == AppEnvironment.production;

  /// Seuil (XOF) a partir duquel un transfert doit etre confirme sur WhatsApp
  /// (retour client sept. 2026 : "a partir de plus de 02 millions tu dois
  /// etre ramene sur WhatsApp pour confirmer ton ordre") -- une simple
  /// orientation vers un canal humain pour les gros montants, jamais une
  /// regle metier serveur : l'ordre est deja cree normalement, voir
  /// `WhatsAppConfirmation` (order_create_page).
  static const int whatsAppConfirmationThresholdXof = 2000000;

  /// Numero de confirmation WhatsApp, au format E.164 (Burkina Faso, +226)
  /// attendu par le lien `https://wa.me/` -- retour client sept. 2026 :
  /// l'ancien numero (71 00 25 25) etait errone, le bon est +226 65 38 23 37.
  /// Egalement affiche en texte (pas seulement le lien) dans
  /// order_create_page.dart : les deux doivent rester synchronises.
  static const String whatsAppConfirmationPhoneE164 = '+22665382337';

  /// Desactive volontairement (retour client sept. 2026 : les ANR observes
  /// pendant les tests -- dus a la machine de dev sursollicitee, pas a la
  /// config Google elle-meme -- ont pousse a retirer le bouton plutot que
  /// continuer a s'en inquieter). `GoogleAuthClient`/`GOOGLE_SERVER_CLIENT_ID`
  /// restent en place tels quels pour une reactivation en changeant
  /// uniquement cette ligne, sans autre travail de configuration.
  bool get googleSignInAvailable => false;
}
