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
