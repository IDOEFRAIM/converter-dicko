import '../../../shared/utils/json_decimal.dart';

/// Miroir de `BusinessType` backend.
enum BusinessType {
  importer,
  merchant,
  services,
  other;

  String get code => switch (this) {
        BusinessType.importer => 'IMPORTER',
        BusinessType.merchant => 'MERCHANT',
        BusinessType.services => 'SERVICES',
        BusinessType.other => 'OTHER',
      };

  String get label => switch (this) {
        BusinessType.importer => 'Importateur',
        BusinessType.merchant => 'Commercant',
        BusinessType.services => 'Services',
        BusinessType.other => 'Autre',
      };

  static BusinessType fromCode(String? code) {
    switch (code) {
      case 'IMPORTER':
        return BusinessType.importer;
      case 'MERCHANT':
        return BusinessType.merchant;
      case 'SERVICES':
        return BusinessType.services;
      default:
        return BusinessType.other;
    }
  }
}

/// Miroir de `BusinessProfileResponse`. Son existence (et non un champ
/// `accountType`, qui n'existe nulle part) est ce qui distingue un compte
/// "Professionnel" d'un compte "Personnel" (mission section 33).
class BusinessProfile {
  final String id;
  final String businessName;
  final BusinessType businessType;
  final String? registrationNumber;
  final String country;
  final String? city;
  final String? address;
  final DateTime createdAt;
  final DateTime updatedAt;

  const BusinessProfile({
    required this.id,
    required this.businessName,
    required this.businessType,
    required this.registrationNumber,
    required this.country,
    required this.city,
    required this.address,
    required this.createdAt,
    required this.updatedAt,
  });

  factory BusinessProfile.fromJson(Map<String, dynamic> json) {
    return BusinessProfile(
      id: json['id'] as String,
      businessName: json['businessName'] as String? ?? '',
      businessType: BusinessType.fromCode(json['businessType'] as String?),
      registrationNumber: json['registrationNumber'] as String?,
      country: json['country'] as String? ?? '',
      city: json['city'] as String?,
      address: json['address'] as String?,
      createdAt: DateTime.parse(json['createdAt'] as String),
      updatedAt: DateTime.parse(json['updatedAt'] as String),
    );
  }
}

class UpsertBusinessProfileRequest {
  final String businessName;
  final BusinessType businessType;
  final String? registrationNumber;
  final String country;
  final String? city;
  final String? address;

  const UpsertBusinessProfileRequest({
    required this.businessName,
    required this.businessType,
    this.registrationNumber,
    required this.country,
    this.city,
    this.address,
  });

  Map<String, dynamic> toJson() => {
        'businessName': businessName,
        'businessType': businessType.code,
        'registrationNumber': registrationNumber,
        'country': country,
        'city': city,
        'address': address,
      };
}

/// Miroir de `BusinessPaymentSummaryResponse`. Les 3 totaux ne comptent QUE
/// les ordres COMPLETED — convention backend deliberee (mission section 32 :
/// jamais un recalcul, jamais un ordre annule/rejete qui gonflerait un
/// "volume traite").
class BusinessPaymentSummary {
  final DateTime? periodFrom;
  final DateTime? periodTo;
  final int transferCount;
  final int completedCount;
  final int cancelledCount;
  final int rejectedCount;
  final String totalAmountXof;
  final String totalAmountCny;
  final String totalFeesXof;

  const BusinessPaymentSummary({
    required this.periodFrom,
    required this.periodTo,
    required this.transferCount,
    required this.completedCount,
    required this.cancelledCount,
    required this.rejectedCount,
    required this.totalAmountXof,
    required this.totalAmountCny,
    required this.totalFeesXof,
  });

  factory BusinessPaymentSummary.fromJson(Map<String, dynamic> json) {
    final period = json['period'] as Map<String, dynamic>? ?? const {};
    return BusinessPaymentSummary(
      periodFrom: period['from'] == null ? null : DateTime.parse(period['from'] as String),
      periodTo: period['to'] == null ? null : DateTime.parse(period['to'] as String),
      transferCount: json['transferCount'] as int? ?? 0,
      completedCount: json['completedCount'] as int? ?? 0,
      cancelledCount: json['cancelledCount'] as int? ?? 0,
      rejectedCount: json['rejectedCount'] as int? ?? 0,
      totalAmountXof: decimalStringFromJson(json['totalAmountXof']),
      totalAmountCny: decimalStringFromJson(json['totalAmountCny']),
      totalFeesXof: decimalStringFromJson(json['totalFeesXof']),
    );
  }
}
