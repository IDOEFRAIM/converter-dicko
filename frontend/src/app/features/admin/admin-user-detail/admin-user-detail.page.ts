import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { AdminUserService } from '../../../core/services/admin-user.service';
import { NotificationService } from '../../../core/services/notification.service';
import { extractErrorMessage } from '../../../core/services/api-error.util';
import { openPendingTab, resolveBlobTab } from '../../../core/services/file-download.util';
import { AdminUserDetail } from '../../../core/models/admin-user.model';
import { BENEFICIARY_TYPE_LABELS, BeneficiaryType } from '../../../core/models/order.model';
import { SupplierDetail } from '../../../core/models/supplier.model';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';
import { StatusBadgeComponent } from '../../../shared/components/status-badge/status-badge.component';
import { openConfirmDialog } from '../../../shared/components/confirm-dialog/confirm-dialog.component';
import { MoneyPipe } from '../../../shared/pipes/money.pipe';

/**
 * Fiche complete d'un compte client + son carnet de fournisseurs (retour client sept. 2026 :
 * "l'admin n'arrive pas a voir les detail des different fournisseur pour chaque
 * utilisateur...c'est ca qui permet de pouvoir faire les transfert") -- avant cette page, la
 * seule vue admin d'un fournisseur passait par un ordre deja cree (AdminOrderDetailPage), donc
 * jamais AVANT que le client ne l'utilise pour un transfert.
 */
@Component({
  selector: 'app-admin-user-detail-page',
  standalone: true,
  imports: [
    RouterLink,
    DatePipe,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTableModule,
    PageHeaderComponent,
    EmptyStateComponent,
    StatusBadgeComponent,
    MoneyPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './admin-user-detail.page.html',
  styleUrl: './admin-user-detail.page.scss',
})
export class AdminUserDetailPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly adminUserService = inject(AdminUserService);
  private readonly notification = inject(NotificationService);
  private readonly dialog = inject(MatDialog);

  private userId = '';

  readonly user = signal<AdminUserDetail | null>(null);
  readonly loading = signal(true);
  readonly actionPending = signal(false);

  readonly suppliers = signal<SupplierDetail[]>([]);
  readonly loadingSuppliers = signal(true);
  readonly displayedColumns = ['displayName', 'type', 'identifier', 'status', 'readyForPayment', 'actions'];

  beneficiaryTypeLabel(type: BeneficiaryType): string {
    return BENEFICIARY_TYPE_LABELS[type];
  }

  readonly fullName = computed(() => {
    const u = this.user();
    return u ? `${u.firstName} ${u.lastName}`.trim() : '';
  });

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.userId = id;
      this.load();
      this.loadSuppliers();
    }
  }

  private load(): void {
    this.loading.set(true);
    this.adminUserService.get(this.userId).subscribe({
      next: (response) => {
        this.user.set(response.data);
        this.loading.set(false);
      },
      error: (error) => {
        this.notification.error(extractErrorMessage(error));
        this.loading.set(false);
      },
    });
  }

  private loadSuppliers(): void {
    this.loadingSuppliers.set(true);
    this.adminUserService.suppliers(this.userId).subscribe({
      next: (response) => {
        this.suppliers.set(response.data.content);
        this.loadingSuppliers.set(false);
      },
      error: (error) => {
        this.notification.error(extractErrorMessage(error));
        this.loadingSuppliers.set(false);
      },
    });
  }

  /** Le compte bancaire chinois s'identifie par son numero (deja en clair, voir SupplierService.listForAdmin) ;
   * Alipay/WeChat n'a pas d'identifiant textuel fiable -- seul le code QR (viewQrCode) fait foi. */
  identifier(supplier: SupplierDetail): string {
    if (supplier.type === 'CHINESE_BANK_ACCOUNT') {
      return [supplier.bankName, supplier.accountNumber].filter(Boolean).join(' · ') || '—';
    }
    return supplier.qrCodeUploaded ? 'Code QR televerse' : 'Aucun code QR';
  }

  viewQrCode(supplier: SupplierDetail): void {
    const tab = openPendingTab();
    this.adminUserService.supplierQrCode(this.userId, supplier.id).subscribe({
      next: (blob) => resolveBlobTab(tab, blob),
      error: (error) => {
        tab?.close();
        this.notification.error(extractErrorMessage(error));
      },
    });
  }

  block(): void {
    if (this.actionPending()) {
      return;
    }
    openConfirmDialog(this.dialog, {
      title: 'Bloquer ce compte',
      message: 'Le client ne pourra plus se connecter ni operer, immediatement.',
      confirmLabel: 'Bloquer',
      requireReasonLabel: 'Motif du blocage',
    }).subscribe((reason) => {
      if (!reason || typeof reason !== 'string') {
        return;
      }
      this.actionPending.set(true);
      this.adminUserService.block(this.userId, { reason }).subscribe({
        next: (response) => {
          this.actionPending.set(false);
          this.user.set(response.data);
          this.notification.success('Compte bloque.');
        },
        error: (error) => {
          this.actionPending.set(false);
          this.notification.error(extractErrorMessage(error));
        },
      });
    });
  }

  unblock(): void {
    if (this.actionPending()) {
      return;
    }
    this.actionPending.set(true);
    this.adminUserService.unblock(this.userId).subscribe({
      next: (response) => {
        this.actionPending.set(false);
        this.user.set(response.data);
        this.notification.success('Compte debloque.');
      },
      error: (error) => {
        this.actionPending.set(false);
        this.notification.error(extractErrorMessage(error));
      },
    });
  }

  verifyKyc(): void {
    if (this.actionPending()) {
      return;
    }
    this.actionPending.set(true);
    this.adminUserService.verifyKyc(this.userId).subscribe({
      next: (response) => {
        this.actionPending.set(false);
        this.user.set(response.data);
        this.notification.success('Identite verifiee.');
      },
      error: (error) => {
        this.actionPending.set(false);
        this.notification.error(extractErrorMessage(error));
      },
    });
  }

  revokeKyc(): void {
    if (this.actionPending()) {
      return;
    }
    this.actionPending.set(true);
    this.adminUserService.revokeKyc(this.userId).subscribe({
      next: (response) => {
        this.actionPending.set(false);
        this.user.set(response.data);
        this.notification.success("Verification d'identite revoquee.");
      },
      error: (error) => {
        this.actionPending.set(false);
        this.notification.error(extractErrorMessage(error));
      },
    });
  }
}
