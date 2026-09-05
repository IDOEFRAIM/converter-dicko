import 'package:intl/intl.dart';

/// Formatage de dates pour l'affichage — le backend renvoie toujours un
/// `Instant` UTC ISO-8601 (ex. `"2026-09-05T19:55:27Z"`) ; `DateTime.parse`
/// le reconnait nativement et produit un `DateTime` UTC, converti ici en
/// heure locale uniquement pour l'affichage (mission section 35 : jamais de
/// modification de la donnee source, seulement une conversion d'affichage).
abstract final class DateFormatting {
  static final _dayTime = DateFormat('dd/MM/yyyy HH:mm');
  static final _dayOnly = DateFormat('dd/MM/yyyy');

  static String dayTime(DateTime utcInstant) => _dayTime.format(utcInstant.toLocal());

  static String dayOnly(DateTime utcInstant) => _dayOnly.format(utcInstant.toLocal());
}
