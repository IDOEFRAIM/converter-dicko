import { AppBarActionsDirective } from '../../../shared/directives/app-bar-actions.directive';
import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RateHistoryService } from '../../../core/services/rate-history.service';
import { extractErrorMessage } from '../../../core/services/api-error.util';
import { PublicRateHistoryEntry } from '../../../core/models/rate-history.model';
import { CorridorComponent } from '../../../shared/components/corridor/corridor.component';

const CHART_WIDTH = 320;
const CHART_HEIGHT = 90;

/**
 * Historique du taux client. Affiche uniquement `customerRate` / `recordedAt` — jamais le
 * taux de revient, la marge ou les frais internes (le backend ne les expose pas ici). La
 * courbe est un SVG genere en ligne : aucune dependance graphique ajoutee.
 */
@Component({
  selector: 'app-rate-history-page',
  standalone: true,
  imports: [
    RouterLink,
    DatePipe,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    AppBarActionsDirective,
    CorridorComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './rate-history.page.html',
  styleUrl: './rate-history.page.scss',
})
export class RateHistoryPage implements OnInit {
  private readonly rateHistoryService = inject(RateHistoryService);

  /** Le backend renvoie du plus recent au plus ancien : on garde cet ordre pour la liste. */
  readonly entries = signal<PublicRateHistoryEntry[]>([]);
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);

  readonly chartWidth = CHART_WIDTH;
  readonly chartHeight = CHART_HEIGHT;

  readonly latest = computed(() => this.entries()[0] ?? null);

  /** Serie chronologique (ancien -> recent) pour la courbe. */
  private readonly chronological = computed(() => [...this.entries()].reverse());

  readonly chartPoints = computed(() => {
    const series = this.chronological();
    if (series.length < 2) {
      return '';
    }
    const values = series.map((e) => Number(e.customerRate));
    const min = Math.min(...values);
    const max = Math.max(...values);
    const span = max - min || 1;
    const stepX = CHART_WIDTH / (series.length - 1);
    return values
      .map((v, i) => {
        const x = i * stepX;
        const y = CHART_HEIGHT - ((v - min) / span) * (CHART_HEIGHT - 8) - 4;
        return `${x.toFixed(1)},${y.toFixed(1)}`;
      })
      .join(' ');
  });

  readonly rangeLabel = computed(() => {
    const series = this.chronological();
    if (series.length === 0) {
      return null;
    }
    const values = series.map((e) => Number(e.customerRate));
    return { min: Math.min(...values), max: Math.max(...values) };
  });

  /**
   * Variation par rapport au releve precedent (pas "aujourd'hui" : le backend n'horodate pas
   * de notion de journee, seulement des publications ponctuelles — afficher une variation
   * "journaliere" serait invente). Null tant qu'il n'y a pas deux points a comparer.
   */
  readonly variation = computed(() => {
    const series = this.chronological();
    if (series.length < 2) {
      return null;
    }
    const previous = Number(series[series.length - 2].customerRate);
    const current = Number(series[series.length - 1].customerRate);
    if (!previous) {
      return null;
    }
    return {
      percent: ((current - previous) / previous) * 100,
      previousRecordedAt: series[series.length - 2].recordedAt,
    };
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.rateHistoryService.history({ size: 60 }).subscribe({
      next: (response) => {
        this.entries.set(response.data.content);
        this.loading.set(false);
      },
      error: (error) => {
        this.errorMessage.set(extractErrorMessage(error));
        this.loading.set(false);
      },
    });
  }
}
