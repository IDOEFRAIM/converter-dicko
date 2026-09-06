import '../../../shared/utils/json_decimal.dart';

/// Miroir de `com.converter.preferredrate.domain.PreferredRateStatus`.
enum PreferredRateStatus {
  active,
  executed,
  expired,
  cancelled,
  unknown;

  String get code => switch (this) {
        PreferredRateStatus.active => 'ACTIVE',
        PreferredRateStatus.executed => 'EXECUTED',
        PreferredRateStatus.expired => 'EXPIRED',
        PreferredRateStatus.cancelled => 'CANCELLED',
        PreferredRateStatus.unknown => 'UNKNOWN',
      };

  static PreferredRateStatus fromCode(String? code) {
    switch (code) {
      case 'ACTIVE':
        return PreferredRateStatus.active;
      case 'EXECUTED':
        return PreferredRateStatus.executed;
      case 'EXPIRED':
        return PreferredRateStatus.expired;
      case 'CANCELLED':
        return PreferredRateStatus.cancelled;
      default:
        return PreferredRateStatus.unknown;
    }
  }
}

/// Miroir de `PreferredRatePhase` (backend) : indique quelle des deux vues
/// afficher — jamais les deux compteurs (J+3 et 2h) en meme temps (mission,
/// meme principe que documente dans la Javadoc backend).
enum PreferredRatePhase {
  waiting,
  exchangeInProgress,
  exchangeCompleted,
  expired,
  cancelled,
  unknown;

  static PreferredRatePhase fromCode(String? code) {
    switch (code) {
      case 'WAITING':
        return PreferredRatePhase.waiting;
      case 'EXCHANGE_IN_PROGRESS':
        return PreferredRatePhase.exchangeInProgress;
      case 'EXCHANGE_COMPLETED':
        return PreferredRatePhase.exchangeCompleted;
      case 'EXPIRED':
        return PreferredRatePhase.expired;
      case 'CANCELLED':
        return PreferredRatePhase.cancelled;
      default:
        return PreferredRatePhase.unknown;
    }
  }
}

/// Miroir de `com.converter.preferredrate.domain.ExchangeStatus`.
enum ExchangeStatus {
  started,
  completed,
  cancelled,
  unknown;

  static ExchangeStatus fromCode(String? code) {
    switch (code) {
      case 'STARTED':
        return ExchangeStatus.started;
      case 'COMPLETED':
        return ExchangeStatus.completed;
      case 'CANCELLED':
        return ExchangeStatus.cancelled;
      default:
        return ExchangeStatus.unknown;
    }
  }
}

/// Miroir de `ExchangeSummaryResponse`. `stage` reste la chaine brute
/// backend (`STARTED`/`PROGRESS_45`/`PROGRESS_90`/`COMPLETED`) — jamais
/// recalculee cote mobile, uniquement traduite pour l'affichage.
class ExchangeSummary {
  final String id;
  final String amountXof;
  final String achievedRate;
  final String amountCny;
  final ExchangeStatus status;
  final String stage;
  final DateTime startedAt;
  final DateTime deadlineAt;
  final DateTime? nextUpdateAt;
  final DateTime? completedAt;

  const ExchangeSummary({
    required this.id,
    required this.amountXof,
    required this.achievedRate,
    required this.amountCny,
    required this.status,
    required this.stage,
    required this.startedAt,
    required this.deadlineAt,
    required this.nextUpdateAt,
    required this.completedAt,
  });

  factory ExchangeSummary.fromJson(Map<String, dynamic> json) {
    return ExchangeSummary(
      id: json['id'] as String,
      amountXof: decimalStringFromJson(json['amountXof']),
      achievedRate: decimalStringFromJson(json['achievedRate']),
      amountCny: decimalStringFromJson(json['amountCny']),
      status: ExchangeStatus.fromCode(json['status'] as String?),
      stage: json['stage'] as String? ?? 'STARTED',
      startedAt: DateTime.parse(json['startedAt'] as String),
      deadlineAt: DateTime.parse(json['deadlineAt'] as String),
      nextUpdateAt: json['nextUpdateAt'] == null ? null : DateTime.parse(json['nextUpdateAt'] as String),
      completedAt: json['completedAt'] == null ? null : DateTime.parse(json['completedAt'] as String),
    );
  }
}

/// Miroir de `PreferredRateRequestResponse`. `currentRate`/`gap` sont
/// `null` si aucune cotation courante n'est disponible ou si la demande
/// n'est plus `ACTIVE` — jamais recalcules cote mobile (mission, meme
/// principe que `RateAlertsController.gapFor`, mais fourni ici directement
/// par le backend).
class PreferredRate {
  final String id;
  final String amountXof;
  final String targetRate;
  final String? currentRate;
  final String? gap;
  final PreferredRatePhase phase;
  final PreferredRateStatus status;
  final String? achievedRate;
  final DateTime createdAt;
  final DateTime expiresAt;
  final DateTime? executedAt;
  final DateTime? expiredAt;
  final DateTime? cancelledAt;
  final ExchangeSummary? exchange;

  const PreferredRate({
    required this.id,
    required this.amountXof,
    required this.targetRate,
    required this.currentRate,
    required this.gap,
    required this.phase,
    required this.status,
    required this.achievedRate,
    required this.createdAt,
    required this.expiresAt,
    required this.executedAt,
    required this.expiredAt,
    required this.cancelledAt,
    required this.exchange,
  });

  factory PreferredRate.fromJson(Map<String, dynamic> json) {
    return PreferredRate(
      id: json['id'] as String,
      amountXof: decimalStringFromJson(json['amountXof']),
      targetRate: decimalStringFromJson(json['targetRate']),
      currentRate: json['currentRate'] == null ? null : decimalStringFromJson(json['currentRate']),
      gap: json['gap'] == null ? null : decimalStringFromJson(json['gap']),
      phase: PreferredRatePhase.fromCode(json['phase'] as String?),
      status: PreferredRateStatus.fromCode(json['status'] as String?),
      achievedRate: json['achievedRate'] == null ? null : decimalStringFromJson(json['achievedRate']),
      createdAt: DateTime.parse(json['createdAt'] as String),
      expiresAt: DateTime.parse(json['expiresAt'] as String),
      executedAt: json['executedAt'] == null ? null : DateTime.parse(json['executedAt'] as String),
      expiredAt: json['expiredAt'] == null ? null : DateTime.parse(json['expiredAt'] as String),
      cancelledAt: json['cancelledAt'] == null ? null : DateTime.parse(json['cancelledAt'] as String),
      exchange: json['exchange'] == null ? null : ExchangeSummary.fromJson(json['exchange'] as Map<String, dynamic>),
    );
  }
}

/// Corps de creation — miroir de `CreatePreferredRateRequest`. `direction`
/// n'est jamais expose au choix de l'utilisateur : `XOF_TO_CNY` est la seule
/// valeur supportee par le backend (voir `PreferredRateService.create`, qui
/// rejette toute autre valeur).
class CreatePreferredRateRequest {
  final String amountXof;
  final String targetRate;

  const CreatePreferredRateRequest({required this.amountXof, required this.targetRate});

  Map<String, dynamic> toJson() => {
        'direction': 'XOF_TO_CNY',
        'amountXof': amountXof,
        'targetRate': targetRate,
      };
}
