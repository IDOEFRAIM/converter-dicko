export interface Wallet {
  id: string;
  balance: string;
  reservedBalance: string;
  available: string;
  updatedAt: string;
}

export type WalletTransactionType = 'CREDIT' | 'DEBIT' | 'RESERVE' | 'RELEASE';

export interface WalletTransaction {
  id: string;
  type: WalletTransactionType;
  amount: string;
  balanceAfter: string;
  reservedAfter: string;
  referenceId: string | null;
  reason: string | null;
  createdAt: string;
}
