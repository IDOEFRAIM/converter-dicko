/// Verification d'identite en libre-service (remarque produit #6) — miroir de
/// `com.converter.kyc`. Revue manuelle interne cote backend : le mobile
/// soumet un dossier et suit son statut, rien de plus.

enum KycDocumentType {
  nationalId,
  passport,
  residencePermit;

  String get code => switch (this) {
        KycDocumentType.nationalId => 'NATIONAL_ID',
        KycDocumentType.passport => 'PASSPORT',
        KycDocumentType.residencePermit => 'RESIDENCE_PERMIT',
      };

  String get label => switch (this) {
        KycDocumentType.nationalId => "Carte nationale d'identite (CNIB)",
        KycDocumentType.passport => 'Passeport',
        KycDocumentType.residencePermit => 'Carte de sejour',
      };

  /// Le passeport n'a pas de verso pertinent.
  bool get needsBack => this != KycDocumentType.passport;

  static KycDocumentType? fromCode(String? code) => switch (code) {
        'NATIONAL_ID' => KycDocumentType.nationalId,
        'PASSPORT' => KycDocumentType.passport,
        'RESIDENCE_PERMIT' => KycDocumentType.residencePermit,
        _ => null,
      };
}

enum KycStatus {
  /// Aucun dossier jamais soumis.
  none,
  pending,
  approved,
  rejected;

  static KycStatus fromCode(String? code) => switch (code) {
        'PENDING' => KycStatus.pending,
        'APPROVED' => KycStatus.approved,
        'REJECTED' => KycStatus.rejected,
        _ => KycStatus.none,
      };
}

class KycSubmission {
  final KycStatus status;
  final KycDocumentType? documentType;
  final DateTime submittedAt;
  final DateTime? reviewedAt;
  final String? rejectionReason;

  const KycSubmission({
    required this.status,
    required this.documentType,
    required this.submittedAt,
    required this.reviewedAt,
    required this.rejectionReason,
  });

  factory KycSubmission.fromJson(Map<String, dynamic> json) {
    return KycSubmission(
      status: KycStatus.fromCode(json['status'] as String?),
      documentType: KycDocumentType.fromCode(json['documentType'] as String?),
      submittedAt: DateTime.parse(json['submittedAt'] as String),
      reviewedAt: json['reviewedAt'] == null ? null : DateTime.parse(json['reviewedAt'] as String),
      rejectionReason: json['rejectionReason'] as String?,
    );
  }
}
