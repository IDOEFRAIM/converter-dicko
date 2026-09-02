export type PaymentMethod = 'MOBILE_MONEY' | 'WAVE' | 'BANK_TRANSFER';
export type PaymentStatus = 'SUBMITTED' | 'CONFIRMED' | 'REJECTED';

export interface PaymentProof {
  id: string;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  uploadedAt: string;
}

export interface Payment {
  id: string;
  orderId: string;
  method: PaymentMethod;
  status: PaymentStatus;
  expectedAmountXof: string;
  receivedAmountXof: string;
  transactionReference: string;
  payerPhone: string | null;
  rejectionReason: string | null;
  proofs: PaymentProof[];
  submittedAt: string;
  confirmedAt: string | null;
  rejectedAt: string | null;
}

export interface SubmitPaymentRequest {
  method: PaymentMethod;
  receivedAmountXof: string;
  transactionReference: string;
  payerPhone: string | null;
}

export interface RejectPaymentRequest {
  reason: string;
}
