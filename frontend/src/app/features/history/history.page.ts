import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { OrderService } from '../../core/services/order.service';
import { SupplierService } from '../../core/services/supplier.service';
import { extractErrorMessage } from '../../core/services/api-error.util';
import { OrderHistoryEntry, OrderHistoryQuery, OrderStatus } from '../../core/models/order.model';
import { Purpose, PURPOSE_LABELS, PURPOSE_OPTIONS } from '../../core/models/common.model';
import { SupplierSummary } from '../../core/models/supplier.model';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge.component';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { MoneyPipe } from '../../shared/pipes/money.pipe';

const STATUS_OPTIONS: OrderStatus[] = [
  'AWAITING_PAYMENT',
  'PAYMENT_SUBMITTED',
  'PAYMENT_VERIFIED',
  'PROCESSING',
  'COMPLETED',
  'CANCELLED',
  'REJECTED',
  'EXPIRED',
];

/**
 * Historique enrichi des ordres. Tous les filtres (statut, motif, fournisseur, plage de
 * dates) et la pagination sont appliques <b>cote serveur</b> via {@code GET /api/v1/orders/history}
 * — aucun filtrage local qui masquerait des pages non chargees. Les montants affiches sont
 * ceux figes a la creation de l'ordre, jamais recalcules.
 */
@Component({
  selector: 'app-history-page',
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
    MatSelectModule,
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
  private readonly supplierService = inject(SupplierService);

  readonly orders = signal<OrderHistoryEntry[]>([]);
  readonly suppliers = signal<SupplierSummary[]>([]);
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);

  readonly statusOptions = STATUS_OPTIONS;
  readonly purposeOptions = PURPOSE_OPTIONS;
  readonly purposeLabels = PURPOSE_LABELS;

  private readonly pageSize = 15;

  readonly filters = new FormGroup({
    status: new FormControl<OrderStatus | ''>(''),
    purpose: new FormControl<Purpose | ''>(''),
    supplierId: new FormControl<string>(''),
    from: new FormControl<string>(''),
    to: new FormControl<string>(''),
  });

  ngOnInit(): void {
    this.supplierService.list(0, 100).subscribe({
      next: (response) => this.suppliers.set(response.data.content),
      error: () => undefined,
    });
    this.load(0);
  }

  applyFilters(): void {
    this.load(0);
  }

  resetFilters(): void {
    this.filters.reset({ status: '', purpose: '', supplierId: '', from: '', to: '' });
    this.load(0);
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

  private load(page: number): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.orderService.history(this.buildQuery(page)).subscribe({
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

  private buildQuery(page: number): OrderHistoryQuery {
    const v = this.filters.getRawValue();
    return {
      page,
      size: this.pageSize,
      status: v.status || null,
      purpose: v.purpose || null,
      supplierId: v.supplierId || null,
      // Le champ <input type="date"> designe un jour du calendrier LOCAL de l'utilisateur :
      // on convertit minuit local -> instant UTC, sans forcer "Z" sur une date sans fuseau.
      from: v.from ? localStartOfDayIso(v.from) : null,
      // Borne haute exclusive cote backend (createdAt < to) : minuit local du lendemain, pour
      // inclure toute la journee choisie quel que soit le fuseau de l'utilisateur.
      to: v.to ? localStartOfNextDayIso(v.to) : null,
    };
  }
}

/** `YYYY-MM-DD` (jour local) -> instant ISO-8601 de minuit local ce jour-la. */
function localStartOfDayIso(date: string): string {
  return new Date(`${date}T00:00:00`).toISOString();
}

/** `YYYY-MM-DD` (jour local) -> instant ISO-8601 de minuit local le lendemain. */
function localStartOfNextDayIso(date: string): string {
  const d = new Date(`${date}T00:00:00`);
  d.setDate(d.getDate() + 1);
  return d.toISOString();
}
