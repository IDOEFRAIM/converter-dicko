import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AdminOrderService } from '../../../core/services/admin-order.service';
import { AdminSettlementService } from '../../../core/services/admin-settlement.service';
import { NotificationService } from '../../../core/services/notification.service';
import { extractErrorMessage } from '../../../core/services/api-error.util';
import { IdempotencyAttempt } from '../../../core/services/idempotency.util';
import { OrderDetail } from '../../../core/models/order.model';
import { StatusBadgeComponent } from '../../../shared/components/status-badge/status-badge.component';
import { MoneyPipe } from '../../../shared/pipes/money.pipe';

@Component({
  selector: 'app-admin-order-detail-page',
  standalone: true,
  imports: [
    RouterLink,
    DatePipe,
    MatButtonModule,
    MatCardModule,
    MatProgressSpinnerModule,
    StatusBadgeComponent,
    MoneyPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './admin-order-detail.page.html',
  styleUrl: './admin-order-detail.page.scss',
})
export class AdminOrderDetailPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly adminOrderService = inject(AdminOrderService);
  private readonly adminSettlementService = inject(AdminSettlementService);
  private readonly notification = inject(NotificationService);

  readonly order = signal<OrderDetail | null>(null);
  readonly loading = signal(true);
  readonly creatingSettlement = signal(false);
  private readonly settlementIdempotency = new IdempotencyAttempt();

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.load(id);
    }
  }

  private load(id: string): void {
    this.loading.set(true);
    this.adminOrderService.get(id).subscribe({
      next: (response) => {
        this.order.set(response.data);
        this.loading.set(false);
      },
      error: (error) => {
        this.notification.error(extractErrorMessage(error));
        this.loading.set(false);
      },
    });
  }

  createSettlement(): void {
    const order = this.order();
    if (!order || this.creatingSettlement()) {
      return;
    }
    this.creatingSettlement.set(true);
    const idempotencyKey = this.settlementIdempotency.keyFor({ orderId: order.id });
    this.adminSettlementService.createForOrder(order.id, idempotencyKey).subscribe({
      next: (response) => {
        this.creatingSettlement.set(false);
        this.settlementIdempotency.complete();
        this.notification.success('Reglement cree.');
        this.router.navigate(['/admin/settlements', response.data.id]);
      },
      error: (error) => {
        this.creatingSettlement.set(false);
        this.notification.error(extractErrorMessage(error));
      },
    });
  }
}
