import '../../../core/network/api_client.dart';
import '../models/quote_models.dart';

class QuoteApi {
  final ApiClient _client;

  QuoteApi(this._client);

  Future<Quote> create(CreateQuoteRequest request) async {
    final body = await _client.post('/v1/quotes', data: request.toJson());
    return Quote.fromJson(body['data'] as Map<String, dynamic>);
  }

  Future<Quote> get(String id) async {
    final body = await _client.get('/v1/quotes/$id');
    return Quote.fromJson(body['data'] as Map<String, dynamic>);
  }

  Future<Quote> accept(String id) async {
    final body = await _client.post('/v1/quotes/$id/accept');
    return Quote.fromJson(body['data'] as Map<String, dynamic>);
  }

  Future<Quote> cancel(String id) async {
    final body = await _client.post('/v1/quotes/$id/cancel');
    return Quote.fromJson(body['data'] as Map<String, dynamic>);
  }
}
