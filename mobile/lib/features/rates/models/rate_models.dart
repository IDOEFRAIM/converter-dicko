import '../../../shared/utils/json_decimal.dart';

/// Ligne de l'historique public du taux client — miroir de
/// `PublicRateHistoryEntry` (backend `com.converter.rate.publicrate.dto`,
/// Angular `rate-history.model.ts`). N'expose JAMAIS le taux de revient ni la
/// marge : uniquement `customerRate`, le taux reellement propose au client.
class PublicRateHistoryEntry {
  final String currencyPair;
  final String customerRate;
  final DateTime recordedAt;

  const PublicRateHistoryEntry({
    required this.currencyPair,
    required this.customerRate,
    required this.recordedAt,
  });

  factory PublicRateHistoryEntry.fromJson(Map<String, dynamic> json) {
    return PublicRateHistoryEntry(
      currencyPair: json['currencyPair'] as String? ?? 'XOF/CNY',
      customerRate: decimalStringFromJson(json['customerRate']),
      recordedAt: DateTime.parse(json['recordedAt'] as String),
    );
  }
}

// ---------------------------------------------------------------------
// Alertes de taux — "previens-moi quand le taux atteint mon objectif".
// Aucune transaction : ni devis, ni ordre (mission section 30).
// ---------------------------------------------------------------------

enum RateAlertStatus {
  active,
  triggered,
  cancelled,
  expired,
  unknown;

  String get code => switch (this) {
        RateAlertStatus.active => 'ACTIVE',
        RateAlertStatus.triggered => 'TRIGGERED',
        RateAlertStatus.cancelled => 'CANCELLED',
        RateAlertStatus.expired => 'EXPIRED',
        RateAlertStatus.unknown => 'UNKNOWN',
      };

  static RateAlertStatus fromCode(String? code) {
    switch (code) {
      case 'ACTIVE':
        return RateAlertStatus.active;
      case 'TRIGGERED':
        return RateAlertStatus.triggered;
      case 'CANCELLED':
        return RateAlertStatus.cancelled;
      case 'EXPIRED':
        return RateAlertStatus.expired;
      default:
        return RateAlertStatus.unknown;
    }
  }
}

/// Aligne sur `com.converter.rate.alert.domain.RateComparison`. Seule
/// `lessThanOrEqual` est proposee a la creation (mission section 30) : c'est
/// la seule semantique dont le micro-copy est valide ("je veux etre averti
/// quand le taux descend a ma cible ou en dessous").
enum RateComparison {
  lessThanOrEqual,
  greaterThanOrEqual;

  String get code => this == RateComparison.greaterThanOrEqual ? 'GREATER_THAN_OR_EQUAL' : 'LESS_THAN_OR_EQUAL';

  static RateComparison fromCode(String? code) =>
      code == 'GREATER_THAN_OR_EQUAL' ? RateComparison.greaterThanOrEqual : RateComparison.lessThanOrEqual;
}

class RateAlert {
  final String id;
  final String currencyPair;
  final String targetRate;
  final RateComparison comparison;
  final RateAlertStatus status;
  final DateTime createdAt;
  final DateTime? expiresAt;
  final DateTime? triggeredAt;
  final DateTime? cancelledAt;
  final DateTime? expiredAt;

  const RateAlert({
    required this.id,
    required this.currencyPair,
    required this.targetRate,
    required this.comparison,
    required this.status,
    required this.createdAt,
    required this.expiresAt,
    required this.triggeredAt,
    required this.cancelledAt,
    required this.expiredAt,
  });

  factory RateAlert.fromJson(Map<String, dynamic> json) {
    return RateAlert(
      id: json['id'] as String,
      currencyPair: json['currencyPair'] as String? ?? 'XOF/CNY',
      targetRate: decimalStringFromJson(json['targetRate']),
      comparison: RateComparison.fromCode(json['comparison'] as String?),
      status: RateAlertStatus.fromCode(json['status'] as String?),
      createdAt: DateTime.parse(json['createdAt'] as String),
      expiresAt: json['expiresAt'] == null ? null : DateTime.parse(json['expiresAt'] as String),
      triggeredAt: json['triggeredAt'] == null ? null : DateTime.parse(json['triggeredAt'] as String),
      cancelledAt: json['cancelledAt'] == null ? null : DateTime.parse(json['cancelledAt'] as String),
      expiredAt: json['expiredAt'] == null ? null : DateTime.parse(json['expiredAt'] as String),
    );
  }
}

/// `currencyPair`/`direction`/`comparison` sont deduits par le backend de la
/// configuration actuelle du produit — seul `targetRate` est une decision
/// utilisateur (mission section 30).
class CreateRateAlertRequest {
  final String targetRate;
  final DateTime? expiresAt;

  const CreateRateAlertRequest({required this.targetRate, this.expiresAt});

  Map<String, dynamic> toJson() => {
        'targetRate': targetRate,
        'expiresAt': expiresAt?.toUtc().toIso8601String(),
      };
}
