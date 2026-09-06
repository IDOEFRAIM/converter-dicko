import 'order_models.dart';

/// Catalogue ferme, aligne sur `com.converter.order.dto.TrackingEventCode` —
/// miroir de `TrackingEventCode` (Angular `tracking.model.ts`).
enum TrackingEventCode {
  orderCreated,
  paymentSubmitted,
  paymentVerified,
  paymentRejected,
  processing,
  settlementExecuted,
  completed,
  cancelled,
  rejected,
  expired,
  refundPending,
  refundProcessed,
  unknown;

  static TrackingEventCode fromCode(String? code) {
    switch (code) {
      case 'ORDER_CREATED':
        return TrackingEventCode.orderCreated;
      case 'PAYMENT_SUBMITTED':
        return TrackingEventCode.paymentSubmitted;
      case 'PAYMENT_VERIFIED':
        return TrackingEventCode.paymentVerified;
      case 'PAYMENT_REJECTED':
        return TrackingEventCode.paymentRejected;
      case 'PROCESSING':
        return TrackingEventCode.processing;
      case 'SETTLEMENT_EXECUTED':
        return TrackingEventCode.settlementExecuted;
      case 'COMPLETED':
        return TrackingEventCode.completed;
      case 'CANCELLED':
        return TrackingEventCode.cancelled;
      case 'REJECTED':
        return TrackingEventCode.rejected;
      case 'EXPIRED':
        return TrackingEventCode.expired;
      case 'REFUND_PENDING':
        return TrackingEventCode.refundPending;
      case 'REFUND_PROCESSED':
        return TrackingEventCode.refundProcessed;
      default:
        return TrackingEventCode.unknown;
    }
  }

  /// Mapping UI central, base UNIQUEMENT sur le code (jamais le `label` brut
  /// renvoye par le backend) — miroir exact de `TRACKING_EVENT_LABELS` cote
  /// Angular (mission section 25 : ne jamais recreer une machine d'etat
  /// independante, seulement traduire les codes reels).
  String get label => switch (this) {
        TrackingEventCode.orderCreated => 'Transfert cree',
        TrackingEventCode.paymentSubmitted => 'Paiement soumis',
        TrackingEventCode.paymentVerified => 'Paiement verifie',
        TrackingEventCode.paymentRejected => 'Paiement rejete',
        TrackingEventCode.processing => 'Traitement en cours',
        TrackingEventCode.settlementExecuted => 'Reglement effectue',
        TrackingEventCode.completed => 'Transfert termine',
        TrackingEventCode.cancelled => 'Transfert annule',
        TrackingEventCode.rejected => 'Transfert rejete',
        TrackingEventCode.expired => 'Transfert expire',
        TrackingEventCode.refundPending => 'Remboursement en cours',
        TrackingEventCode.refundProcessed => 'Remboursement effectue',
        TrackingEventCode.unknown => 'Evenement',
      };

  /// Represente un echec/une fin non nominale — rendu visuel distinct, jamais
  /// confondu avec un remboursement (mission section 25 : un COMPLETED ne
  /// doit jamais etre colore comme un echec, un remboursement reste separe).
  bool get isNegative => this == TrackingEventCode.paymentRejected ||
      this == TrackingEventCode.cancelled ||
      this == TrackingEventCode.rejected ||
      this == TrackingEventCode.expired;

  bool get isRefund => this == TrackingEventCode.refundPending || this == TrackingEventCode.refundProcessed;
}

class TrackingEvent {
  final TrackingEventCode code;
  final OrderStatus? status;
  final DateTime occurredAt;

  const TrackingEvent({required this.code, required this.status, required this.occurredAt});

  factory TrackingEvent.fromJson(Map<String, dynamic> json) {
    return TrackingEvent(
      code: TrackingEventCode.fromCode(json['code'] as String?),
      status: json['status'] == null ? null : OrderStatus.fromCode(json['status'] as String?),
      occurredAt: DateTime.parse(json['occurredAt'] as String),
    );
  }
}

/// Miroir de `OrderTracking` — timeline agregee en lecture seule, jamais une
/// seconde machine d'etat cote client.
class OrderTracking {
  final String orderId;
  final OrderStatus currentStatus;
  final DateTime createdAt;
  final DateTime? completedAt;
  final List<TrackingEvent> timeline;

  const OrderTracking({
    required this.orderId,
    required this.currentStatus,
    required this.createdAt,
    required this.completedAt,
    required this.timeline,
  });

  static const _terminalStatuses = {
    OrderStatus.completed,
    OrderStatus.cancelled,
    OrderStatus.rejected,
    OrderStatus.expired,
  };

  bool get isTerminal => _terminalStatuses.contains(currentStatus);

  factory OrderTracking.fromJson(Map<String, dynamic> json) {
    return OrderTracking(
      orderId: json['orderId'] as String,
      currentStatus: OrderStatus.fromCode(json['currentStatus'] as String?),
      createdAt: DateTime.parse(json['createdAt'] as String),
      completedAt: json['completedAt'] == null ? null : DateTime.parse(json['completedAt'] as String),
      timeline: (json['timeline'] as List<dynamic>? ?? const [])
          .map((e) => TrackingEvent.fromJson(e as Map<String, dynamic>))
          .toList(growable: false),
    );
  }
}
