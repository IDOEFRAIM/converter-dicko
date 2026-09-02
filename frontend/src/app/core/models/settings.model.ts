/** GET /api/settings/public — bornes et parametres exposes aux clients anonymes. */
export interface PublicSettings {
  minOrderAmountCfa: string;
  maxOrderAmountCfa: string;
  rateLockDurationMinutes: number;
  maxProofFileSizeBytes: number;
  maxProofsPerPayment: number;
  enabledPaymentMethods: string[];
  requirePaymentProof: boolean;
}
