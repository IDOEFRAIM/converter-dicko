import { DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Pool, PoolParticipant, formatPoints, poolProgress } from '../../../core/models/pool.model';
import { extractErrorMessage } from '../../../core/services/api-error.util';
import { PoolService } from '../../../core/services/pool.service';
import { StatusBadgeComponent } from '../../../shared/components/status-badge/status-badge.component';
import { MoneyPipe } from '../../../shared/pipes/money.pipe';

const DIAL_RADIUS = 90;

/**
 * "Ruee collective" — copie de `PoolDetailPage` (mobile) : jauge de collecte sur laque,
 * decomposition du rabais, participants, puis Rejoindre / Payer maintenant / Annuler.
 */
@Component({
  selector: 'app-pool-detail-page',
  standalone: true,
  imports: [
    DatePipe,
    RouterLink,
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
  private readonly poolService = inject(PoolService);
  private readonly snackBar = inject(MatSnackBar);

  readonly pool = signal<Pool | null>(null);
  readonly participants = signal<PoolParticipant[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly joining = signal(false);
  readonly joinError = signal<string | null>(null);
  readonly cancelling = signal(false);

  readonly fmt = formatPoints;
  readonly dialLength = Math.PI * DIAL_RADIUS;
  readonly dialOffset = computed(() => {
    const p = this.pool();
    if (!p) {
      return this.dialLength;
    }
    const progress = p.status === 'SUCCEEDED' ? 1 : poolProgress(p);
    return this.dialLength * (1 - progress);
  });
  readonly closedAt = computed(() => {
    const p = this.pool();
    return p ? (p.succeededAt ?? p.expiredAt ?? p.cancelledAt ?? p.expiresAt) : null;
  });

  private get id(): string {
    return this.route.snapshot.paramMap.get('id') ?? '';
  }

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.poolService.get(this.id).subscribe({
      next: (r) => {
        this.pool.set(r.data);
        this.error.set(null);
        this.loading.set(false);
      },
      error: (e) => {
        this.error.set(extractErrorMessage(e));
        this.loading.set(false);
      },
    });
    this.poolService.participants(this.id).subscribe({
      next: (r) => this.participants.set(r.data),
      error: () => undefined,
    });
  }

  points(raw: string | number): string {
    const v = formatPoints(raw);
    return v === '0' ? '—' : `-${v} pt${v === '1' ? '' : 's'}`;
  }

  hasContributed(p: PoolParticipant): boolean {
    return Number(p.contributedAmountXof) > 0;
  }

  initial(name: string): string {
    return name.trim() ? name.trim()[0].toUpperCase() : '?';
  }

  async share(pool: Pool): Promise<void> {
    const text = `Rejoins ma Ruee sur YUAN PAY ! Code : ${pool.code}`;
    const url = `${location.origin}/pay/pools/${pool.id}`;
    try {
      if (navigator.share) {
        await navigator.share({ title: 'Rejoins ma Ruee !', text, url });
        return;
      }
      await navigator.clipboard.writeText(`${text} — ${url}`);
      this.snackBar.open('Invitation copiee.', undefined, { duration: 2500 });
    } catch {
      // Partage annule ou indisponible : le code reste visible a l'ecran.
    }
  }

  join(): void {
    this.joining.set(true);
    this.joinError.set(null);
    this.poolService.join(this.id).subscribe({
      next: (r) => {
        this.pool.set(r.data);
        this.joining.set(false);
        this.load();
      },
      error: (e) => {
        this.joinError.set(extractErrorMessage(e));
        this.joining.set(false);
      },
    });
  }

  cancel(): void {
    if (!confirm('Annuler la Ruee ? Action definitive. Les participants seront prevenus.')) {
      return;
    }
    this.cancelling.set(true);
    this.poolService.cancel(this.id).subscribe({
      next: (r) => {
        this.pool.set(r.data);
        this.cancelling.set(false);
      },
      error: (e) => {
        this.snackBar.open(extractErrorMessage(e), undefined, { duration: 4000 });
        this.cancelling.set(false);
      },
    });
  }
}
