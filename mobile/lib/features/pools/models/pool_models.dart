import '../../../shared/utils/json_decimal.dart';

/// "Ruee collective" (mission "differenciation marketing", Lot 3) — miroir de
/// `com.converter.pool.domain.PoolStatus`.
enum PoolStatus {
  active,
  succeeded,
  expired,
  cancelled,
  unknown;

  String get code => switch (this) {
        PoolStatus.active => 'ACTIVE',
        PoolStatus.succeeded => 'SUCCEEDED',
        PoolStatus.expired => 'EXPIRED',
        PoolStatus.cancelled => 'CANCELLED',
        PoolStatus.unknown => 'UNKNOWN',
      };

  static PoolStatus fromCode(String? code) {
    switch (code) {
      case 'ACTIVE':
        return PoolStatus.active;
      case 'SUCCEEDED':
        return PoolStatus.succeeded;
      case 'EXPIRED':
        return PoolStatus.expired;
      case 'CANCELLED':
        return PoolStatus.cancelled;
      default:
        return PoolStatus.unknown;
    }
  }
}

/// Miroir de `PoolResponse` backend. `viewerIsParticipant`/`viewerIsCreator`
/// sont relatifs au compte connecte — n'importe qui peut consulter une Ruee
/// (previsualisation avant de la rejoindre), pas seulement ses participants.
class Pool {
  final String id;
  final String code;
  final String creatorId;
  final String currencyPair;
  final String targetAmountXof;
  final String currentAmountXof;
  final PoolStatus status;
  final int participantCount;
  final String rewardMarginReductionPercentage;
  final DateTime createdAt;
  final DateTime expiresAt;
  final DateTime? succeededAt;
  final DateTime? expiredAt;
  final DateTime? cancelledAt;
  final bool viewerIsParticipant;
  final bool viewerIsCreator;

  const Pool({
    required this.id,
    required this.code,
    required this.creatorId,
    required this.currencyPair,
    required this.targetAmountXof,
    required this.currentAmountXof,
    required this.status,
    required this.participantCount,
    required this.rewardMarginReductionPercentage,
    required this.createdAt,
    required this.expiresAt,
    required this.succeededAt,
    required this.expiredAt,
    required this.cancelledAt,
    required this.viewerIsParticipant,
    required this.viewerIsCreator,
  });

  /// Progression 0.0 -> 1.0 (jamais au-dela, meme si le volume cumule depasse
  /// legerement l'objectif au moment exact du declenchement).
  double get progress {
    final target = double.tryParse(targetAmountXof) ?? 0;
    final current = double.tryParse(currentAmountXof) ?? 0;
    if (target <= 0) return 0;
    return (current / target).clamp(0.0, 1.0);
  }

  bool get isActive => status == PoolStatus.active;

  factory Pool.fromJson(Map<String, dynamic> json) {
    return Pool(
      id: json['id'] as String,
      code: json['code'] as String,
      creatorId: json['creatorId'] as String,
      currencyPair: json['currencyPair'] as String? ?? 'XOF/CNY',
      targetAmountXof: decimalStringFromJson(json['targetAmountXof']),
      currentAmountXof: decimalStringFromJson(json['currentAmountXof']),
      status: PoolStatus.fromCode(json['status'] as String?),
      participantCount: json['participantCount'] as int? ?? 0,
      rewardMarginReductionPercentage: decimalStringFromJson(json['rewardMarginReductionPercentage']),
      createdAt: DateTime.parse(json['createdAt'] as String),
      expiresAt: DateTime.parse(json['expiresAt'] as String),
      succeededAt: json['succeededAt'] == null ? null : DateTime.parse(json['succeededAt'] as String),
      expiredAt: json['expiredAt'] == null ? null : DateTime.parse(json['expiredAt'] as String),
      cancelledAt: json['cancelledAt'] == null ? null : DateTime.parse(json['cancelledAt'] as String),
      viewerIsParticipant: json['viewerIsParticipant'] as bool? ?? false,
      viewerIsCreator: json['viewerIsCreator'] as bool? ?? false,
    );
  }
}

/// Miroir de `PoolParticipantResponse` — `firstName` uniquement (jamais le nom
/// complet ni le telephone d'un tiers).
class PoolParticipant {
  final String userId;
  final String firstName;
  final bool isCreator;
  final DateTime joinedAt;
  final String contributedAmountXof;

  const PoolParticipant({
    required this.userId,
    required this.firstName,
    required this.isCreator,
    required this.joinedAt,
    required this.contributedAmountXof,
  });

  factory PoolParticipant.fromJson(Map<String, dynamic> json) {
    return PoolParticipant(
      userId: json['userId'] as String,
      firstName: json['firstName'] as String? ?? '?',
      isCreator: json['isCreator'] as bool? ?? false,
      joinedAt: DateTime.parse(json['joinedAt'] as String),
      contributedAmountXof: decimalStringFromJson(json['contributedAmountXof']),
    );
  }
}

/// Corps de creation — `durationMinutes` borne cote backend (5 a 180).
class CreatePoolRequest {
  final String targetAmountXof;
  final int durationMinutes;

  const CreatePoolRequest({required this.targetAmountXof, required this.durationMinutes});

  Map<String, dynamic> toJson() => {
        'targetAmountXof': targetAmountXof,
        'durationMinutes': durationMinutes,
      };
}
