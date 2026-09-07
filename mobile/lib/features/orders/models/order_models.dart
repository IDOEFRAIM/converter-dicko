import '../../../shared/models/purpose.dart';
import '../../../shared/utils/json_decimal.dart';
import '../../suppliers/models/supplier_models.dart';

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
  final Purpose? purpose;
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
      purpose: Purpose.fromCode(json['purpose'] as String?),
      supplierId: json['supplierId'] as String?,
      createdAt: DateTime.parse(json['createdAt'] as String),
      completedAt: json['completedAt'] == null ? null : DateTime.parse(json['completedAt'] as String),
    );
  }
}

/// Beneficiaire tel qu'enregistre sur l'ordre — snapshot immuable pris a la
/// creation, jamais relie a un `Supplier` vivant par la suite (mission
/// section 21 : le fournisseur peut etre modifie/desactive sans jamais
/// affecter les ordres deja crees).
class Beneficiary {
  final BeneficiaryType type;
  final String fullName;
  final String identifier;
  final String? bankName;
  final String? bankBranch;

  const Beneficiary({
    required this.type,
    required this.fullName,
    required this.identifier,
    required this.bankName,
    required this.bankBranch,
  });

  factory Beneficiary.fromJson(Map<String, dynamic> json) {
    return Beneficiary(
      type: BeneficiaryType.fromCode(json['type'] as String?),
      fullName: json['fullName'] as String? ?? '',
      identifier: json['identifier'] as String? ?? '',
      bankName: json['bankName'] as String?,
      bankBranch: json['bankBranch'] as String?,
    );
  }
}

/// Corps de `BeneficiaryRequest` — saisie manuelle d'un beneficiaire pour un
/// nouvel ordre (alternative a `supplierId`).
class BeneficiaryRequest {
  final BeneficiaryType type;
  final String fullName;
  final String identifier;
  final String? bankName;
  final String? bankBranch;

  const BeneficiaryRequest({
    required this.type,
    required this.fullName,
    required this.identifier,
    this.bankName,
    this.bankBranch,
  });

  Map<String, dynamic> toJson() => {
        'type': type.code,
        'fullName': fullName,
        'identifier': identifier,
        'bankName': bankName,
        'bankBranch': bankBranch,
      };
}

class OrderStatusHistoryEntry {
  final OrderStatus? fromStatus;
  final OrderStatus toStatus;
  final String? changedBy;
  final String? reason;
  final DateTime createdAt;

  const OrderStatusHistoryEntry({
    required this.fromStatus,
    required this.toStatus,
    required this.changedBy,
    required this.reason,
    required this.createdAt,
  });

  factory OrderStatusHistoryEntry.fromJson(Map<String, dynamic> json) {
    return OrderStatusHistoryEntry(
      fromStatus: json['fromStatus'] == null ? null : OrderStatus.fromCode(json['fromStatus'] as String?),
      toStatus: OrderStatus.fromCode(json['toStatus'] as String?),
      changedBy: json['changedBy'] as String?,
      reason: json['reason'] as String?,
      createdAt: DateTime.parse(json['createdAt'] as String),
    );
  }
}

/// Detail complet d'un ordre — miroir de `OrderDetailResponse`.
class OrderDetail {
  final String id;
  final String reference;
  final String quoteId;
  final OrderStatus status;
  final String amountXof;
  final String amountCny;
  final String customerRate;
  final String feeXof;
  final String netAmountXof;
  final String? note;
  final String? cancellationReason;
  final String? rejectionReason;
  final Beneficiary beneficiary;
  final List<OrderStatusHistoryEntry> statusHistory;
  final DateTime createdAt;
  final DateTime updatedAt;
  final DateTime paymentDeadlineAt;
  final DateTime? completedAt;
  final DateTime? cancelledAt;
  final String? supplierId;
  final Purpose? purpose;
  final String? purposeDetails;

  const OrderDetail({
    required this.id,
    required this.reference,
    required this.quoteId,
    required this.status,
    required this.amountXof,
    required this.amountCny,
    required this.customerRate,
    required this.feeXof,
    required this.netAmountXof,
    required this.note,
    required this.cancellationReason,
    required this.rejectionReason,
    required this.beneficiary,
    required this.statusHistory,
    required this.createdAt,
    required this.updatedAt,
    required this.paymentDeadlineAt,
    required this.completedAt,
    required this.cancelledAt,
    required this.supplierId,
    required this.purpose,
    required this.purposeDetails,
  });

