import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

type KnownStatus =
  | 'AWAITING_PAYMENT'
  | 'PAYMENT_SUBMITTED'
  | 'PAYMENT_VERIFIED'
  | 'PROCESSING'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'REJECTED'
  | 'EXPIRED'
  | 'SUBMITTED'
  | 'CONFIRMED'
  | 'ACTIVE'
  | 'ACCEPTED'
  | 'PENDING'
  | 'EXECUTED'
  | 'INACTIVE'
  | 'TRIGGERED'
  | 'BLOCKED';

const LABELS: Record<string, string> = {
  AWAITING_PAYMENT: 'En attente de paiement',
  PAYMENT_SUBMITTED: 'Paiement soumis',
  PAYMENT_VERIFIED: 'Paiement verifie',
  PROCESSING: 'En cours de traitement',
  COMPLETED: 'Termine',
  CANCELLED: 'Annule',
  REJECTED: 'Rejete',
  EXPIRED: 'Expire',
  SUBMITTED: 'Soumis',
  CONFIRMED: 'Confirme',
  ACTIVE: 'Actif',
  ACCEPTED: 'Accepte',
  PENDING: 'En attente',
  EXECUTED: 'Execute',
  STARTED: 'Demarre',
  INACTIVE: 'Desactive',
  TRIGGERED: 'Declenchee',
  BLOCKED: 'Bloque',
};

/** Categorie visuelle : determine la couleur, independamment du module d'origine du statut. */
const TONE: Record<string, 'neutral' | 'positive' | 'negative' | 'progress'> = {
  AWAITING_PAYMENT: 'neutral',
  PAYMENT_SUBMITTED: 'progress',
  PAYMENT_VERIFIED: 'progress',
  PROCESSING: 'progress',
  COMPLETED: 'positive',
  CANCELLED: 'negative',
  REJECTED: 'negative',
  EXPIRED: 'negative',
  SUBMITTED: 'progress',
  CONFIRMED: 'positive',
  ACTIVE: 'neutral',
  ACCEPTED: 'progress',
  STARTED: 'progress',
  PENDING: 'neutral',
  EXECUTED: 'positive',
  INACTIVE: 'neutral',
  TRIGGERED: 'positive',
  BLOCKED: 'negative',
};

/** Pastille de statut reutilisable pour Quote/Order/Payment/Settlement. */
@Component({
  selector: 'app-status-badge',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span class="status-badge" [class]="'status-badge--' + tone()">{{ label() }}</span>`,
  styles: [
    `
      .status-badge {
        display: inline-flex;
        align-items: center;
        padding: 0.25rem 0.75rem;
        border-radius: 999px;
        font-size: 0.8125rem;
        font-weight: 600;
        line-height: 1.4;
        white-space: nowrap;
      }
      .status-badge--neutral {
        background: #eceff1;
        color: #37474f;
      }
      .status-badge--progress {
        background: #e3f2fd;
        color: #0d47a1;
      }
      .status-badge--positive {
        background: #e8f5e9;
        color: #1b5e20;
      }
      .status-badge--negative {
        background: #ffebee;
        color: #b71c1c;
      }
    `,
  ],
})
export class StatusBadgeComponent {
  readonly status = input.required<string>();

  readonly label = computed(() => LABELS[this.status() as KnownStatus] ?? this.status());
  readonly tone = computed(() => TONE[this.status() as KnownStatus] ?? 'neutral');
}
