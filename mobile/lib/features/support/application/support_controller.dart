import 'dart:async';

import 'package:flutter/foundation.dart';

import '../../../core/errors/api_exception.dart';
import '../data/support_api.dart';
import '../models/support_models.dart';

/// Messagerie SAV (retour client) : un seul fil continu. Rafraichi periodiquement (pas de
/// WebSocket dans ce MVP, meme discipline que le reste du paiement/reglement "entierement
/// manuel") pour voir arriver la reponse de l'admin sans quitter l'ecran.
class SupportController extends ChangeNotifier {
  final SupportApi _supportApi;
  Timer? _pollTimer;

  SupportController({required SupportApi supportApi}) : _supportApi = supportApi;

  bool loading = true;
  bool sending = false;
  String? errorMessage;
  List<SupportMessage> messages = const [];

  Future<void> load() async {
    await _load(showSpinner: true);
    _pollTimer ??= Timer.periodic(const Duration(seconds: 10), (_) => _load(showSpinner: false));
  }

  Future<void> _load({required bool showSpinner}) async {
    if (showSpinner) {
      loading = true;
      notifyListeners();
    }
    try {
      final thread = await _supportApi.myThread();
      messages = thread.messages;
      errorMessage = null;
    } on ApiException catch (error) {
      errorMessage = error.message;
    }
    loading = false;
    notifyListeners();
  }

  Future<bool> send(String body) async {
    final trimmed = body.trim();
    if (trimmed.isEmpty || sending) return false;
    sending = true;
    errorMessage = null;
    notifyListeners();
    try {
      final message = await _supportApi.send(trimmed);
      messages = [...messages, message];
      sending = false;
      notifyListeners();
      return true;
    } on ApiException catch (error) {
      errorMessage = error.message;
      sending = false;
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
