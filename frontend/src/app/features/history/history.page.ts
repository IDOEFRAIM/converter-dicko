import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { OrderService } from '../../core/services/order.service';
import { OrderStatus, OrderSummary } from '../../core/models/order.model';
import { extractErrorMessage } from '../../core/services/api-error.util';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge.component';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { MoneyPipe } from '../../shared/pipes/money.pipe';

type HistoryFilter = 'ALL' | 'IN_PROGRESS' | 'COMPLETED' | 'CLOSED';

const IN_PROGRESS_STATUSES: OrderStatus[] = [
  'AWAITING_PAYMENT',
  'PAYMENT_SUBMITTED',
  'PAYMENT_VERIFIED',
  'PROCESSING',
];
const CLOSED_STATUSES: OrderStatus[] = ['CANCELLED', 'REJECTED', 'EXPIRED'];

/**
 * Historique client. Ne liste que les Orders : le backend n'expose
 * pas d'endpoint de liste pour les Quotes (uniquement creation/lecture
 * unitaire/acceptation/annulation) — chaque Order conservant deja le
 * taux et les montants figes de son devis d'origine, cette liste reste
 * le registre complet et fiable des transferts du client.
 */
@Component({
  selector: 'app-history-page',
  standalone: true,
  imports: [
    RouterLink,
    DatePipe,
    MatButtonModule,
    MatButtonToggleModule,
    MatIconModule,
    MatProgressSpinnerModule,
    StatusBadgeComponent,
    EmptyStateComponent,
    PageHeaderComponent,
    MoneyPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './history.page.html',
  styleUrl: './history.page.scss',
})
export class HistoryPage implements OnInit {
  private readonly orderService = inject(OrderService);

  readonly orders = signal<OrderSummary[]>([]);
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);
  readonly filter = signal<HistoryFilter>('ALL');

  readonly filteredOrders = computed(() => {
    const current = this.filter();
    if (current === 'ALL') {
      return this.orders();
    }
    return this.orders().filter((order) => {
      if (current === 'IN_PROGRESS') {
        return IN_PROGRESS_STATUSES.includes(order.status);
      }
      if (current === 'COMPLETED') {
        return order.status === 'COMPLETED';
      }
      return CLOSED_STATUSES.includes(order.status);
    });
  });

  private readonly pageSize = 15;

  ngOnInit(): void {
    this.load(0);
  }

  setFilter(filter: HistoryFilter): void {
    this.filter.set(filter);
  }

  private load(page: number): void {
    this.loading.set(true);
    this.orderService.list(page, this.pageSize).subscribe({
      next: (response) => {
        this.orders.set(response.data.content);
        this.page.set(response.data.page);
        this.totalPages.set(response.data.totalPages);
        this.totalElements.set(response.data.totalElements);
        this.loading.set(false);
      },
      error: (error) => {
        this.errorMessage.set(extractErrorMessage(error));
        this.loading.set(false);
      },
    });
  }

  previousPage(): void {
    if (this.page() > 0) {
      this.load(this.page() - 1);
    }
  }

  nextPage(): void {
    if (this.page() + 1 < this.totalPages()) {
      this.load(this.page() + 1);
    }
  }
}
