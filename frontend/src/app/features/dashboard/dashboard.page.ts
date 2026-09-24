import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from '../../core/services/auth.service';
import { OrderService } from '../../core/services/order.service';
import { RateHistoryService } from '../../core/services/rate-history.service';
import { SupplierService } from '../../core/services/supplier.service';
import { OrderHistoryEntry } from '../../core/models/order.model';
import { PublicRateHistoryEntry } from '../../core/models/rate-history.model';
import { SupplierSummary } from '../../core/models/supplier.model';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge.component';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state.component';
import { CorridorComponent } from '../../shared/components/corridor/corridor.component';
import { MoneyPipe } from '../../shared/pipes/money.pipe';

const SPARKLINE_WIDTH = 200;
const SPARKLINE_HEIGHT = 40;

/**
 * Vue d'activite reelle, pas une grille de cartes marketing : le corridor + taux du moment,
 * la derniere operation, les fournisseurs favoris, l'evolution recente du taux. Chaque section
 * n'affiche que des donnees reellement renvoyees par le backend — aucune ville/statistique
 * inventee quand elle n'existe pas (ex. aucune ville "de l'utilisateur", le corridor reste
 * generique Burkina Faso -> Chine).
 */
@Component({
  selector: 'app-dashboard-page',
  standalone: true,
  imports: [
    RouterLink,
    DatePipe,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    StatusBadgeComponent,
    EmptyStateComponent,
    CorridorComponent,
    MoneyPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './dashboard.page.html',
  styleUrl: './dashboard.page.scss',
})
export class DashboardPage implements OnInit {
  private readonly authService = inject(AuthService);
  private readonly orderService = inject(OrderService);
  private readonly rateHistoryService = inject(RateHistoryService);
  private readonly supplierService = inject(SupplierService);

  readonly currentUser = this.authService.currentUser;

  readonly latestRate = signal<PublicRateHistoryEntry | null>(null);
  readonly rateSeries = signal<PublicRateHistoryEntry[]>([]);
  readonly lastOrder = signal<OrderHistoryEntry | null>(null);
  readonly lastOrderSupplierName = signal<string | null>(null);
  readonly favoriteSuppliers = signal<SupplierSummary[]>([]);

  readonly loading = signal(true);
  readonly suppliersLoading = signal(true);

  readonly sparklineWidth = SPARKLINE_WIDTH;
  readonly sparklineHeight = SPARKLINE_HEIGHT;

  /** Plus ancien -> plus recent pour tracer la courbe de gauche a droite. */
  private readonly chronologicalRates = computed(() => [...this.rateSeries()].reverse());

  readonly sparklinePoints = computed(() => {
    const series = this.chronologicalRates();
    if (series.length < 2) {
      return '';
    }
    const values = series.map((e) => Number(e.customerRate));
    const min = Math.min(...values);
    const max = Math.max(...values);
    const span = max - min || 1;
    const stepX = SPARKLINE_WIDTH / (series.length - 1);
    return values
      .map((v, i) => {
        const x = i * stepX;
        const y = SPARKLINE_HEIGHT - ((v - min) / span) * (SPARKLINE_HEIGHT - 6) - 3;
        return `${x.toFixed(1)},${y.toFixed(1)}`;
      })
      .join(' ');
  });

  ngOnInit(): void {
    this.rateHistoryService.history({ size: 1 }).subscribe({
      next: (response) => {
        this.latestRate.set(response.data.content[0] ?? null);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });

    this.rateHistoryService.history({ size: 8 }).subscribe({
      next: (response) => this.rateSeries.set(response.data.content),
      error: () => undefined,
    });

    this.orderService.history({ size: 1 }).subscribe({
      next: (response) => {
        const order = response.data.content[0] ?? null;
        this.lastOrder.set(order);
        if (order?.supplierId) {
          this.supplierService.get(order.supplierId).subscribe({
            next: (supplierResponse) => this.lastOrderSupplierName.set(supplierResponse.data.displayName),
            error: () => undefined,
          });
        }
      },
      error: () => undefined,
    });

    this.supplierService.listFavorites(0, 6).subscribe({
      next: (response) => {
        this.favoriteSuppliers.set(response.data.content);
        this.suppliersLoading.set(false);
      },
      error: () => this.suppliersLoading.set(false),
    });
  }
}
