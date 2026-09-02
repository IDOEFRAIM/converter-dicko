export type OrderStatus =
  | 'AWAITING_PAYMENT'
  | 'PAYMENT_SUBMITTED'
  | 'PAYMENT_VERIFIED'
  | 'PROCESSING'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'REJECTED'
  | 'EXPIRED';

export type BeneficiaryType = 'ALIPAY' | 'WECHAT_PAY' | 'CHINESE_BANK_ACCOUNT';

export interface Beneficiary {
  type: BeneficiaryType;
  fullName: string;
  identifier: string;
  bankName: string | null;
  bankBranch: string | null;
}

export interface BeneficiaryRequest {
  type: BeneficiaryType;
  fullName: string;
  identifier: string;
  bankName: string | null;
  bankBranch: string | null;
}

export interface CreateOrderRequest {
  quoteId: string;
  beneficiary: BeneficiaryRequest;
  note: string | null;
}

export interface CancelOrderRequest {
  reason: string;
}

export interface OrderStatusHistoryEntry {
  fromStatus: OrderStatus | null;
  toStatus: OrderStatus;
  changedBy: string | null;
  reason: string | null;
  createdAt: string;
}

export interface OrderSummary {
  id: string;
  reference: string;
  status: OrderStatus;
  amountXof: string;
  amountCny: string;
  createdAt: string;
}

export interface OrderDetail {
  id: string;
  reference: string;
  quoteId: string;
  status: OrderStatus;
  amountXof: string;
  amountCny: string;
  customerRate: string;
  feeXof: string;
  netAmountXof: string;
  note: string | null;
  cancellationReason: string | null;
  rejectionReason: string | null;
  beneficiary: Beneficiary;
  statusHistory: OrderStatusHistoryEntry[];
  createdAt: string;
  updatedAt: string;
  completedAt: string | null;
  cancelledAt: string | null;
}
