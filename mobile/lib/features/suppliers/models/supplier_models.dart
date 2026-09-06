import '../../../shared/models/purpose.dart';

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

  static const List<BeneficiaryType> selectableOptions = [
    BeneficiaryType.alipay,
    BeneficiaryType.wechatPay,
    BeneficiaryType.chineseBankAccount,
  ];
}

/// Devise du compte fournisseur — miroir de `Currency` backend.
enum SupplierCurrency {
  xof,
  cny;

  String get code => this == SupplierCurrency.cny ? 'CNY' : 'XOF';

  static SupplierCurrency fromCode(String? code) => code == 'CNY' ? SupplierCurrency.cny : SupplierCurrency.xof;
}

enum SupplierStatus {
  active,
  inactive;

  String get code => this == SupplierStatus.inactive ? 'INACTIVE' : 'ACTIVE';

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
  final Purpose? purpose;
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
      purpose: Purpose.fromCode(json['purpose'] as String?),
      favorite: json['favorite'] as bool? ?? false,
      status: SupplierStatus.fromCode(json['status'] as String?),
      createdAt: DateTime.parse(json['createdAt'] as String),
    );
  }
}

/// Detail complet — miroir de `SupplierDetailResponse`. Le numero de compte
/// en clair n'apparait QUE dans cette representation, jamais dans une liste.
class SupplierDetail {
  final String id;
  final BeneficiaryType type;
  final String displayName;
  final String? legalName;
  final String? phone;
  final String? email;
  final String? country;
  final String? city;
  final String? province;
  final String? bankName;
  final String? bankBranch;
  final String? accountName;
  final String accountNumber;
  final String? bankAddress;
  final String? swiftCode;
  final SupplierCurrency currency;
  final Purpose? purpose;
  final String? notes;
  final bool favorite;
  final SupplierStatus status;
  final DateTime createdAt;
  final DateTime updatedAt;

  const SupplierDetail({
    required this.id,
    required this.type,
    required this.displayName,
    required this.legalName,
    required this.phone,
    required this.email,
    required this.country,
    required this.city,
    required this.province,
    required this.bankName,
    required this.bankBranch,
    required this.accountName,
    required this.accountNumber,
    required this.bankAddress,
    required this.swiftCode,
    required this.currency,
    required this.purpose,
    required this.notes,
    required this.favorite,
    required this.status,
    required this.createdAt,
    required this.updatedAt,
  });

  factory SupplierDetail.fromJson(Map<String, dynamic> json) {
    return SupplierDetail(
      id: json['id'] as String,
      type: BeneficiaryType.fromCode(json['type'] as String?),
      displayName: json['displayName'] as String? ?? '',
      legalName: json['legalName'] as String?,
      phone: json['phone'] as String?,
      email: json['email'] as String?,
      country: json['country'] as String?,
      city: json['city'] as String?,
      province: json['province'] as String?,
      bankName: json['bankName'] as String?,
      bankBranch: json['bankBranch'] as String?,
      accountName: json['accountName'] as String?,
      accountNumber: json['accountNumber'] as String? ?? '',
      bankAddress: json['bankAddress'] as String?,
      swiftCode: json['swiftCode'] as String?,
      currency: SupplierCurrency.fromCode(json['currency'] as String?),
      purpose: Purpose.fromCode(json['purpose'] as String?),
      notes: json['notes'] as String?,
      favorite: json['favorite'] as bool? ?? false,
      status: SupplierStatus.fromCode(json['status'] as String?),
      createdAt: DateTime.parse(json['createdAt'] as String),
      updatedAt: DateTime.parse(json['updatedAt'] as String),
    );
  }
}

/// Corps de creation/mise a jour — meme forme pour `POST` et `PUT`.
class SupplierRequest {
  final BeneficiaryType type;
  final String displayName;
  final String? legalName;
  final String? phone;
  final String? email;
  final String? country;
  final String? city;
  final String? province;
  final String? bankName;
  final String? bankBranch;
  final String? accountName;
  final String accountNumber;
  final String? bankAddress;
  final String? swiftCode;
  final SupplierCurrency currency;
  final Purpose? purpose;
  final String? notes;

  const SupplierRequest({
    required this.type,
    required this.displayName,
    this.legalName,
    this.phone,
    this.email,
    this.country,
    this.city,
    this.province,
    this.bankName,
    this.bankBranch,
    this.accountName,
    required this.accountNumber,
    this.bankAddress,
    this.swiftCode,
    required this.currency,
    this.purpose,
    this.notes,
  });

  Map<String, dynamic> toJson() => {
        'type': type.code,
        'displayName': displayName,
        'legalName': legalName,
        'phone': phone,
        'email': email,
        'country': country,
        'city': city,
        'province': province,
        'bankName': bankName,
        'bankBranch': bankBranch,
        'accountName': accountName,
        'accountNumber': accountNumber,
        'bankAddress': bankAddress,
        'swiftCode': swiftCode,
        'currency': currency.code,
        'purpose': purpose?.code,
        'notes': notes,
      };
}

/// Corps de `POST /api/v1/suppliers/{id}/pay-again` — le montant est TOUJOURS
/// resaisi par l'utilisateur, jamais copie d'un ordre passe (mission section 22).
class PayAgainRequest {
  final String amountXof;
  final Purpose? purpose;
  final String? purposeDetails;

  const PayAgainRequest({required this.amountXof, this.purpose, this.purposeDetails});

  Map<String, dynamic> toJson() => {
        'amountXof': amountXof,
        'purpose': purpose?.code,
        'purposeDetails': purposeDetails,
      };
}
