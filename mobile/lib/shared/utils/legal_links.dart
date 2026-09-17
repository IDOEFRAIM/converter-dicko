import 'package:url_launcher/url_launcher.dart';

/// Documents legaux publies (retour client : "l'utilisateur n'a pas acces
/// aux CGU et a la politique de confidentialite dans l'app") — pages
/// deja live sur https://yuanpaybf.vercel.app, jamais dupliquees/recopiees
/// ici : un texte legal recopie dans l'app se desynchroniserait de la
/// version publiee des la premiere mise a jour de l'une des deux.
abstract final class LegalLinks {
  static const privacyPolicy = 'https://yuanpaybf.vercel.app/privacy';
  static const termsOfUse = 'https://yuanpaybf.vercel.app/cgu';
  static const endUserLicense = 'https://yuanpaybf.vercel.app/cluf';

  /// Ouvre dans le navigateur externe (jamais une webview interne) : ce
  /// sont des pages web completes (mise en forme, liens de contact),
  /// pas du contenu specifique a l'app.
  static Future<bool> open(String url) => launchUrl(Uri.parse(url), mode: LaunchMode.externalApplication);
}
