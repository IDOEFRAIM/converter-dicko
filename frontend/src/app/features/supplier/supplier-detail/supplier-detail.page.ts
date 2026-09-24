import { ChangeDetectionStrategy, Component, OnInit, computed, effect, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { SupplierService } from '../../../core/services/supplier.service';
import { NotificationService } from '../../../core/services/notification.service';
import { extractErrorMessage } from '../../../core/services/api-error.util';
import { openPendingTab, resolveBlobTab } from '../../../core/services/file-download.util';
import { SupplierDetail } from '../../../core/models/supplier.model';
import { BENEFICIARY_TYPE_LABELS } from '../../../core/models/order.model';
import { PURPOSE_LABELS } from '../../../core/models/common.model';
import { AppBarService } from '../../../core/services/app-bar.service';
import { StatusBadgeComponent } from '../../../shared/components/status-badge/status-badge.component';
import { AppBarActionsDirective } from '../../../shared/directives/app-bar-actions.directive';
import { openConfirmDialog } from '../../../shared/components/confirm-dialog/confirm-dialog.component';

@Component({
  selector: 'app-supplier-detail-page',
  standalone: true,
  imports: [
    RouterLink,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    AppBarActionsDirective,
    StatusBadgeComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './supplier-detail.page.html',
  styleUrl: './supplier-detail.page.scss',
})
export class SupplierDetailPage implements OnInit {
  private readonly appBar = inject(AppBarService);
  // Titre de la barre = nom du fournisseur (AppBar(title: supplier.displayName) mobile).
  private readonly titleEffect = effect(() => {
    const name = this.supplier()?.displayName;
    if (name) {
      this.appBar.title.set(name);
    }
  });
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly supplierService = inject(SupplierService);
  private readonly notification = inject(NotificationService);
  private readonly dialog = inject(MatDialog);

  readonly supplier = signal<SupplierDetail | null>(null);
  readonly loading = signal(true);
  readonly actionInProgress = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly openingQrCode = signal(false);

  readonly typeLabel = computed(() => {
    const type = this.supplier()?.type;
    return type ? BENEFICIARY_TYPE_LABELS[type] : '';
  });
  readonly requiresQrCode = computed(() => {
    const type = this.supplier()?.type;
    return type === 'ALIPAY' || type === 'WECHAT_PAY';
  });
  readonly purposeLabel = computed(() => {
    const purpose = this.supplier()?.purpose;
    return purpose ? PURPOSE_LABELS[purpose] : null;
  });

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) {
      this.errorMessage.set('Fournisseur introuvable.');
      this.loading.set(false);
      return;
    }
    this.load(id);
  }

  private load(id: string): void {
    this.loading.set(true);
    this.supplierService.get(id).subscribe({
      next: (response) => {
        this.supplier.set(response.data);
        this.loading.set(false);
      },
      error: (error) => {
        this.errorMessage.set(extractErrorMessage(error));
        this.loading.set(false);
      },
    });
  }

  toggleFavorite(): void {
    const supplier = this.supplier();
    if (!supplier || this.actionInProgress()) {
      return;
    }
    this.actionInProgress.set(true);
    this.supplierService.setFavorite(supplier.id, !supplier.favorite).subscribe({
      next: (response) => {
        this.actionInProgress.set(false);
        this.supplier.set(response.data);
      },
      error: (error) => {
        this.actionInProgress.set(false);
        this.notification.error(extractErrorMessage(error));
      },
    });
  }

  payAgain(): void {
    const supplier = this.supplier();
    if (supplier) {
      this.router.navigate(['/suppliers', supplier.id, 'pay-again']);
    }
  }

  viewQrCode(): void {
    const supplier = this.supplier();
    if (!supplier || this.openingQrCode()) {
      return;
    }
    const tab = openPendingTab();
    this.openingQrCode.set(true);
    this.supplierService.downloadQrCode(supplier.id).subscribe({
      next: (blob) => {
        this.openingQrCode.set(false);
        resolveBlobTab(tab, blob);
      },
      error: (error) => {
        this.openingQrCode.set(false);
        tab?.close();
        this.notification.error(extractErrorMessage(error));
      },
    });
  }

  edit(): void {
    const supplier = this.supplier();
    if (supplier) {
      this.router.navigate(['/suppliers', supplier.id, 'edit']);
    }
  }

  deactivate(): void {
    const supplier = this.supplier();
    if (!supplier || this.actionInProgress()) {
      return;
    }
    openConfirmDialog(this.dialog, {
      title: 'Desactiver le fournisseur',
      message:
        'Ce fournisseur ne sera plus proposable pour un nouveau paiement. ' +
        "Vos ordres deja crees avec lui ne sont pas affectes. Aucune suppression n'a lieu.",
      confirmLabel: 'Desactiver',
    }).subscribe((confirmed) => {
      if (confirmed !== true) {
        return;
      }
      this.actionInProgress.set(true);
      this.supplierService.deactivate(supplier.id).subscribe({
        next: (response) => {
          this.actionInProgress.set(false);
          this.supplier.set(response.data);
          this.notification.success('Fournisseur desactive.');
        },
        error: (error) => {
          this.actionInProgress.set(false);
          this.notification.error(extractErrorMessage(error));
        },
      });
    });
  }
}
