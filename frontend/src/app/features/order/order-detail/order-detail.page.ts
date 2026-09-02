import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { OrderService } from '../../../core/services/order.service';
import { SettlementService } from '../../../core/services/settlement.service';
import { NotificationService } from '../../../core/services/notification.service';
import { extractErrorMessage } from '../../../core/services/api-error.util';
import { OrderDetail } from '../../../core/models/order.model';
import { StatusBadgeComponent } from '../../../shared/components/status-badge/status-badge.component';
import { MoneyPipe } from '../../../shared/pipes/money.pipe';
import { openConfirmDialog } from '../../../shared/components/confirm-dialog/confirm-dialog.component';

const BENEFICIARY_TYPE_LABELS: Record<string, string> = {
  ALIPAY: 'Alipay',
  WECHAT_PAY: 'WeChat Pay',
  CHINESE_BANK_ACCOUNT: 'Compte bancaire chinois',
};

@Component({
  selector: 'app-order-detail-page',
  standalone: true,
  imports: [
    RouterLink,
    DatePipe,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatProgressSpinnerModule,
    StatusBadgeComponent,
    MoneyPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './order-detail.page.html',
  styleUrl: './order-detail.page.scss',
})
export class OrderDetailPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly orderService = inject(OrderService);
  private readonly settlementService = inject(SettlementService);
  private readonly notification = inject(NotificationService);
  private readonly dialog = inject(MatDialog);

  readonly order = signal<OrderDetail | null>(null);
  readonly loading = signal(true);
  readonly cancelling = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly settlementView = computed(() => {
    const order = this.order();
    return order ? this.settlementService.deriveFromOrder(order) : null;
  });

  readonly beneficiaryTypeLabel = computed(() => {
    const type = this.order()?.beneficiary.type;
    return type ? (BENEFICIARY_TYPE_LABELS[type] ?? type) : '';
  });

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) {
      this.errorMessage.set('Ordre introuvable.');
      this.loading.set(false);
      return;
    }
    this.load(id);
  }

  private load(id: string): void {
    this.loading.set(true);
    this.orderService.get(id).subscribe({
      next: (response) => {
        this.order.set(response.data);
        this.loading.set(false);
      },
      error: (error) => {
        this.errorMessage.set(extractErrorMessage(error));
        this.loading.set(false);
      },
    });
  }

  goToPayment(): void {
    const order = this.order();
    if (order) {
      this.router.navigate(['/orders', order.id, 'payment']);
    }
  }

  cancel(): void {
    const order = this.order();
    if (!order || this.cancelling()) {
      return;
    }
    openConfirmDialog(this.dialog, {
      title: "Annuler l'ordre",
      message: 'Cette action est definitive et libere la reservation de tresorerie associee.',
      confirmLabel: 'Annuler l\'ordre',
      requireReasonLabel: "Motif de l'annulation",
    }).subscribe((reason) => {
      if (!reason || typeof reason !== 'string') {
        return;
      }
      this.cancelling.set(true);
      this.orderService.cancel(order.id, { reason }).subscribe({
        next: (response) => {
          this.cancelling.set(false);
          this.order.set(response.data);
          this.notification.success('Ordre annule.');
        },
        error: (error) => {
          this.cancelling.set(false);
          this.notification.error(extractErrorMessage(error));
        },
      });
    });
  }
}
