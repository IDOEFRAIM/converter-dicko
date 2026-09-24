/// Devises supportees — miroir de `com.converter.treasury.domain.Currency`.
enum AppCurrency {
  xof,
  cny;

  static AppCurrency fromCode(String code) {
    switch (code) {
      case 'CNY':
        return AppCurrency.cny;
      case 'XOF':
      default:
        return AppCurrency.xof;
    }
  }

  String get code => this == AppCurrency.cny ? 'CNY' : 'XOF';

  /// Decimales d'affichage usuelles : le XOF n'a pas de sous-unite, le CNY en a deux
  /// (voir `MoneyPipe` cote Angular).
  int get displayDecimals => this == AppCurrency.cny ? 2 : 0;
}

/// Montant monetaire tel que renvoye par le backend : une chaine decimale
/// exacte (serialisation Jackson d'un `BigDecimal`), JAMAIS convertie en
/// `double` — un `double` ne peut pas representer certaines valeurs
/// decimales exactement et introduirait une derive sur des montants
/// financiers (mission section 34). Flutter est une couche d'affichage :
/// aucun calcul financier n'est fait ici, seulement du formatage textuel.
class Money {
  /// Valeur brute exactement telle que recue du backend (ex. "1000000.00").
  final String raw;
  final AppCurrency currency;

  const Money(this.raw, this.currency);

  factory Money.xof(String raw) => Money(raw, AppCurrency.xof);

  factory Money.cny(String raw) => Money(raw, AppCurrency.cny);

  /// Signe du montant, deduit du prefixe textuel — jamais d'une conversion numerique.
  bool get isNegative => raw.trimLeft().startsWith('-');

  /// Formate pour l'affichage avec separateurs de milliers et le nombre de
  /// decimales usuel de la devise, par manipulation de chaines/entiers
  /// (`BigInt`) uniquement — aucune arithmetique a virgule flottante.
  String formatted() {
    final normalized = _normalize(raw);
    if (normalized == null) {
      return '—';
    }
    final (integerPart, fractionPart) = _roundTo(normalized, currency.displayDecimals);
    final grouped = _groupThousands(integerPart);
    if (currency.displayDecimals == 0) {
      return grouped;
    }
    return '$grouped,$fractionPart';
  }

  /// Formate avec le code devise a la suite (ex. "1 000 000 XOF").
  String formattedWithCurrency() => '${formatted()} ${currency.code}';

  static (String, bool)? _split(String value) {
    final trimmed = value.trim();
    if (trimmed.isEmpty) {
      return null;
    }
    final negative = trimmed.startsWith('-');
    final unsigned = negative ? trimmed.substring(1) : trimmed;
    if (!RegExp(r'^\d+(\.\d+)?$').hasMatch(unsigned)) {
      return null;
    }
    return (unsigned, negative);
  }

  static _Normalized? _normalize(String value) {
    final split = _split(value);
    if (split == null) {
      return null;
    }
    final (unsigned, negative) = split;
    final dotIndex = unsigned.indexOf('.');
    if (dotIndex == -1) {
      return _Normalized(unsigned, '', negative);
    }
    return _Normalized(unsigned.substring(0, dotIndex), unsigned.substring(dotIndex + 1), negative);
  }

  /// Arrondit `fraction` a `decimals` chiffres (arrondi au plus proche, moitie
  /// vers le haut) en propageant une eventuelle retenue sur la partie
  /// entiere — entierement en arithmetique entiere (`BigInt`).
  static (String, String) _roundTo(_Normalized value, int decimals) {
    var integerDigits = BigInt.parse(value.integerPart);
    final fraction = value.fractionPart;
    String roundedFraction;

    if (fraction.length <= decimals) {
      roundedFraction = fraction.padRight(decimals, '0');
    } else {
      final kept = fraction.substring(0, decimals);
      final firstDropped = fraction.codeUnitAt(decimals) - '0'.codeUnitAt(0);
      var keptDigits = decimals == 0 ? BigInt.zero : BigInt.parse(kept);

      if (firstDropped >= 5) {
        if (decimals == 0) {
          integerDigits += BigInt.one;
        } else {
          final base = BigInt.from(10).pow(decimals);
          keptDigits += BigInt.one;
          if (keptDigits >= base) {
            keptDigits -= base;
            integerDigits += BigInt.one;
          }
        }
      }
      roundedFraction = decimals == 0 ? '' : keptDigits.toString().padLeft(decimals, '0');
    }

    // Le signe n'est affiche que si la magnitude arrondie est reellement non
    // nulle (evite un "-0" pour une valeur comme "-0.001" arrondie a 0 decimale).
    final isZero = integerDigits == BigInt.zero && !roundedFraction.contains(RegExp(r'[1-9]'));
    final sign = value.negative && !isZero ? '-' : '';
    return ('$sign$integerDigits', roundedFraction);
  }

  static String _groupThousands(String integerPart) {
    final negative = integerPart.startsWith('-');
    final digits = negative ? integerPart.substring(1) : integerPart;
    final buffer = StringBuffer();
    for (var i = 0; i < digits.length; i++) {
      if (i > 0 && (digits.length - i) % 3 == 0) {
        buffer.write(' ');
      }
      buffer.write(digits[i]);
    }
    return negative ? '-${buffer.toString()}' : buffer.toString();
  }
}

class _Normalized {
  final String integerPart;
  final String fractionPart;
  final bool negative;

  const _Normalized(this.integerPart, this.fractionPart, this.negative);
}
