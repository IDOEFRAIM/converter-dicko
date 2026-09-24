import 'package:flutter/foundation.dart';

import '../../../core/errors/api_exception.dart';
import '../data/rate_history_api.dart';
import '../models/rate_models.dart';

class RateVariation {
  final double percent;

  const RateVariation(this.percent);
}

/// Etat de l'ecran "Historique du taux" (mission section 29). Aucune donnee
/// fictive : min/max et variation ne sont calcules QUE si les points reels
/// necessaires sont disponibles (section 37 — jamais de "variation du jour"
/// inventee).
class RateHistoryController extends ChangeNotifier {
  final RateHistoryApi _api;

  RateHistoryController(this._api);

  bool loading = true;
  String? errorMessage;
  List<PublicRateHistoryEntry> entries = const [];

  PublicRateHistoryEntry? get latest => entries.isEmpty ? null : entries.first;

  /// Le backend renvoie du plus recent au plus ancien ; la courbe se lit
  /// ancien -> recent.
  List<PublicRateHistoryEntry> get chronological => entries.reversed.toList(growable: false);

  (double min, double max)? get range {
    if (entries.isEmpty) return null;
    final values = entries.map((e) => double.parse(e.customerRate)).toList();
    return (values.reduce((a, b) => a < b ? a : b), values.reduce((a, b) => a > b ? a : b));
  }

  /// Variation par rapport au releve precedent — jamais "aujourd'hui" (le
  /// backend n'a pas de notion de journee, seulement des publications
  /// ponctuelles).
  RateVariation? get variation {
    final series = chronological;
    if (series.length < 2) return null;
    final previous = double.parse(series[series.length - 2].customerRate);
    final current = double.parse(series[series.length - 1].customerRate);
    if (previous == 0) return null;
    return RateVariation(((current - previous) / previous) * 100);
  }

  Future<void> load() async {
    loading = true;
    notifyListeners();
    try {
      final page = await _api.history(size: 60);
      entries = page.content;
      errorMessage = null;
    } on ApiException catch (error) {
      errorMessage = error.message;
    }
    loading = false;
    notifyListeners();
  }
}
