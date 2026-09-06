import '../../../shared/utils/json_decimal.dart';

/// Sens d'un devis — miroir de `QuoteDirection` backend. `sendXof` : le
/// client fixe le montant XOF envoye ; `receiveCny` : le client fixe le
/// montant CNY que le beneficiaire doit recevoir.
enum QuoteDirection {
  sendXof,
  receiveCny;

  String get code => this == QuoteDirection.receiveCny ? 'RECEIVE_CNY' : 'SEND_XOF';

  static QuoteDirection fromCode(String? code) =>
      code == 'RECEIVE_CNY' ? QuoteDirection.receiveCny : QuoteDirection.sendXof;
}

enum QuoteStatus {
  active,
  accepted,
  expired,
  cancelled,
  unknown;

  static QuoteStatus fromCode(String? code) {
    switch (code) {
      case 'ACTIVE':
        return QuoteStatus.active;
      case 'ACCEPTED':
        return QuoteStatus.accepted;
      case 'EXPIRED':
        return QuoteStatus.expired;
      case 'CANCELLED':
        return QuoteStatus.cancelled;
      default:
        return QuoteStatus.unknown;
    }
  }
}

/// Corps de `POST /api/v1/quotes` — exactement un des deux montants doit
/// etre fourni, jamais les deux (verifie cote serveur).
class CreateQuoteRequest {
  final QuoteDirection direction;
  final String? amountXof;
  final String? amountCny;

  const CreateQuoteRequest.sendXof(String amount)
      : direction = QuoteDirection.sendXof,
        amountXof = amount,
        amountCny = null;

  const CreateQuoteRequest.receiveCny(String amount)
      : direction = QuoteDirection.receiveCny,
        amountXof = null,
        amountCny = amount;

  Map<String, dynamic> toJson() => {
        'direction': direction.code,
        'amountXof': amountXof,
        'amountCny': amountCny,
      };
}

/// Miroir de `QuoteResponse` backend. N'expose JAMAIS `breakEvenRate` ni
/// `marginPercentage` (interdit par un test backend dedie) — ne tente pas de
/// les reconstruire cote mobile non plus.
class Quote {
  final String id;
  final QuoteDirection direction;
  final String amountXof;
  final String amountCny;
  final String customerRate;
  final String feeXof;
  final String netAmountXof;
  final QuoteStatus status;
  final DateTime createdAt;
  final DateTime expiresAt;

  const Quote({
    required this.id,
    required this.direction,
    required this.amountXof,
    required this.amountCny,
    required this.customerRate,
    required this.feeXof,
    required this.netAmountXof,
    required this.status,
    required this.createdAt,
    required this.expiresAt,
  });

  bool get isExpired => status == QuoteStatus.expired || DateTime.now().toUtc().isAfter(expiresAt);

  Duration get timeUntilExpiry => expiresAt.difference(DateTime.now().toUtc());

  factory Quote.fromJson(Map<String, dynamic> json) {
    return Quote(
      id: json['id'] as String,
      direction: QuoteDirection.fromCode(json['direction'] as String?),
      amountXof: decimalStringFromJson(json['amountXof']),
      amountCny: decimalStringFromJson(json['amountCny']),
      customerRate: decimalStringFromJson(json['customerRate']),
      feeXof: decimalStringFromJson(json['feeXof']),
      netAmountXof: decimalStringFromJson(json['netAmountXof']),
      status: QuoteStatus.fromCode(json['status'] as String?),
      createdAt: DateTime.parse(json['createdAt'] as String),
      expiresAt: DateTime.parse(json['expiresAt'] as String),
    );
  }
}
