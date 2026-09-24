import 'package:url_launcher/url_launcher.dart';

import '../../core/config/app_config.dart';

/// Ouvre une conversation WhatsApp preremplie avec le numero de confirmation
/// (retour client sept. 2026, voir `AppConfig.whatsAppConfirmationThresholdXof`).
/// `https://wa.me/` fonctionne sans configuration native supplementaire :
/// ouvre l'app WhatsApp si installee, sinon le navigateur -- jamais d'echec
/// silencieux si l'app est absente.
Future<bool> launchWhatsAppConfirmation({required String message}) {
  final phone = AppConfig.whatsAppConfirmationPhoneE164.replaceFirst('+', '');
  final uri = Uri.parse('https://wa.me/$phone?text=${Uri.encodeComponent(message)}');
  return launchUrl(uri, mode: LaunchMode.externalApplication);
}
