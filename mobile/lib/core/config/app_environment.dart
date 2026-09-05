/// Environnements de deploiement de l'application.
///
/// La valeur active est choisie a la compilation via `--dart-define=APP_ENV=...`
/// (voir [AppConfig.fromDefine]), jamais codee en dur dans un service metier.
enum AppEnvironment {
  dev,
  staging,
  production;

  static AppEnvironment fromName(String? name) {
    switch (name) {
      case 'staging':
        return AppEnvironment.staging;
      case 'production':
      case 'prod':
        return AppEnvironment.production;
      case 'dev':
      default:
        return AppEnvironment.dev;
    }
  }
}
