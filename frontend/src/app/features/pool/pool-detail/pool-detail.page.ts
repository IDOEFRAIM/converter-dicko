import { DatePipe, UpperCasePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { forkJoin } from 'rxjs';
import { PoolService } from '../../../core/services/pool.service';
import { NotificationService } from '../../../core/services/notification.service';
import { extractErrorMessage } from '../../../core/services/api-error.util';
import { Pool, PoolParticipant, poolIsActive, poolProgress } from '../../../core/models/pool.model';
import { StatusBadgeComponent } from '../../../shared/components/status-badge/status-badge.component';
import { openConfirmDialog } from '../../../shared/components/confirm-dialog/confirm-dialog.component';
import { MoneyPipe } from '../../../shared/pipes/money.pipe';

const POLL_INTERVAL_MS = 4000;

/**
 * Detail d'une Ruee collective (mission "differenciation marketing", Lot 3) : thermometre,
 * participants, invitation par code, acces au transfert qui y contribue. Le "temps reel" est
 * simule par sondage periodique tant que la Ruee est ACTIVE et que l'ecran est visible — aucune
 * infrastructure WebSocket/SSE cote backend (meme principe que le mobile).
 */
@Component({
  selector: 'app-pool-detail-page',
  standalone: true,
  imports: [
    DatePipe,
    UpperCasePipe,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    StatusBadgeComponent,
    MoneyPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './pool-detail.page.html',
  styleUrl: './pool-detail.page.scss',
})
export class PoolDetailPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly poolService = inject(PoolService);
  private readonly notification = inject(NotificationService);
  private readonly dialog = inject(MatDialog);
  private readonly destroyRef = inject(DestroyRef);

  private poolId = '';
  private pollTimeoutId: ReturnType<typeof setTimeout> | null = null;

  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly pool = signal<Pool | null>(null);
  readonly participants = signal<PoolParticipant[]>([]);

  readonly joining = signal(false);
  readonly joinErrorMessage = signal<string | null>(null);
  readonly cancelling = signal(false);

  readonly progress = computed(() => {
    const pool = this.pool();
    return pool ? poolProgress(pool) : 0;
  });
  readonly isActive = computed(() => {
    const pool = this.pool();
    return pool !== null && poolIsActive(pool);
  });

  readonly rewardLines = computed(() => {
    const pool = this.pool();
    if (!pool) return [];
    return [
      { label: 'Part de base', points: pool.rewardBasePercentage },
      {
        label: `${pool.participantCount} participants`,
        points: pool.rewardParticipantBonusPercentage,
        sub: `+${fmtPct(pool.rewardPerParticipantPercentage)} pt par personne`,
      },
      {
        label: 'Volume du groupe',
        points: pool.rewardVolumeBonusPercentage,
        sub: `+${fmtPct(pool.rewardPerMillionXofPercentage)} pt par million`,
      },
    ];
  });

  readonly fmtPct = fmtPct;

  constructor() {
    this.destroyRef.onDestroy(() => {
      if (this.pollTimeoutId !== null) clearTimeout(this.pollTimeoutId);
    });
  }

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) {
      this.errorMessage.set('Ruee introuvable.');
      this.loading.set(false);
      return;
    }
    this.poolId = id;
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.refresh(() => this.loading.set(false));
  }

  private refresh(onDone?: () => void): void {
    forkJoin({
      pool: this.poolService.get(this.poolId),
      participants: this.poolService.participants(this.poolId),
    }).subscribe({
      next: ({ pool, participants }) => {
        this.pool.set(pool.data);
        this.participants.set(participants.data);
        this.errorMessage.set(null);
        onDone?.();
        this.schedulePolling();
      },
      error: (error) => {
        this.errorMessage.set(extractErrorMessage(error));
        onDone?.();
        this.schedulePolling();
      },
    });
  }

  private schedulePolling(): void {
    if (this.pollTimeoutId !== null) {
      clearTimeout(this.pollTimeoutId);
      this.pollTimeoutId = null;
    }
    if (!this.isActive()) return;
    this.pollTimeoutId = setTimeout(() => this.refresh(), POLL_INTERVAL_MS);
  }

  copyCode(): void {
    const pool = this.pool();
    if (!pool) return;
    navigator.clipboard
      .writeText(pool.code)
      .then(() => this.notification.success('Code copie.'))
      .catch(() => this.notification.error('Impossible de copier le code.'));
  }

  join(): void {
    if (this.joining()) return;
    this.joining.set(true);
    this.joinErrorMessage.set(null);
    this.poolService.join(this.poolId).subscribe({
      next: (response) => {
        this.joining.set(false);
        this.pool.set(response.data);
        this.schedulePolling();
      },
      error: (error) => {
        this.joining.set(false);
        this.joinErrorMessage.set(extractErrorMessage(error));
      },
    });
  }

  payNow(): void {
    this.router.navigate(['/quote/new'], { queryParams: { poolId: this.poolId } });
  }

  cancel(): void {
    if (this.cancelling()) return;
    openConfirmDialog(this.dialog, {
      title: 'Annuler la Ruee',
      message: 'Action definitive. Les participants seront prevenus.',
      confirmLabel: 'Annuler la Ruee',
      cancelLabel: 'Garder',
    }).subscribe((confirmed) => {
      if (!confirmed) return;
      this.cancelling.set(true);
      this.poolService.cancel(this.poolId).subscribe({
        next: (response) => {
          this.cancelling.set(false);
          this.pool.set(response.data);
        },
        error: (error) => {
          this.cancelling.set(false);
          this.notification.error(extractErrorMessage(error));
        },
      });
    });
  }
}

/** Nettoie une chaine de pourcentage renvoyee par le backend ("0.500" -> "0.5") — purement cosmetique. */
function fmtPct(raw: string): string {
  if (!raw.includes('.')) return raw;
  return raw.replace(/\.?0+$/, '');
}
