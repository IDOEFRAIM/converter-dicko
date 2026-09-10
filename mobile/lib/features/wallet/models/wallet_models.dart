import '../../../shared/utils/json_decimal.dart';

/// Miroir de `com.converter.wallet.domain.WalletTransactionType`.
enum WalletTransactionType {
  credit,
  debit,
  reserve,
  release,
  unknown;

  static WalletTransactionType fromCode(String? code) => switch (code) {
        'CREDIT' => WalletTransactionType.credit,
        'DEBIT' => WalletTransactionType.debit,
        'RESERVE' => WalletTransactionType.reserve,
        'RELEASE' => WalletTransactionType.release,
        _ => WalletTransactionType.unknown,
      };

  String get label => switch (this) {
        WalletTransactionType.credit => 'Depot',
        WalletTransactionType.debit => 'Debit',
        WalletTransactionType.reserve => 'Reservation',
        WalletTransactionType.release => 'Liberation',
        WalletTransactionType.unknown => 'Mouvement',
      };
}

/// Miroir de `com.converter.wallet.dto.WalletResponse`. Le solde disponible
/// est fourni tel quel par le backend (`balance - reservedBalance`) : jamais
/// recalcule ici. Toutes les valeurs restent des chaines decimales exactes
/// (voir `json_decimal.dart`) — aucun `double`, aucun calcul financier cote
/// mobile.
class Wallet {
  final String id;
  final String balance;
  final String reservedBalance;
  final String available;
  final DateTime updatedAt;

  const Wallet({
    required this.id,
    required this.balance,
    required this.reservedBalance,
    required this.available,
    required this.updatedAt,
  });

  factory Wallet.fromJson(Map<String, dynamic> json) {
    return Wallet(
      id: json['id'] as String,
      balance: decimalStringFromJson(json['balance']),
      reservedBalance: decimalStringFromJson(json['reservedBalance']),
      available: decimalStringFromJson(json['available']),
      updatedAt: DateTime.parse(json['updatedAt'] as String),
    );
  }
}

/// Miroir de `com.converter.wallet.dto.WalletTransactionResponse` — une
/// ecriture du ledger append-only. `balanceAfter`/`reservedAfter` capturent
/// le solde resultant, affiches tels quels.
class WalletTransaction {
  final String id;
  final WalletTransactionType type;
  final String amount;
  final String balanceAfter;
  final String reservedAfter;
  final String? referenceId;
  final String? reason;
  final DateTime createdAt;

  const WalletTransaction({
    required this.id,
    required this.type,
    required this.amount,
    required this.balanceAfter,
    required this.reservedAfter,
    required this.referenceId,
    required this.reason,
    required this.createdAt,
  });

  factory WalletTransaction.fromJson(Map<String, dynamic> json) {
    return WalletTransaction(
      id: json['id'] as String,
      type: WalletTransactionType.fromCode(json['type'] as String?),
      amount: decimalStringFromJson(json['amount']),
      balanceAfter: decimalStringFromJson(json['balanceAfter']),
      reservedAfter: decimalStringFromJson(json['reservedAfter']),
      referenceId: json['referenceId'] as String?,
      reason: json['reason'] as String?,
      createdAt: DateTime.parse(json['createdAt'] as String),
    );
  }
}
