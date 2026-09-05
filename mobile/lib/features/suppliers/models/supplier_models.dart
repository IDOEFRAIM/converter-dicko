/// Type de beneficiaire — miroir de `BeneficiaryType` backend.
enum BeneficiaryType {
  alipay,
  wechatPay,
  chineseBankAccount,
  unknown;

  static BeneficiaryType fromCode(String? code) {
    switch (code) {
      case 'ALIPAY':
        return BeneficiaryType.alipay;
      case 'WECHAT_PAY':
        return BeneficiaryType.wechatPay;
      case 'CHINESE_BANK_ACCOUNT':
        return BeneficiaryType.chineseBankAccount;
      default:
        return BeneficiaryType.unknown;
    }
  }

  String get code => switch (this) {
        BeneficiaryType.alipay => 'ALIPAY',
        BeneficiaryType.wechatPay => 'WECHAT_PAY',
        BeneficiaryType.chineseBankAccount => 'CHINESE_BANK_ACCOUNT',
        BeneficiaryType.unknown => 'UNKNOWN',
      };

  /// Libelle francais — miroir de `BENEFICIARY_TYPE_LABELS` cote Angular.
  String get label => switch (this) {
        BeneficiaryType.alipay => 'Alipay',
        BeneficiaryType.wechatPay => 'WeChat Pay',
        BeneficiaryType.chineseBankAccount => 'Compte bancaire chinois',
        BeneficiaryType.unknown => 'Inconnu',
      };
}

enum SupplierStatus {
  active,
  inactive;

  static SupplierStatus fromCode(String? code) => code == 'INACTIVE' ? SupplierStatus.inactive : SupplierStatus.active;
}

/// Ligne de liste — miroir de `SupplierSummaryResponse` / `SupplierSummary`
/// (Angular). Le numero de compte complet n'est jamais expose ici, seulement
/// masque (ex. "******1234").
class SupplierSummary {
  final String id;
  final BeneficiaryType type;
  final String displayName;
  final String? country;
  final String? city;
  final String maskedAccountNumber;
  final String? purpose;
  final bool favorite;
  final SupplierStatus status;
  final DateTime createdAt;

  const SupplierSummary({
    required this.id,
    required this.type,
    required this.displayName,
    required this.country,
    required this.city,
    required this.maskedAccountNumber,
    required this.purpose,
    required this.favorite,
    required this.status,
    required this.createdAt,
  });

  factory SupplierSummary.fromJson(Map<String, dynamic> json) {
    return SupplierSummary(
      id: json['id'] as String,
      type: BeneficiaryType.fromCode(json['type'] as String?),
      displayName: json['displayName'] as String? ?? '',
      country: json['country'] as String?,
      city: json['city'] as String?,
      maskedAccountNumber: json['maskedAccountNumber'] as String? ?? '',
      purpose: json['purpose'] as String?,
      favorite: json['favorite'] as bool? ?? false,
      status: SupplierStatus.fromCode(json['status'] as String?),
      createdAt: DateTime.parse(json['createdAt'] as String),
    );
  }
}
