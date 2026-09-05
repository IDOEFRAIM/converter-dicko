/// Echelle d'espacement unique — evite les valeurs magiques (`SizedBox(height: 13)`)
/// eparpillees dans les widgets (mission section 16/44).
abstract final class AppSpacing {
  static const xs = 4.0;
  static const sm = 8.0;
  static const md = 12.0;
  static const lg = 16.0;
  static const xl = 24.0;
  static const xxl = 32.0;
  static const xxxl = 48.0;

  /// Rayon de coin standard des panneaux/cartes — sobre, pas de "gros arrondi" (section 6).
  static const radiusMd = 14.0;
  static const radiusSm = 10.0;
  static const radiusPill = 999.0;
}
