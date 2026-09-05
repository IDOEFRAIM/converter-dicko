import { PreferredRateDirection } from './preferred-rate.model';

/**
 * Historique public du taux client XOF/CNY (`GET /api/v1/rates/history`) et alertes de taux
 * (`/api/v1/rate-alerts`). Le backend n'expose ici QUE le taux commercial — jamais le taux de
 * revient, la marge ou les frais internes. Convention identique aux devis : 1 CNY = customerRate XOF.
 */

export interface PublicRateHistoryEntry {
  currencyPair: string;
  customerRate: string;
  recordedAt: string;
}

export interface RateHistoryQuery {
  pair?: string;
  from?: string | null;
  to?: string | null;
  page?: number;
  size?: number;
}

// ---------------------------------------------------------------------
// Alertes de taux (Phase 6) — "previens-moi quand le taux atteint ma cible".
// Aucune transaction : ni devis, ni ordre, ni reservation Wallet/Treasury.
// ---------------------------------------------------------------------

export type RateAlertStatus = 'ACTIVE' | 'TRIGGERED' | 'CANCELLED' | 'EXPIRED';

/** Aligne sur `com.converter.rate.alert.domain.RateComparison`. */
export type RateComparison = 'LESS_THAN_OR_EQUAL' | 'GREATER_THAN_OR_EQUAL';

export interface RateAlert {
  id: string;
  currencyPair: string;
  direction: PreferredRateDirection;
  targetRate: string;
  comparison: RateComparison;
  status: RateAlertStatus;
  createdAt: string;
  expiresAt: string | null;
  triggeredAt: string | null;
  cancelledAt: string | null;
  expiredAt: string | null;
}

/**
 * `currencyPair`, `direction` et `comparison` sont optionnels : le backend les deduit de la
 * configuration actuelle du produit (une seule paire, un seul sens). Seul `targetRate` est
 * une decision strictement utilisateur. `expiresAt` optionnel — `null` = pas d'expiration.
 */
export interface CreateRateAlertRequest {
  targetRate: string;
  currencyPair?: string | null;
  direction?: PreferredRateDirection | null;
  comparison?: RateComparison | null;
  expiresAt?: string | null;
}

export const RATE_ALERT_STATUS_LABELS: Record<RateAlertStatus, string> = {
  ACTIVE: 'Active',
  TRIGGERED: 'Declenchee',
  CANCELLED: 'Annulee',
  EXPIRED: 'Expiree',
};
