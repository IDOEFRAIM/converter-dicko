export type NotificationType =
  | 'QUOTE_CREATED'
  | 'PAYMENT_SUBMITTED'
  | 'PAYMENT_CONFIRMED'
  | 'EXCHANGE_STARTED'
  | 'EXCHANGE_PROGRESS'
  | 'EXCHANGE_COMPLETED'
  | 'PREFERRED_RATE_REACHED'
  | 'PREFERRED_RATE_EXPIRED'
  | 'EXCHANGE_CANCELLED'
  | 'ORDER_EXPIRED'
  | 'RATE_ALERT_TRIGGERED'
  | 'SUPPORT_REPLY';

export interface InboxNotification {
  id: string;
  type: NotificationType;
  title: string;
  message: string;
  createdAt: string;
  readAt: string | null;
}

/**
 * Mapping UI centralise des types de notification (icone Material + libelle de repli).
 * Le backend fournit deja {@code title}/{@code message} : {@code label} n'est utilise que
 * lorsqu'un regroupement/filtre par type est necessaire, jamais a la place du message backend.
 */
export const NOTIFICATION_TYPE_META: Record<NotificationType, { icon: string; label: string }> = {
  QUOTE_CREATED: { icon: 'request_quote', label: 'Devis' },
  PAYMENT_SUBMITTED: { icon: 'payments', label: 'Paiement declare' },
  PAYMENT_CONFIRMED: { icon: 'check_circle', label: 'Paiement confirme' },
  EXCHANGE_STARTED: { icon: 'sync', label: 'Echange demarre' },
  EXCHANGE_PROGRESS: { icon: 'hourglass_top', label: 'Echange en cours' },
  EXCHANGE_COMPLETED: { icon: 'task_alt', label: 'Echange termine' },
  PREFERRED_RATE_REACHED: { icon: 'trending_up', label: 'Taux preferentiel atteint' },
  PREFERRED_RATE_EXPIRED: { icon: 'schedule', label: 'Taux preferentiel expire' },
  EXCHANGE_CANCELLED: { icon: 'cancel', label: 'Echange annule' },
  ORDER_EXPIRED: { icon: 'timer_off', label: 'Ordre expire' },
  RATE_ALERT_TRIGGERED: { icon: 'notifications_active', label: 'Alerte de taux declenchee' },
  SUPPORT_REPLY: { icon: 'forum', label: 'Reponse du support' },
};

export function notificationIcon(type: string): string {
  return NOTIFICATION_TYPE_META[type as NotificationType]?.icon ?? 'notifications';
}
