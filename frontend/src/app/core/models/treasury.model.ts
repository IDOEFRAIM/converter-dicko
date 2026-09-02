export type Currency = 'XOF' | 'CNY';

export type TreasuryTransactionType = 'DEPOSIT' | 'WITHDRAWAL' | 'RESERVATION' | 'RELEASE' | 'ADJUSTMENT';

export interface TreasuryAccount {
  id: string;
  currency: Currency;
  balance: string;
  reservedBalance: string;
  available: string;
  lowThreshold: string;
  updatedAt: string;
}

export interface TreasuryTransaction {
  id: string;
  accountId: string;
  type: TreasuryTransactionType;
  amount: string;
  balanceAfter: string;
  reservedAfter: string;
  orderId: string | null;
  performedBy: string | null;
  reason: string | null;
  createdAt: string;
}

export interface TreasuryAdjustmentRequest {
  currency: Currency;
  amount: string;
  reason: string;
}
