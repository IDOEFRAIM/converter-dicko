import '../../../shared/utils/json_decimal.dart';

enum PaymentMethod {
  mobileMoney,
  unknown;

  String get code => this == PaymentMethod.mobileMoney ? 'MOBILE_MONEY' : 'UNKNOWN';

  String get label => this == PaymentMethod.mobileMoney ? 'Mobile Money' : code;

  static PaymentMethod fromCode(String? code) => code == 'MOBILE_MONEY' ? PaymentMethod.mobileMoney : PaymentMethod.unknown;
}

enum PaymentStatus {
  submitted,
  confirmed,
  rejected,
  unknown;

  String get code => switch (this) {
        PaymentStatus.submitted => 'SUBMITTED',
        PaymentStatus.confirmed => 'CONFIRMED',
        PaymentStatus.rejected => 'REJECTED',
        PaymentStatus.unknown => 'UNKNOWN',
      };

  static PaymentStatus fromCode(String? code) {
    switch (code) {
      case 'SUBMITTED':
        return PaymentStatus.submitted;
      case 'CONFIRMED':
        return PaymentStatus.confirmed;
      case 'REJECTED':
        return PaymentStatus.rejected;
      default:
        return PaymentStatus.unknown;
    }
  }
}

class SubmitPaymentRequest {
  final PaymentMethod method;
  final String receivedAmountXof;
  final String transactionReference;
  final String payerPhone;
  final String payerName;

  const SubmitPaymentRequest({
    required this.method,
    required this.receivedAmountXof,
    required this.transactionReference,
    required this.payerPhone,
    required this.payerName,
  });

  Map<String, dynamic> toJson() => {
        'method': method.code,
        'receivedAmountXof': receivedAmountXof,
        'transactionReference': transactionReference,
        'payerPhone': payerPhone,
        'payerName': payerName,
      };
}

class PaymentProof {
  final String id;
  final String fileName;
  final String contentType;
  final int sizeBytes;
  final DateTime uploadedAt;

  const PaymentProof({
    required this.id,
    required this.fileName,
    required this.contentType,
    required this.sizeBytes,
    required this.uploadedAt,
  });

  factory PaymentProof.fromJson(Map<String, dynamic> json) {
    return PaymentProof(
      id: json['id'] as String,
      fileName: json['fileName'] as String? ?? '',
      contentType: json['contentType'] as String? ?? 'application/octet-stream',
      sizeBytes: json['sizeBytes'] as int? ?? 0,
      uploadedAt: DateTime.parse(json['uploadedAt'] as String),
    );
  }
}

class Payment {
  final String id;
  final String orderId;
  final PaymentMethod method;
  final PaymentStatus status;
  final String expectedAmountXof;
  final String receivedAmountXof;
  final String transactionReference;
  final String? payerPhone;
  final String? payerName;
  final String? rejectionReason;
  final List<PaymentProof> proofs;
  final DateTime submittedAt;
  final DateTime? confirmedAt;
  final DateTime? rejectedAt;

  const Payment({
    required this.id,
    required this.orderId,
    required this.method,
    required this.status,
    required this.expectedAmountXof,
    required this.receivedAmountXof,
    required this.transactionReference,
    required this.payerPhone,
    required this.payerName,
    required this.rejectionReason,
    required this.proofs,
    required this.submittedAt,
    required this.confirmedAt,
    required this.rejectedAt,
  });

  factory Payment.fromJson(Map<String, dynamic> json) {
    return Payment(
      id: json['id'] as String,
      orderId: json['orderId'] as String,
      method: PaymentMethod.fromCode(json['method'] as String?),
      status: PaymentStatus.fromCode(json['status'] as String?),
      expectedAmountXof: decimalStringFromJson(json['expectedAmountXof']),
      receivedAmountXof: decimalStringFromJson(json['receivedAmountXof']),
      transactionReference: json['transactionReference'] as String? ?? '',
      payerPhone: json['payerPhone'] as String?,
      payerName: json['payerName'] as String?,
      rejectionReason: json['rejectionReason'] as String?,
      proofs: (json['proofs'] as List<dynamic>? ?? const [])
          .map((e) => PaymentProof.fromJson(e as Map<String, dynamic>))
          .toList(growable: false),
      submittedAt: DateTime.parse(json['submittedAt'] as String),
      confirmedAt: json['confirmedAt'] == null ? null : DateTime.parse(json['confirmedAt'] as String),
      rejectedAt: json['rejectedAt'] == null ? null : DateTime.parse(json['rejectedAt'] as String),
    );
  }
}
