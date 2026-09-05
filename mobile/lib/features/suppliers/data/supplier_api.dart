import '../../../core/network/api_client.dart';
import '../../../shared/models/page_response.dart';
import '../models/supplier_models.dart';

/// Acces a `GET /api/v1/suppliers` — le Home n'utilise que la liste pour
/// l'apercu "Mes fournisseurs" ; le carnet complet (creation, edition,
/// favoris, pay-again) arrive dans un lot ulterieur.
class SupplierApi {
  final ApiClient _client;

  SupplierApi(this._client);

  Future<PageResponse<SupplierSummary>> list({int page = 0, int size = 20, String? status}) async {
    final query = <String, dynamic>{'page': page, 'size': size};
    if (status != null) {
      query['status'] = status;
    }
    final body = await _client.get('/v1/suppliers', queryParameters: query);
    return PageResponse.fromJson(body['data'] as Map<String, dynamic>, SupplierSummary.fromJson);
  }
}
