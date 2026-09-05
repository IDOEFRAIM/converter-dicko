import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RateHistoryService } from '../../../core/services/rate-history.service';
import { NotificationService } from '../../../core/services/notification.service';
import { extractErrorMessage } from '../../../core/services/api-error.util';
import { RateAlert } from '../../../core/models/rate-history.model';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { StatusBadgeComponent } from '../../../shared/components/status-badge/status-badge.component';
import { openConfirmDialog } from '../../../shared/components/confirm-dialog/confirm-dialog.component';

const ACTIVE: RateAlert['status'] = 'ACTIVE';

/**
 * Alertes de taux : l'utilisateur fixe une cible, le backend (scheduler) declenche et
 * notifie. L'ecran affiche l'etat serveur tel quel — aucun calcul de declenchement cote client.
 * Le seul calcul fait ici (l'ecart affiche sur une alerte active) compare deux valeurs deja
 * publiques (`targetRate` de l'alerte, `customerRate` public le plus recent) — jamais une
 * prediction ou un declenchement anticipe : la decision reste entierement au scheduler backend.
 */
@Component({
  selector: 'app-rate-alerts-page',
  standalone: true,
  imports: [
    RouterLink,
    DatePipe,
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    EmptyStateComponent,
    PageHeaderComponent,
    StatusBadgeComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './rate-alerts.page.html',
  styleUrl: './rate-alerts.page.scss',
})
export class RateAlertsPage implements OnInit {
  private readonly rateHistoryService = inject(RateHistoryService);
  private readonly notification = inject(NotificationService);
  private readonly dialog = inject(MatDialog);

  readonly alerts = signal<RateAlert[]>([]);
  readonly currentRate = signal<string | null>(null);
  readonly loading = signal(true);
  readonly creating = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly activeAlerts = computed(() => this.alerts().filter((a) => a.status === ACTIVE));
  readonly closedAlerts = computed(() => this.alerts().filter((a) => a.status !== ACTIVE));

  readonly form = new FormGroup({
    targetRate: new FormControl<number | null>(null, {
      validators: [Validators.required, Validators.min(0.000001)],
    }),
    expiresAt: new FormControl<string>(''),
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.rateHistoryService.listAlerts(0, 50).subscribe({
      next: (response) => {
        this.alerts.set(response.data.content);
        this.loading.set(false);
      },
      error: (error) => {
        this.errorMessage.set(extractErrorMessage(error));
        this.loading.set(false);
      },
    });
    // Purement informatif (ecart affiche sur les alertes actives) : un echec ici ne bloque
    // jamais l'affichage des alertes elles-memes.
    this.rateHistoryService.history({ size: 1 }).subscribe({
      next: (response) => this.currentRate.set(response.data.content[0]?.customerRate ?? null),
      error: () => this.currentRate.set(null),
    });
  }

  /** Ecart entre le taux client public actuel et la cible de l'alerte, en pourcentage. */
  gapFor(alert: RateAlert): { percent: number } | null {
    const current = this.currentRate();
    if (!current) {
      return null;
    }
    const currentValue = Number(current);
    const target = Number(alert.targetRate);
    if (!currentValue) {
      return null;
    }
    return { percent: ((target - currentValue) / currentValue) * 100 };
  }

  create(): void {
    if (this.form.invalid || this.creating()) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.creating.set(true);
    this.rateHistoryService
      .createAlert({
        targetRate: String(v.targetRate),
        // Fin de journee LOCALE de l'utilisateur convertie en instant UTC (pas de "Z" force
        // sur une date sans fuseau) ; null = aucune expiration.
        expiresAt: v.expiresAt ? new Date(`${v.expiresAt}T23:59:59`).toISOString() : null,
      })
      .subscribe({
        next: () => {
          this.creating.set(false);
          this.form.reset({ targetRate: null, expiresAt: '' });
          this.notification.success('Alerte de taux creee.');
          this.load();
        },
        error: (error) => {
          this.creating.set(false);
          this.notification.error(extractErrorMessage(error));
        },
      });
  }

  cancel(alert: RateAlert): void {
    openConfirmDialog(this.dialog, {
      title: "Annuler l'alerte",
      message: `Vous ne serez plus notifie lorsque le taux atteindra ${alert.targetRate} XOF.`,
      confirmLabel: "Annuler l'alerte",
      cancelLabel: 'Garder',
    }).subscribe((confirmed) => {
      if (confirmed !== true) {
        return;
      }
      this.rateHistoryService.cancelAlert(alert.id).subscribe({
        next: (response) => {
          this.alerts.update((list) => list.map((a) => (a.id === alert.id ? response.data : a)));
          this.notification.success('Alerte annulee.');
        },
        error: (error) => this.notification.error(extractErrorMessage(error)),
      });
    });
  }
}
