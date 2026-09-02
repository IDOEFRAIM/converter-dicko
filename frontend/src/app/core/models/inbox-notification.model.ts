export type NotificationType =
  | 'QUOTE_CREATED'
  | 'PAYMENT_SUBMITTED'
  | 'PAYMENT_CONFIRMED'
  | 'EXCHANGE_STARTED'
  | 'EXCHANGE_PROGRESS'
  | 'EXCHANGE_COMPLETED'
  | 'PREFERRED_RATE_REACHED'
  | 'PREFERRED_RATE_EXPIRED'
  | 'EXCHANGE_CANCELLED';

export interface InboxNotification {
  id: string;
  type: NotificationType;
  title: string;
  message: string;
  createdAt: string;
  readAt: string | null;
}
