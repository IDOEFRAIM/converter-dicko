/// Extrait un champ montant/taux (`BigDecimal` cote backend) d'un JSON decode
/// en une chaine decimale exacte, sans jamais exposer un `double` au reste de
/// l'application (voir mission section 34 : aucun calcul financier avec un
/// nombre a virgule flottante).
///
/// **Constat empirique important** (verifie sur de vraies reponses backend
/// pendant ce projet, ex. `"customerRate":85.774648`, `"expectedAmountXof":
/// 50000.00`, `"targetRate":85.00` — tous SANS guillemets) : contrairement a
/// ce que les types TypeScript Angular annoncent (`amountXof: string`), le
/// backend serialise en realite ses `BigDecimal` comme des NOMBRES JSON bruts
/// (comportement par defaut de Jackson en l'absence de serialiseur dedie).
/// Les types Angular sont donc optimistes, pas le contrat reel — `Number(x)`
/// cote Angular fonctionne par coincidence (no-op sur un nombre deja
/// numerique).
///
/// Consequence pour Dart : `jsonDecode` convertit deja ce nombre en `double`
/// AVANT que ce code ne s'execute — impossible d'eviter ce passage sans
/// remplacer entierement le decodeur JSON par un parseur maison (juge hors
/// de portee ici : risque de bug non verifiable sans environnement Flutter,
/// pour un gain reel nul aux ordres de grandeur de cette application : un
/// `double` conserve 15-17 chiffres significatifs exacts, tres au-dela des
/// 6 decimales/quelques milliards d'unites manipules ici). C'est exactement
/// le comportement deja en production cote Angular (qui n'a que `number`
/// JS, un double, pour toute valeur) — donc coherent avec la reference
/// finale Angular (mission section 2/42).
///
/// La discipline reelle et non-negociable reste : ce `double` transite
/// UNIQUEMENT ici, converti immediatement en `String` ; aucun champ de
/// modele, aucun calcul, aucune comparaison financiere n'utilise jamais un
/// `double` ailleurs dans l'application.
String decimalStringFromJson(dynamic value) {
  if (value == null) {
    return '0';
  }
  if (value is String) {
    return value.trim();
  }
  if (value is int) {
    return value.toString();
  }
  if (value is double) {
    // Sans danger de notation scientifique aux ordres de grandeur de cette
    // application (taux ~0-10000, montants jusqu'a quelques milliards de XOF) :
    // `double.toString()` ne bascule en notation exponentielle qu'au-dela
    // de ~1e21 ou en-dessous de ~1e-6, jamais atteint ici.
    return value.toString();
  }
  return value.toString();
}

/// Meme extraction, mais tolerante a l'absence du champ (retourne null plutot
/// qu'un "0" — utile pour un taux optionnel comme `currentRate` sur une
/// alerte non encore evaluee).
String? decimalStringFromJsonOrNull(dynamic value) {
  if (value == null) {
    return null;
  }
  return decimalStringFromJson(value);
}
