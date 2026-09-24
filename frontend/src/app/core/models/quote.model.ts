export type QuoteDirection = 'SEND_XOF' | 'RECEIVE_CNY';
export type QuoteStatus = 'ACTIVE' | 'ACCEPTED' | 'EXPIRED' | 'CANCELLED';

/**
 * Devis tel que renvoye par le backend. Ne contient ni marketRate ni
 * margin (jamais exposes au client) — customerRate est le seul taux
 * que le client voit. Le frontend n'effectue AUCUN calcul financier :
 * ces valeurs sont affichees telles quelles.
 */
export interface Quote {
  id: string;
  direction: QuoteDirection;
  amountXof: string;
  amountCny: string;
  customerRate: string;
  feeXof: string;
  netAmountXof: string;
  status: QuoteStatus;
  createdAt: string;
  expiresAt: string;
  /** Vrai si le rabais d'une Ruee reussie a ete applique a ce devis. */
  poolRewardApplied?: boolean;
}

export interface CreateQuoteRequest {
  direction: QuoteDirection;
  amountXof: string | null;
  amountCny: string | null;
}