  factory OrderDetail.fromJson(Map<String, dynamic> json) {
    return OrderDetail(
      id: json['id'] as String,
      reference: json['reference'] as String,
      quoteId: json['quoteId'] as String,
      status: OrderStatus.fromCode(json['status'] as String?),
      amountXof: decimalStringFromJson(json['amountXof']),
      amountCny: decimalStringFromJson(json['amountCny']),
      customerRate: decimalStringFromJson(json['customerRate']),
      feeXof: decimalStringFromJson(json['feeXof']),
      netAmountXof: decimalStringFromJson(json['netAmountXof']),
      note: json['note'] as String?,
      cancellationReason: json['cancellationReason'] as String?,
      rejectionReason: json['rejectionReason'] as String?,
      beneficiary: Beneficiary.fromJson(json['beneficiary'] as Map<String, dynamic>),
      statusHistory: (json['statusHistory'] as List<dynamic>? ?? const [])
          .map((e) => OrderStatusHistoryEntry.fromJson(e as Map<String, dynamic>))
          .toList(growable: false),
      createdAt: DateTime.parse(json['createdAt'] as String),
      updatedAt: DateTime.parse(json['updatedAt'] as String),
      paymentDeadlineAt: DateTime.parse(json['paymentDeadlineAt'] as String),
      completedAt: json['completedAt'] == null ? null : DateTime.parse(json['completedAt'] as String),
      cancelledAt: json['cancelledAt'] == null ? null : DateTime.parse(json['cancelledAt'] as String),
      supplierId: json['supplierId'] as String?,
      purpose: Purpose.fromCode(json['purpose'] as String?),
      purposeDetails: json['purposeDetails'] as String?,
    );
  }
}

/// Corps de `POST /api/v1/orders` — exactement un de `beneficiary`/`supplierId`.
class CreateOrderRequest {
  final String quoteId;
  final BeneficiaryRequest? beneficiary;
  final String? supplierId;
  final String? note;
  final Purpose? purpose;
  final String? purposeDetails;

  /// Ruee collective a laquelle cet ordre contribue, optionnelle (mission
  /// "differenciation marketing", Lot 3) — le client doit deja l'avoir
  /// rejointe (voir `PoolApi.join`).
  final String? poolId;

  const CreateOrderRequest({
    required this.quoteId,
    this.beneficiary,
    this.supplierId,
    this.note,
    this.purpose,
    this.purposeDetails,
    this.poolId,
  }) : assert(
          (beneficiary == null) != (supplierId == null),
          'Exactement un de beneficiary/supplierId doit etre fourni.',
        );

  Map<String, dynamic> toJson() => {
        'quoteId': quoteId,
        'beneficiary': beneficiary?.toJson(),
        'supplierId': supplierId,
        'note': note,
        'purpose': purpose?.code,
        'purposeDetails': purposeDetails,
        'poolId': poolId,
      };
}

/// Miroir de `OrderFeasibilityResponse` — aucun solde de tresorerie exposeé,
/// uniquement un hint d'affichage (mission : ne jamais bloquer uniquement sur
/// ce signal, la verite reste la reservation faite a la creation de l'ordre).
class OrderFeasibility {
  final String quoteId;
  final String amountCny;
  final bool settlementReservationEnabled;
  final bool sufficientLiquidity;

  const OrderFeasibility({
    required this.quoteId,
    required this.amountCny,
    required this.settlementReservationEnabled,
    required this.sufficientLiquidity,
  });

  factory OrderFeasibility.fromJson(Map<String, dynamic> json) {
    return OrderFeasibility(
      quoteId: json['quoteId'] as String,
      amountCny: decimalStringFromJson(json['amountCny']),
      settlementReservationEnabled: json['settlementReservationEnabled'] as bool? ?? false,
      sufficientLiquidity: json['sufficientLiquidity'] as bool? ?? true,
    );
  }
}
