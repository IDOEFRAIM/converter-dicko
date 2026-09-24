import { DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AchievementSummary, tierProgress } from '../../core/models/achievement.model';
import { OrderHistoryEntry } from '../../core/models/order.model';
import { extractErrorMessage } from '../../core/services/api-error.util';
import { AchievementService } from '../../core/services/achievement.service';
import { AuthService } from '../../core/services/auth.service';
import { OrderService } from '../../core/services/order.service';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge.component';
import { MoneyPipe } from '../../shared/pipes/money.pipe';

const OBJECTIVE_KEY = 'yuanpay.pro.monthlyObjectiveXof';
const RING_RADIUS = 86;
const DIAL_RADIUS = 90;

/**
 * "Mes gains" — copie de `MyGainsPage` (mobile) : console sobre pour PRO (cadran de
 * l'objectif mensuel, serie de mois actifs, volume), sceau + progression de palier pour
 * les profils etudiants, puis l'historique des transferts termines.
 * L'objectif mensuel reste LOCAL a l'appareil, comme sur mobile (`ProObjectiveStore`).
 */
@Component({
  selector: 'app-my-gains-page',
  standalone: true,
  imports: [
    RouterLink,
    DatePipe,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    StatusBadgeComponent,
    MoneyPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './my-gains.page.html',
  styleUrl: './my-gains.page.scss',
})
export class MyGainsPage implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly achievementService = inject(AchievementService);
  private readonly orderService = inject(OrderService);

  readonly profile = this.auth.experienceProfile;
  readonly summary = signal<AchievementSummary | null>(null);
  readonly summaryError = signal<string | null>(null);
  readonly loadingSummary = signal(true);
  readonly history = signal<OrderHistoryEntry[]>([]);
  readonly loadingHistory = signal(true);
  readonly historyError = signal<string | null>(null);
  readonly objective = signal<number | null>(this.readObjective());

  readonly ringCircumference = 2 * Math.PI * RING_RADIUS;
  readonly ringOffset = computed(() => {
    const s = this.summary();
    const progress = s ? (tierProgress(s) ?? 0) : 0;
    return this.ringCircumference * (1 - progress);
  });

  /** Cadran demi-cercle : longueur de l'arc et remplissage (volume du mois / objectif). */
  readonly dialLength = Math.PI * DIAL_RADIUS;
  readonly dialRatio = computed(() => {
    const objective = this.objective();
    const s = this.summary();
    if (!objective || !s) {
      return null;
    }
    return Math.min(
      1,
      Math.max(0, Math.trunc(Number(s.currentMonthAmountXofCompleted)) / objective),
    );
  });
  readonly dialOffset = computed(() => this.dialLength * (1 - (this.dialRatio() ?? 0)));
  readonly dialPercent = computed(() => Math.round((this.dialRatio() ?? 0) * 100));

  /** Serie de mois calendaires consecutifs avec un transfert termine (meme regle que mobile). */
  readonly streak = computed<{ label: string; caption: string }>(() => {
    if (this.loadingHistory()) {
      return { label: '…', caption: 'calcul en cours' };
    }
    const entries = this.history();
    if (entries.length === 0) {
      return { label: '0', caption: 'relancez une serie' };
    }
    const active = new Set(
      entries.map((e) => {
        const d = new Date(e.createdAt);
        return d.getUTCFullYear() * 12 + d.getUTCMonth();
      }),
    );
    const now = new Date();
    let anchor = now.getUTCFullYear() * 12 + now.getUTCMonth();
    let securedThisMonth = true;
    if (!active.has(anchor)) {
      securedThisMonth = false;
      if (active.has(anchor - 1)) {
        anchor -= 1;
      } else {
        return { label: '0', caption: 'aucun transfert ce mois-ci' };
      }
    }
    let count = 0;
    for (let k = anchor; active.has(k); k--) {
      count++;
    }
    return {
      label: String(count),
      caption: securedThisMonth ? "mois d'affilee" : 'mois · a confirmer',
    };
  });

  ngOnInit(): void {
    this.loadSummary();
    this.loadHistory();
  }

  loadSummary(): void {
    this.loadingSummary.set(true);
    this.achievementService.summary().subscribe({
      next: (r) => {
        this.summary.set(r.data);
        this.summaryError.set(null);
        this.loadingSummary.set(false);
      },
      error: (error) => {
        this.summaryError.set(extractErrorMessage(error));
        this.loadingSummary.set(false);
      },
    });
  }

  loadHistory(): void {
    this.loadingHistory.set(true);
    this.orderService.history({ status: 'COMPLETED', size: 20 }).subscribe({
      next: (r) => {
        this.history.set(r.data.content);
        this.historyError.set(null);
        this.loadingHistory.set(false);
      },
      error: (error) => {
        this.historyError.set(extractErrorMessage(error));
        this.loadingHistory.set(false);
      },
    });
  }

  editObjective(): void {
    const current = this.objective();
    const raw = window.prompt(
      "Objectif mensuel (XOF) — repere sur votre cadran, n'affecte aucun tarif. Laissez vide pour le retirer.",
      current ? String(current) : '',
    );
    if (raw === null) {
      return;
    }
    const value = Math.trunc(Number(raw.replace(/\s/g, '')));
    const next = raw.trim() === '' || !Number.isFinite(value) || value <= 0 ? null : value;
    this.objective.set(next);
    try {
      if (next === null) {
        localStorage.removeItem(OBJECTIVE_KEY);
      } else {
        localStorage.setItem(OBJECTIVE_KEY, String(next));
      }
    } catch {
      // Stockage indisponible : l'objectif vaut pour cette session.
    }
  }

  initial(label: string | null): string {
    return label ? label[0].toUpperCase() : '?';
  }

  private readObjective(): number | null {
    try {
      const raw = Number(localStorage.getItem(OBJECTIVE_KEY));
      return Number.isFinite(raw) && raw > 0 ? raw : null;
    } catch {
      return null;
    }
  }
}
