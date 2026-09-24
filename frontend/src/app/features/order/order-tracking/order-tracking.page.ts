import { AppBarActionsDirective } from '../../../shared/directives/app-bar-actions.directive';
import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { OrderService } from '../../../core/services/order.service';
import { extractErrorMessage } from '../../../core/services/api-error.util';
import {
  OrderTracking,
  TrackingEvent,
  NEGATIVE_TRACKING_CODES,
  TRACKING_EVENT_LABELS,
} from '../../../core/models/tracking.model';
import { OrderStatus } from '../../../core/models/order.model';

const TERMINAL_STATUSES: ReadonlySet<OrderStatus> = new Set(['COMPLETED', 'CANCELLED', 'REJECTED', 'EXPIRED']);

/**
 * Suivi d'un ordre : affiche telle quelle la timeline agregee par le backend. Aucune
 * transition n'est calculee ici — {@code currentStatus} vient directement de l'ordre,
 * chaque evenement est teste par son {@code code} (le {@code label} n'est qu'un affichage).
 */
@Component({
  selector: 'app-order-tracking-page',
  standalone: true,
  imports: [
    AppBarActionsDirective,
    DatePipe,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './order-tracking.page.html',
  styleUrl: './order-tracking.page.scss',
})
export class OrderTrackingPage implements OnInit {
  /** Position (0..1) d'une station le long du fil du corridor. */
  stationX(i: number, n: number): number {
    return n <= 1 ? 0.5 : 0.06 + (0.88 * i) / (n - 1);
  }

  /** Meme courbe que le corridor d'accueil, evaluee a t (Bezier cubique). */
  stationY(t: number): number {
    const mid = 66;
    const amp = 26;
    const p0 = mid + amp * 0.15;
    const p1 = mid + amp;
    const p2 = mid - amp;
    const p3 = mid - amp * 0.15;
    const u = 1 - t;
    return u * u * u * p0 + 3 * u * u * t * p1 + 3 * u * t * t * p2 + t * t * t * p3;
  }

  private readonly route = inject(ActivatedRoute);
  private readonly orderService = inject(OrderService);

  readonly tracking = signal<OrderTracking | null>(null);
  readonly loading = signal(true);
  readonly refreshing = signal(false);
  readonly errorMessage = signal<string | null>(null);
  /** Horodatage cote client de la derniere reponse recue avec succes (jamais une donnee backend). */
  readonly lastUpdatedAt = signal<Date | null>(null);

  private orderId = '';

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) {
      this.errorMessage.set('Ordre introuvable.');
      this.loading.set(false);
      return;
    }
    this.orderId = id;
    this.load(true);
  }

  refresh(): void {
    if (!this.refreshing()) {
      this.load(false);
    }
  }

  hasNegative(): boolean {
    return (this.tracking()?.timeline ?? []).some((e) => this.isNegative(e));
  }

  isNegative(event: TrackingEvent): boolean {
    return NEGATIVE_TRACKING_CODES.has(event.code);
  }

  /**
   * Un remboursement est une operation distincte du transfert (section 12) : jamais un echec.
   * Style neutre/informatif propre, jamais la couleur "negative" reservee aux vrais rejets/
   * annulations/expirations.
   */
  isRefund(event: TrackingEvent): boolean {
    return event.code === 'REFUND_PENDING' || event.code === 'REFUND_PROCESSED';
  }

  /** Toujours base sur `code` (section 10) — jamais le `label` renvoye par le backend. */
  labelFor(event: TrackingEvent): string {
    return TRACKING_EVENT_LABELS[event.code] ?? event.label;
  }

  /**
   * Dernier evenement recu tant que l'ordre n'a pas atteint un statut terminal : represente
   * "on en est la", distinct visuellement des etapes deja acquises. Derive uniquement de
   * `currentStatus`/de la position dans la liste deja renvoyee par le backend -- aucune etape
   * future n'est inventee.
   */
  isCurrent(event: TrackingEvent, index: number): boolean {
    const tracking = this.tracking();
    if (!tracking || this.isNegative(event) || index !== tracking.timeline.length - 1) {
      return false;
    }
    return !TERMINAL_STATUSES.has(tracking.currentStatus);
  }

  private load(initial: boolean): void {
    if (initial) {
      this.loading.set(true);
    } else {
      this.refreshing.set(true);
    }
    this.orderService.getTracking(this.orderId).subscribe({
      next: (response) => {
        this.tracking.set(response.data);
        this.lastUpdatedAt.set(new Date());
        this.loading.set(false);
        this.refreshing.set(false);
      },
      error: (error) => {
        this.errorMessage.set(extractErrorMessage(error));
        this.loading.set(false);
        this.refreshing.set(false);
      },
    });
  }
}
