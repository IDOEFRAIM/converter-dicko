import '../../../core/network/api_client.dart';
import '../../../shared/models/page_response.dart';
import '../models/wallet_models.dart';

/// Acces a `/api/v1/wallet` — consultation seule du solde interne XOF du
/// client et de son historique de mouvements. Aucun endpoint de rechargement
/// n'est expose (pas de canal d'alimentation reel a ce stade — voir
/// `WalletService` cote backend, meme choix que le frontend web).
class WalletApi {
  final ApiClient _client;

  WalletApi(this._client);

  /// Solde courant. Le wallet est cree cote backend au premier acces (a zero).
  Future<Wallet> snapshot() async {
    final body = await _client.get('/v1/wallet');
    return Wallet.fromJson(body['data'] as Map<String, dynamic>);
  }

  Future<PageResponse<WalletTransaction>> transactions({int page = 0, int size = 30}) async {
    final body = await _client.get(
      '/v1/wallet/transactions',
      queryParameters: {'page': page, 'size': size},
    );
    return PageResponse.fromJson(body['data'] as Map<String, dynamic>, WalletTransaction.fromJson);
  }
}
