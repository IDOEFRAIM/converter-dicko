/// Motif d'un transfert — partage entre Order et Supplier (miroir de
/// `com.converter.supplier.domain.Purpose`). Jamais recalcule ni invente cote
/// mobile : le motif par defaut d'un fournisseur n'est qu'une pre-selection,
/// jamais copie aveuglement sur un ordre (mission section 21).
enum Purpose {
  personal,
  education,
  familySupport,
  importGoods,
  services,
  business,
  other;

  static Purpose? fromCode(String? code) {
    switch (code) {
      case 'PERSONAL':
        return Purpose.personal;
      case 'EDUCATION':
        return Purpose.education;
      case 'FAMILY_SUPPORT':
        return Purpose.familySupport;
      case 'IMPORT_GOODS':
        return Purpose.importGoods;
      case 'SERVICES':
        return Purpose.services;
      case 'BUSINESS':
        return Purpose.business;
      case 'OTHER':
        return Purpose.other;
      default:
        return null;
    }
  }

  String get code => switch (this) {
        Purpose.personal => 'PERSONAL',
        Purpose.education => 'EDUCATION',
        Purpose.familySupport => 'FAMILY_SUPPORT',
        Purpose.importGoods => 'IMPORT_GOODS',
        Purpose.services => 'SERVICES',
        Purpose.business => 'BUSINESS',
        Purpose.other => 'OTHER',
      };

  /// Libelle francais — miroir exact de `PURPOSE_LABELS` cote Angular.
  String get label => switch (this) {
        Purpose.personal => 'Personnel',
        Purpose.education => 'Études',
        Purpose.familySupport => 'Soutien familial',
        Purpose.importGoods => 'Import de marchandises',
        Purpose.services => 'Services',
        Purpose.business => 'Professionnel',
        Purpose.other => 'Autre',
      };

  static const List<Purpose> options = Purpose.values;
}
