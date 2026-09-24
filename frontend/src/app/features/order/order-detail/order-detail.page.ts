import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { OrderService } from '../../../core/services/order.service';
import { SettlementService } from '../../../core/services/settlement.service';
import { NotificationService } from '../../../core/services/notification.service';
import { extractErrorMessage } from '../../../core/services/api-error.util';
import { openPendingTab, resolveBlobTab } from '../../../core/services/file-download.util';
import { ORDER_STATUS_MESSAGES, OrderDetail } from '../../../core/models/order.model';
import { PURPOSE_LABELS } from '../../../core/models/common.model';
import { StatusBadgeComponent } from '../../../shared/components/status-badge/status-badge.component';
import { CorridorComponent } from '../../../shared/components/corridor/corridor.component';
import { MoneyPipe } from '../../../shared/pipes/money.pipe';
import { TicketRow, TransferTicketComponent } from '../../../shared/components/transfer-ticket/transfer-ticket.component';
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
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    StatusBadgeComponent,
    CorridorComponent,
    TransferTicketComponent,
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
  readonly downloadingReceipt = signal(false);
  readonly downloadingProforma = signal(false);
  private readonly money = new MoneyPipe();

  /** Lignes du ticket de transfert (TransferTicket mobile). */
  readonly ticketRows = computed<TicketRow[]>(() => {
    const o = this.order();
    return o
      ? [
          { label: 'Taux', value: `1 CNY = ${o.customerRate} XOF` },
          { label: 'Frais', value: this.money.transform(o.feeXof, 'XOF') },
          { label: 'Reference', value: o.reference, copyable: true },
        ]
      : [];
  });
  readonly errorMessage = signal<string | null>(null);

  readonly purposeLabel = computed(() => {
    const purpose = this.order()?.purpose;
    return purpose ? PURPOSE_LABELS[purpose] : null;
  });

  /** Carte reglement dediee : uniquement pertinente une fois le paiement verifie. */
  readonly settlementView = computed(() => {
    const order = this.order();
    if (!order || (order.status !== 'PROCESSING' && order.status !== 'COMPLETED')) {
      return null;
    }
    return this.settlementService.deriveFromOrder(order);
  });

  readonly statusMessage = computed(() => {
    const order = this.order();
    return order ? ORDER_STATUS_MESSAGES[order.status] : null;
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

  /**
   * Telecharge le justificatif PDF. L'onglet est ouvert de maniere synchrone AVANT l'appel
   * reseau (contrainte anti-popup des navigateurs), puis redirige vers le blob une fois recu
   * — le PDF vient du backend, jamais reconstruit ici.
   */
  downloadReceipt(): void {
    const order = this.order();
    if (!order || this.downloadingReceipt()) {
      return;
    }
    const tab = openPendingTab();
    this.downloadingReceipt.set(true);
    this.orderService.downloadReceipt(order.id).subscribe({
      next: (blob) => {
        this.downloadingReceipt.set(false);
        resolveBlobTab(tab, blob);
      },
      error: (error) => {
        this.downloadingReceipt.set(false);
        tab?.close();
        this.notification.error(extractErrorMessage(error));
      },
    });
  }

  /** Facture proforma — meme mecanique d'onglet que le justificatif. */
  downloadProforma(): void {
    const order = this.order();
    if (!order || this.downloadingProforma()) {
      return;
    }
    const tab = openPendingTab();
    this.downloadingProforma.set(true);
    this.orderService.downloadProforma(order.id).subscribe({
      next: (blob) => {
        this.downloadingProforma.set(false);
        resolveBlobTab(tab, blob);
      },
      error: (error) => {
        this.downloadingProforma.set(false);
        tab?.close();
        this.notification.error(extractErrorMessage(error));
      },
    });
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
