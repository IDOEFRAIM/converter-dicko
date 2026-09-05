import { OrderStatus } from './order.model';

/**
 * Timeline de suivi d'un ordre — projection en lecture seule de l'etat backend
 * ({@code OrderStatus}/{@code OrderStatusHistory}/{@code Payment}/{@code Settlement}/
 * {@code Refund}). Le frontend n'invente aucune transition : il affiche ce que le backend
 * calcule, en testant {@code code} (jamais {@code label}, qui n'est qu'un libelle d'affichage).
 */

/** Catalogue ferme, aligne sur `com.converter.order.dto.TrackingEventCode`. */
export type TrackingEventCode =
  | 'ORDER_CREATED'
  | 'PAYMENT_SUBMITTED'
  | 'PAYMENT_VERIFIED'
  | 'PAYMENT_REJECTED'
  | 'PROCESSING'
  | 'SETTLEMENT_EXECUTED'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'REJECTED'
  | 'EXPIRED'
  | 'REFUND_PENDING'
  | 'REFUND_PROCESSED';

export interface TrackingEvent {
  code: TrackingEventCode;
  /** Renseigne uniquement si l'evenement correspond a une valeur reelle de `OrderStatus`. */
  status: OrderStatus | null;
  occurredAt: string;
  label: string;
}

export interface OrderTracking {
  orderId: string;
  currentStatus: OrderStatus;
  createdAt: string;
  completedAt: string | null;
  timeline: TrackingEvent[];
}

/**
 * Mapping UI central, base uniquement sur `code` (jamais le `label` renvoye par le backend,
 * qui reste une commodite serveur independante) — section 10 : le frontend garde la main sur
 * le ton exact, aligne sur le nouveau positionnement ("transfert", pas "ordre").
 */
export const TRACKING_EVENT_LABELS: Record<TrackingEventCode, string> = {
  ORDER_CREATED: 'Transfert cree',
  PAYMENT_SUBMITTED: 'Paiement soumis',
  PAYMENT_VERIFIED: 'Paiement verifie',
  PAYMENT_REJECTED: 'Paiement rejete',
  PROCESSING: 'Traitement en cours',
  SETTLEMENT_EXECUTED: 'Reglement effectue',
  COMPLETED: 'Transfert termine',
  CANCELLED: 'Transfert annule',
  REJECTED: 'Transfert rejete',
  EXPIRED: 'Transfert expire',
  REFUND_PENDING: 'Remboursement en cours',
  REFUND_PROCESSED: 'Remboursement effectue',
};

/** Evenements qui representent un echec / une fin non nominale (rendu visuel distinct). */
export const NEGATIVE_TRACKING_CODES: ReadonlySet<TrackingEventCode> = new Set([
  'PAYMENT_REJECTED',
  'CANCELLED',
  'REJECTED',
  'EXPIRED',
]);
