import 'package:flutter/foundation.dart';

import '../../../core/errors/api_exception.dart';
import '../data/supplier_api.dart';
import '../models/supplier_models.dart';

enum SupplierListFilter { all, favorites, deactivated }

/// Trois onglets (Tous/Favoris/Desactives) — filtre serveur, jamais un
/// filtrage local sur une seule page deja chargee (mission section 21).
class SupplierListController extends ChangeNotifier {
  final SupplierApi _supplierApi;

  SupplierListController(this._supplierApi);

  SupplierListFilter filter = SupplierListFilter.all;
  bool loading = true;
  String? errorMessage;
  List<SupplierSummary> suppliers = [];

  /// Filtre texte APPLIQUE UNIQUEMENT sur la page deja chargee (max 50 lignes) —
  /// il n'existe aucun endpoint de recherche plein texte cote backend (miroir
  /// assume du meme compromis honnete que cote Angular).
  String searchText = '';

  List<SupplierSummary> get filteredSuppliers {
    if (searchText.trim().isEmpty) return suppliers;
    final needle = searchText.trim().toLowerCase();
    return suppliers.where((s) => s.displayName.toLowerCase().contains(needle)).toList(growable: false);
  }

  void setSearchText(String value) {
    searchText = value;
    notifyListeners();
  }

  Future<void> setFilter(SupplierListFilter newFilter) async {
    filter = newFilter;
    await load();
  }

  Future<void> load() async {
    loading = true;
    notifyListeners();
    try {
      final page = switch (filter) {
        SupplierListFilter.all => await _supplierApi.list(size: 50, status: 'ACTIVE'),
        SupplierListFilter.favorites => await _supplierApi.listFavorites(size: 50),
        SupplierListFilter.deactivated => await _supplierApi.list(size: 50, status: 'INACTIVE'),
      };
      suppliers = page.content;
      errorMessage = null;
    } on ApiException catch (error) {
      errorMessage = error.message;
    }
    loading = false;
    notifyListeners();
  }
}
