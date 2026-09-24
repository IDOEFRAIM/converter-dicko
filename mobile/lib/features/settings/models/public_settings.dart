import '../../../shared/utils/json_decimal.dart';

/// Miroir de `PublicSettingsResponse` (`GET /api/settings/public`, sans auth).
/// Purement une commodite d'affichage/pre-remplissage de formulaire — chaque
/// regle reste revalidee cote serveur au moment de l'action reelle (mission
/// section 9 du document de reference API), jamais la seule barriere.
class PublicSettings {
  final String minOrderAmountCfa;
  final String maxOrderAmountCfa;
  final int rateLockDurationMinutes;
  final int maxProofFileSizeBytes;
  final int maxProofsPerPayment;
  final List<String> enabledPaymentMethods;
  final bool requirePaymentProof;

  /// Instructions affichees au client pour savoir ou/comment envoyer son paiement (Mobile
  /// Money, banque...) avant de le declarer -- voir migration V39 backend. Vide seulement si
  /// l'administration n'a pas encore configure ce texte.
  final String paymentInstructionsText;

  const PublicSettings({
    required this.minOrderAmountCfa,
    required this.maxOrderAmountCfa,
    required this.rateLockDurationMinutes,
    required this.maxProofFileSizeBytes,
    required this.maxProofsPerPayment,
    required this.enabledPaymentMethods,
    required this.requirePaymentProof,
    required this.paymentInstructionsText,
  });

  factory PublicSettings.fromJson(Map<String, dynamic> json) {
    return PublicSettings(
      minOrderAmountCfa: decimalStringFromJson(json['minOrderAmountCfa']),
      maxOrderAmountCfa: decimalStringFromJson(json['maxOrderAmountCfa']),
      rateLockDurationMinutes: json['rateLockDurationMinutes'] as int? ?? 30,
      maxProofFileSizeBytes: json['maxProofFileSizeBytes'] as int? ?? 5242880,
      maxProofsPerPayment: json['maxProofsPerPayment'] as int? ?? 3,
      enabledPaymentMethods: (json['enabledPaymentMethods'] as List<dynamic>? ?? const ['MOBILE_MONEY'])
          .map((e) => e as String)
          .toList(growable: false),
      requirePaymentProof: json['requirePaymentProof'] as bool? ?? true,
      paymentInstructionsText: json['paymentInstructionsText'] as String? ?? '',
    );
  }
}
