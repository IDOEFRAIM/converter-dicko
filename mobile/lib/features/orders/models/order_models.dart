import '../../../shared/utils/json_decimal.dart';

/// Statuts d'un ordre — miroir exact de `OrderStatus` backend. Aucune machine
/// d'etat independante ne doit etre recreee cote mobile (mission section 25).
enum OrderStatus {
  awaitingPayment,
  paymentSubmitted,
  paymentVerified,
  processing,
  completed,
  cancelled,
  rejected,
  expired,
  unknown;

  static OrderStatus fromCode(String? code) {
    switch (code) {
      case 'AWAITING_PAYMENT':
        return OrderStatus.awaitingPayment;
      case 'PAYMENT_SUBMITTED':
        return OrderStatus.paymentSubmitted;
      case 'PAYMENT_VERIFIED':
        return OrderStatus.paymentVerified;
      case 'PROCESSING':
        return OrderStatus.processing;
      case 'COMPLETED':
        return OrderStatus.completed;
      case 'CANCELLED':
        return OrderStatus.cancelled;
      case 'REJECTED':
        return OrderStatus.rejected;
      case 'EXPIRED':
        return OrderStatus.expired;
      default:
        return OrderStatus.unknown;
    }
  }

  /// Code backend d'origine — a utiliser pour [StatusBadge] (jamais une
  /// traduction inventee cote mobile pour l'affichage du badge).
  String get code => switch (this) {
        OrderStatus.awaitingPayment => 'AWAITING_PAYMENT',
        OrderStatus.paymentSubmitted => 'PAYMENT_SUBMITTED',
        OrderStatus.paymentVerified => 'PAYMENT_VERIFIED',
        OrderStatus.processing => 'PROCESSING',
        OrderStatus.completed => 'COMPLETED',
        OrderStatus.cancelled => 'CANCELLED',
        OrderStatus.rejected => 'REJECTED',
        OrderStatus.expired => 'EXPIRED',
        OrderStatus.unknown => 'UNKNOWN',
      };
}

/// Ligne de l'historique enrichi (`GET /api/v1/orders/history`) — miroir de
/// `OrderHistoryResponse` / `OrderHistoryEntry` (Angular).
class OrderHistoryEntry {
  final String id;
  final String reference;
  final OrderStatus status;
  final String amountXof;
  final String amountCny;
  final String feeXof;
  final String customerRate;
  final String? purpose;
  final String? supplierId;
  final DateTime createdAt;
  final DateTime? completedAt;

  const OrderHistoryEntry({
    required this.id,
    required this.reference,
    required this.status,
    required this.amountXof,
    required this.amountCny,
    required this.feeXof,
    required this.customerRate,
    required this.purpose,
    required this.supplierId,
    required this.createdAt,
    required this.completedAt,
  });

  factory OrderHistoryEntry.fromJson(Map<String, dynamic> json) {
    return OrderHistoryEntry(
      id: json['id'] as String,
      reference: json['reference'] as String,
      status: OrderStatus.fromCode(json['status'] as String?),
      amountXof: decimalStringFromJson(json['amountXof']),
      amountCny: decimalStringFromJson(json['amountCny']),
      feeXof: decimalStringFromJson(json['feeXof']),
      customerRate: decimalStringFromJson(json['customerRate']),
      purpose: json['purpose'] as String?,
      supplierId: json['supplierId'] as String?,
      createdAt: DateTime.parse(json['createdAt'] as String),
      completedAt: json['completedAt'] == null ? null : DateTime.parse(json['completedAt'] as String),
    );
  }
}
