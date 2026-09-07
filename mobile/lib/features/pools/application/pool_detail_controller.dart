import 'dart:async';

import 'package:flutter/foundation.dart';

import '../../../core/errors/api_exception.dart';
import '../data/pool_api.dart';
import '../models/pool_models.dart';

/// Etat de l'ecran detail d'une Ruee : thermometre en "temps reel" simule par
/// sondage periodique (aucune infrastructure WebSocket/SSE cote backend --
/// voir la note de PoolApi) tant que la Ruee est encore ACTIVE et que l'ecran
/// est visible. S'arrete de lui-meme des que le statut n'est plus ACTIVE.
class PoolDetailController extends ChangeNotifier {
  final PoolApi _poolApi;
  final String poolId;
  static const _pollInterval = Duration(seconds: 4);

  PoolDetailController({required PoolApi poolApi, required this.poolId}) : _poolApi = poolApi;

  bool loading = true;
  String? errorMessage;
  Pool? pool;
  List<PoolParticipant> participants = const [];

  bool joining = false;
  String? joinErrorMessage;
  bool cancelling = false;

  Timer? _pollTimer;

  Future<void> load() async {
    loading = true;
    notifyListeners();
    await _refresh();
    loading = false;
    notifyListeners();
    _schedulePolling();
  }

  Future<void> _refresh() async {
    try {
      final results = await Future.wait([_poolApi.get(poolId), _poolApi.participants(poolId)]);
      pool = results[0] as Pool;
      participants = results[1] as List<PoolParticipant>;
      errorMessage = null;
    } on ApiException catch (error) {
      errorMessage = error.message;
    }
  }

  void _schedulePolling() {
    _pollTimer?.cancel();
    if (pool?.isActive != true) {
      return;
    }
    _pollTimer = Timer(_pollInterval, () async {
      await _refresh();
      notifyListeners();
      _schedulePolling();
    });
  }

  Future<bool> join() async {
    if (joining) return false;
    joining = true;
    joinErrorMessage = null;
    notifyListeners();
    try {
      pool = await _poolApi.join(poolId);
      joining = false;
      notifyListeners();
      _schedulePolling();
      return true;
    } on ApiException catch (error) {
      joinErrorMessage = error.message;
      joining = false;
      notifyListeners();
      return false;
    }
  }

  Future<bool> cancel() async {
    if (cancelling) return false;
    cancelling = true;
    notifyListeners();
    try {
      pool = await _poolApi.cancel(poolId);
      cancelling = false;
      notifyListeners();
      return true;
    } on ApiException catch (error) {
      errorMessage = error.message;
      cancelling = false;
      notifyListeners();
      return false;
    }
  }

  @override
  void dispose() {
    _pollTimer?.cancel();
    super.dispose();
  }
}
