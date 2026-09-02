export type PreferredRateDirection = 'XOF_TO_CNY';

export type PreferredRateStatus = 'ACTIVE' | 'EXECUTED' | 'EXPIRED' | 'CANCELLED';

export type ExchangeStatus = 'STARTED' | 'COMPLETED' | 'CANCELLED';

/** Vue affichage : quel ecran presenter. Calculee par le backend, jamais deduite cote frontend. */
export type PreferredRatePhase = 'WAITING' | 'EXCHANGE_IN_PROGRESS' | 'EXCHANGE_COMPLETED' | 'EXPIRED' | 'CANCELLED';

export type ExchangeStage = 'STARTED' | 'PROGRESS_45' | 'PROGRESS_90' | 'COMPLETED';

export interface ExchangeSummary {
  id: string;
  amountXof: string;
  achievedRate: string;
  amountCny: string;
  status: ExchangeStatus;
  stage: ExchangeStage;
  startedAt: string;
  deadlineAt: string;
  nextUpdateAt: string | null;
  completedAt: string | null;
}

/** currentRate/gap sont calcules par le backend (RateProvider + marge) — jamais cote frontend. */
export interface PreferredRateRequest {
  id: string;
  direction: PreferredRateDirection;
  amountXof: string;
  targetRate: string;
  currentRate: string | null;
  /** BigDecimal serialise en nombre JSON natif par Jackson (pas de guillemets), contrairement aux autres champs montant/taux. */
  gap: number | null;
  phase: PreferredRatePhase;
  status: PreferredRateStatus;
  achievedRate: string | null;
  createdAt: string;
  expiresAt: string;
  executedAt: string | null;
  expiredAt: string | null;
  cancelledAt: string | null;
  exchange: ExchangeSummary | null;
}

export interface CreatePreferredRateRequest {
  direction: PreferredRateDirection;
  amountXof: string;
  targetRate: string;
}
