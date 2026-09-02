import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { AdminOrderService } from '../../../core/services/admin-order.service';
import { OrderStatus, OrderSummary } from '../../../core/models/order.model';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';
import { StatusBadgeComponent } from '../../../shared/components/status-badge/status-badge.component';
import { MoneyPipe } from '../../../shared/pipes/money.pipe';

const STATUSES: OrderStatus[] = [
  'AWAITING_PAYMENT',
  'PAYMENT_SUBMITTED',
  'PAYMENT_VERIFIED',
  'PROCESSING',
  'COMPLETED',
  'CANCELLED',
  'REJECTED',
  'EXPIRED',
];

@Component({
  selector: 'app-admin-orders-page',
  standalone: true,
  imports: [
    RouterLink,
    DatePipe,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatTableModule,
    PageHeaderComponent,
    EmptyStateComponent,
    StatusBadgeComponent,
    MoneyPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './admin-orders.page.html',
  styleUrl: './admin-orders.page.scss',
})
export class AdminOrdersPage implements OnInit {
  private readonly adminOrderService = inject(AdminOrderService);

  readonly statuses = STATUSES;
  readonly selectedStatus = signal<OrderStatus | null>(null);
  readonly orders = signal<OrderSummary[]>([]);
  readonly loading = signal(true);
  readonly displayedColumns = ['reference', 'status', 'amountXof', 'amountCny', 'createdAt', 'actions'];

  ngOnInit(): void {
    this.load();
  }

  onStatusChange(status: OrderStatus | null): void {
    this.selectedStatus.set(status);
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.adminOrderService.list(this.selectedStatus(), 0, 30).subscribe({
      next: (response) => {
        this.orders.set(response.data.content);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }
}
