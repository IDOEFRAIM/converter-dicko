/** GET /api/settings/public — bornes et parametres exposes aux clients anonymes. */
export interface PublicSettings {
  minOrderAmountCfa: string;
  maxOrderAmountCfa: string;
  rateLockDurationMinutes: number;
  maxProofFileSizeBytes: number;
  maxProofsPerPayment: number;
  enabledPaymentMethods: string[];
  requirePaymentProof: boolean;
  /** Instructions affichees au client pour savoir ou/comment envoyer son paiement (Mobile
   * Money, banque...) avant de le declarer -- voir migration V39 backend. */
  paymentInstructionsText: string;
}
