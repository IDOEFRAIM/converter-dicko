export type RateProviderType = 'MANUAL' | 'MARKET' | 'P2P';

/** Reserve a l'administration : jamais expose au client. */
export interface RateSource {
  id: string;
  providerType: RateProviderType;
  currencyPair: string;
  cfaPerCny: string;
  effectiveFrom: string;
  effectiveTo: string | null;
  note: string | null;
  createdAt: string;
}

export interface PublishRateRequest {
  cfaPerCny: string;
  note: string | null;
}
