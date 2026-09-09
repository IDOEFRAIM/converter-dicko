import '../../../shared/utils/json_decimal.dart';

/// "Mes gains" — miroir de `AchievementSummaryResponse` backend. Purement
/// derive des ordres deja COMPLETED, jamais un recalcul de taux/frais ni une
/// donnee inventee : `totalAmountXofCompleted`/`currentMonthAmountXofCompleted`
/// sont un volume transfere reel, jamais presentes comme une "economie" par
/// rapport a un concurrent (aucune donnee de reference pour l'affirmer
/// honnetement).
///
/// `badgeCode`/`badgeLabel` sont `null` pour un profil PRO (aucune
/// gamification, voir mission "differenciation marketing") ou pour un profil
/// STUDENT_* qui n'a encore aucun transfert termine.
class AchievementSummary {
  final String experienceProfile;
  final int completedTransferCount;
  final String totalAmountXofCompleted;
  final String currentMonthAmountXofCompleted;
  final int poolsSucceededCount;
  final int xp;
  final String? badgeCode;
  final String? badgeLabel;
  final String? nextBadgeLabel;
  final int? transfersUntilNextBadge;

  /// Bornes (nombre de transferts COMPLETED) du palier courant et du suivant —
  /// pour tracer une progression honnete dans le rang sans repliquer le
  /// bareme cote client. `null` pour PRO / aucun badge, et
  /// `nextBadgeThreshold` `null` au palier maximal.
  final int? currentBadgeThreshold;
  final int? nextBadgeThreshold;

  const AchievementSummary({
    required this.experienceProfile,
    required this.completedTransferCount,
    required this.totalAmountXofCompleted,
    required this.currentMonthAmountXofCompleted,
    required this.poolsSucceededCount,
    required this.xp,
    required this.badgeCode,
    required this.badgeLabel,
    required this.nextBadgeLabel,
    required this.transfersUntilNextBadge,
    required this.currentBadgeThreshold,
    required this.nextBadgeThreshold,
  });

  bool get hasBadge => badgeLabel != null;

  /// Avancement 0..1 dans le rang courant vers le suivant. `null` si non
  /// applicable (PRO / aucun transfert) ; `1.0` au palier maximal.
  double? get tierProgress {
    if (!hasBadge) return null;
    if (nextBadgeThreshold == null) return 1;
    final floor = currentBadgeThreshold ?? 0;
    final span = nextBadgeThreshold! - floor;
    if (span <= 0) return 1;
    return ((completedTransferCount - floor) / span).clamp(0.0, 1.0);
  }

  factory AchievementSummary.fromJson(Map<String, dynamic> json) {
    return AchievementSummary(
      experienceProfile: json['experienceProfile'] as String? ?? 'PRO',
      completedTransferCount: json['completedTransferCount'] as int? ?? 0,
      totalAmountXofCompleted: decimalStringFromJson(json['totalAmountXofCompleted']),
      currentMonthAmountXofCompleted: decimalStringFromJson(json['currentMonthAmountXofCompleted']),
      poolsSucceededCount: json['poolsSucceededCount'] as int? ?? 0,
      xp: json['xp'] as int? ?? 0,
      badgeCode: json['badgeCode'] as String?,
      badgeLabel: json['badgeLabel'] as String?,
      nextBadgeLabel: json['nextBadgeLabel'] as String?,
      transfersUntilNextBadge: json['transfersUntilNextBadge'] as int?,
      currentBadgeThreshold: json['currentBadgeThreshold'] as int?,
      nextBadgeThreshold: json['nextBadgeThreshold'] as int?,
    );
  }
}
