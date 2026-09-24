import 'package:flutter/foundation.dart';

import '../../../core/errors/api_exception.dart';
import '../data/pool_api.dart';
import '../models/pool_models.dart';

/// Etat de "Mes Ruees" : toutes les Ruees creees ou rejointes par le compte
/// connecte, les plus recentes d'abord (deja trie cote backend).
class MyPoolsController extends ChangeNotifier {
  final PoolApi _poolApi;

  MyPoolsController(this._poolApi);

  bool loading = true;
  String? errorMessage;
  List<Pool> pools = const [];

  List<Pool> get active => pools.where((p) => p.isActive).toList(growable: false);

  List<Pool> get closed => pools.where((p) => !p.isActive).toList(growable: false);

  Future<void> load() async {
    loading = true;
    notifyListeners();
    try {
      final page = await _poolApi.mine(size: 50);
      pools = page.content;
      errorMessage = null;
    } on ApiException catch (error) {
      errorMessage = error.message;
    }
    loading = false;
    notifyListeners();
  }
}
