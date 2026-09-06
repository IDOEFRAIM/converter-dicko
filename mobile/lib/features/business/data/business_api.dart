import '../../../core/errors/api_exception.dart';
import '../../../core/network/api_client.dart';
import '../models/business_models.dart';

/// Acces a `/api/v1/business-profile` et `/api/v1/business/payments/summary`
/// (mission section 32/33).
class BusinessApi {
  final ApiClient _client;

  BusinessApi(this._client);

  /// Retourne null si aucun profil n'existe (`404 BUSINESS_PROFILE_NOT_FOUND`)
  /// — un compte "Personnel" normal, jamais une erreur a afficher.
  Future<BusinessProfile?> getProfile() async {
    try {
      final body = await _client.get('/v1/business-profile');
      return BusinessProfile.fromJson(body['data'] as Map<String, dynamic>);
    } on ApiException catch (error) {
      if (error.code == 'BUSINESS_PROFILE_NOT_FOUND') {
        return null;
      }
      rethrow;
    }
  }

  Future<BusinessProfile> upsertProfile(UpsertBusinessProfileRequest request) async {
    final body = await _client.put('/v1/business-profile', data: request.toJson());
    return BusinessProfile.fromJson(body['data'] as Map<String, dynamic>);
  }

  Future<BusinessPaymentSummary> paymentsSummary({DateTime? from, DateTime? to}) async {
    final query = <String, dynamic>{};
    if (from != null) query['from'] = from.toUtc().toIso8601String();
    if (to != null) query['to'] = to.toUtc().toIso8601String();
    final body = await _client.get('/v1/business/payments/summary', queryParameters: query);
    return BusinessPaymentSummary.fromJson(body['data'] as Map<String, dynamic>);
  }
}
