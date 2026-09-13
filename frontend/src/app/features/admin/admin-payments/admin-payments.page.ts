import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { AdminPaymentService } from '../../../core/services/admin-payment.service';
import { NotificationService } from '../../../core/services/notification.service';
import { extractErrorMessage } from '../../../core/services/api-error.util';
import { openPendingTab, resolveBlobTab } from '../../../core/services/file-download.util';
import { Payment } from '../../../core/models/payment.model';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';
import { MoneyPipe } from '../../../shared/pipes/money.pipe';
import { openConfirmDialog } from '../../../shared/components/confirm-dialog/confirm-dialog.component';

@Component({
  selector: 'app-admin-payments-page',
  standalone: true,
  imports: [
    DatePipe,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTableModule,
    PageHeaderComponent,
    EmptyStateComponent,
    MoneyPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './admin-payments.page.html',
  styleUrl: './admin-payments.page.scss',
})
export class AdminPaymentsPage implements OnInit {
  private readonly adminPaymentService = inject(AdminPaymentService);
  private readonly notification = inject(NotificationService);
  private readonly dialog = inject(MatDialog);

  readonly payments = signal<Payment[]>([]);
  readonly loading = signal(true);
  readonly processingId = signal<string | null>(null);
  readonly displayedColumns = ['reference', 'method', 'payer', 'receivedAmountXof', 'submittedAt', 'proofs', 'actions'];

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.adminPaymentService.pending(0, 30).subscribe({
      next: (response) => {
        this.payments.set(response.data.content);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  hasProof(payment: Payment): boolean {
    return payment.proofs.length > 0;
  }

  viewProof(payment: Payment): void {
    const proof = payment.proofs[0];
    if (!proof) {
      return;
    }
    const tab = openPendingTab();
    this.adminPaymentService.downloadProof(payment.id, proof.id).subscribe({
      next: (blob) => resolveBlobTab(tab, blob),
      error: (error) => {
        tab?.close();
        this.notification.error(extractErrorMessage(error));
      },
    });
  }

  confirm(payment: Payment): void {
    if (this.processingId()) {
      return;
    }
    this.processingId.set(payment.id);
    this.adminPaymentService.confirm(payment.id).subscribe({
      next: () => {
        this.processingId.set(null);
        this.notification.success('Paiement confirme.');
        this.load();
      },
      error: (error) => {
        this.processingId.set(null);
        this.notification.error(extractErrorMessage(error));
      },
    });
  }

  reject(payment: Payment): void {
    if (this.processingId()) {
      return;
    }
    openConfirmDialog(this.dialog, {
      title: 'Rejeter le paiement',
      message: 'Le client pourra resoumettre une preuve de paiement pour ce meme ordre.',
      confirmLabel: 'Rejeter',
      requireReasonLabel: 'Motif du rejet',
    }).subscribe((reason) => {
      if (!reason || typeof reason !== 'string') {
        return;
      }
      this.processingId.set(payment.id);
      this.adminPaymentService.reject(payment.id, { reason }).subscribe({
        next: () => {
          this.processingId.set(null);
          this.notification.success('Paiement rejete.');
          this.load();
        },
        error: (error) => {
          this.processingId.set(null);
          this.notification.error(extractErrorMessage(error));
        },
      });
    });
  }
}
