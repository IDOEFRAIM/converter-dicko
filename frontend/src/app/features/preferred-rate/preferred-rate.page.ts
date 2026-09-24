import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { PreferredRateService } from '../../core/services/preferred-rate.service';
import { InboxNotificationService } from '../../core/services/inbox-notification.service';
import { extractErrorMessage } from '../../core/services/api-error.util';
import { NotificationService } from '../../core/services/notification.service';
import { InboxNotification } from '../../core/models/inbox-notification.model';
import { PreferredRateRequest } from '../../core/models/preferred-rate.model';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge.component';
import { openConfirmDialog } from '../../shared/components/confirm-dialog/confirm-dialog.component';
import { MoneyPipe } from '../../shared/pipes/money.pipe';

const NOTIFICATION_TYPES_FOR_THIS_REQUEST = new Set([
  'PREFERRED_RATE_REACHED',
  'EXCHANGE_STARTED',
  'EXCHANGE_PROGRESS',
  'EXCHANGE_COMPLETED',
  'PREFERRED_RATE_EXPIRED',
]);

/**
 * Deux vues distinctes, jamais melangees, pilotees par {@code phase}
 * (calculee par le backend) : EN ATTENTE DU TAUX (compte a rebours
 * J+3) ou ECHANGE EN COURS (compte a rebours 2h + progression). Aucun
 * calcul financier ni de "taux atteint" cote frontend.
 */
@Component({
  selector: 'app-preferred-rate-page',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    DatePipe,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    StatusBadgeComponent,
    MoneyPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './preferred-rate.page.html',
  styleUrl: './preferred-rate.page.scss',
})
export class PreferredRatePage implements OnInit {
  private readonly preferredRateService = inject(PreferredRateService);
  private readonly inboxNotificationService = inject(InboxNotificationService);
  private readonly notification = inject(NotificationService);
  private readonly dialog = inject(MatDialog);
  private readonly destroyRef = inject(DestroyRef);

  readonly loading = signal(true);
  readonly submitting = signal(false);
  readonly cancelling = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly current = signal<PreferredRateRequest | null>(null);
  readonly history = signal<PreferredRateRequest[]>([]);
  readonly lastNotification = signal<InboxNotification | null>(null);

  private readonly nowMs = signal(Date.now());

  readonly isWaiting = computed(() => this.current()?.phase === 'WAITING');
  readonly isExchange = computed(() => {
    const phase = this.current()?.phase;
    return phase === 'EXCHANGE_IN_PROGRESS' || phase === 'EXCHANGE_COMPLETED';
  });

  readonly waitingRemainingLabel = computed(() => this.remainingLabel(this.current()?.expiresAt ?? null));
  readonly exchangeRemainingLabel = computed(() => this.remainingLabel(this.current()?.exchange?.deadlineAt ?? null));

  readonly form = new FormGroup({
    amountXof: new FormControl<number | null>(null, { validators: [Validators.required, Validators.min(1)] }),
    targetRate: new FormControl<number | null>(null, { validators: [Validators.required, Validators.min(0.000001)] }),
  });

  constructor() {
    const intervalId = setInterval(() => this.nowMs.set(Date.now()), 15_000);
    this.destroyRef.onDestroy(() => clearInterval(intervalId));
  }

  ngOnInit(): void {
    this.load();
  }

  private remainingLabel(targetIso: string | null): string {
    if (!targetIso) {
      return '';
    }
    const remainingMs = new Date(targetIso).getTime() - this.nowMs();
    if (remainingMs <= 0) {
      return '0 min';
    }
    const totalMinutes = Math.floor(remainingMs / 60000);
    const days = Math.floor(totalMinutes / (24 * 60));
    const hours = Math.floor((totalMinutes % (24 * 60)) / 60);
    const minutes = totalMinutes % 60;
    return days > 0 ? `${days} j ${hours} h` : `${hours} h ${minutes} min`;
  }

  private load(): void {
    this.loading.set(true);
    this.preferredRateService.list(0, 20).subscribe({
      next: (response) => {
        const all = response.data.content;
        // "Courant" a l'ecran = WAITING (en attente du taux) ou
        // EXCHANGE_IN_PROGRESS (echange declenche, pas encore termine) :
        // exactement une des deux vues, jamais les deux compteurs (J+3
        // et 2h) affiches ensemble.
        const prominent = all.find((r) => r.phase === 'WAITING' || r.phase === 'EXCHANGE_IN_PROGRESS');
        this.current.set(prominent ?? null);
        this.history.set(all.filter((r) => r.id !== prominent?.id));
        this.loading.set(false);
        if (prominent && prominent.phase === 'EXCHANGE_IN_PROGRESS') {
          this.loadLastNotification();
        }
      },
      error: (error) => {
        this.errorMessage.set(extractErrorMessage(error));
        this.loading.set(false);
      },
    });
  }

  private loadLastNotification(): void {
    this.inboxNotificationService.list(0, 10).subscribe({
      next: (response) => {
        const relevant = response.data.content.find((n) => NOTIFICATION_TYPES_FOR_THIS_REQUEST.has(n.type));
        this.lastNotification.set(relevant ?? null);
      },
    });
  }

  submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.errorMessage.set(null);
    const value = this.form.getRawValue();

    this.preferredRateService
      .create({
        direction: 'XOF_TO_CNY',
        amountXof: String(value.amountXof),
        targetRate: String(value.targetRate),
      })
      .subscribe({
        next: (response) => {
          this.submitting.set(false);
          this.current.set(response.data);
          this.form.reset();
          this.notification.success('Demande de taux preferentiel creee.');
        },
        error: (error) => {
          this.submitting.set(false);
          this.errorMessage.set(extractErrorMessage(error));
        },
      });
  }

  cancel(): void {
    const request = this.current();
    if (!request || this.cancelling()) {
      return;
    }
    openConfirmDialog(this.dialog, {
      title: 'Annuler la demande',
      message: 'Le montant reserve sera immediatement restitue a votre solde disponible.',
      confirmLabel: 'Annuler la demande',
    }).subscribe((confirmed) => {
      if (!confirmed) {
        return;
      }
      this.cancelling.set(true);
      this.preferredRateService.cancel(request.id).subscribe({
        next: () => {
          this.cancelling.set(false);
          this.notification.success('Demande annulee, montant restitue.');
          this.load();
        },
        error: (error) => {
          this.cancelling.set(false);
          this.notification.error(extractErrorMessage(error));
        },
      });
    });
  }
}
