import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { SupplierService } from '../../../core/services/supplier.service';
import { NotificationService } from '../../../core/services/notification.service';
import { extractErrorMessage } from '../../../core/services/api-error.util';
import { SupplierDetail } from '../../../core/models/supplier.model';
import { BENEFICIARY_TYPE_LABELS } from '../../../core/models/order.model';
import { PURPOSE_LABELS } from '../../../core/models/common.model';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { openConfirmDialog } from '../../../shared/components/confirm-dialog/confirm-dialog.component';

@Component({
  selector: 'app-supplier-detail-page',
  standalone: true,
  imports: [
    RouterLink,
    DatePipe,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatProgressSpinnerModule,
    PageHeaderComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './supplier-detail.page.html',
})
export class SupplierDetailPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly supplierService = inject(SupplierService);
  private readonly notification = inject(NotificationService);
  private readonly dialog = inject(MatDialog);

  readonly supplier = signal<SupplierDetail | null>(null);
  readonly loading = signal(true);
  readonly actionInProgress = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly typeLabel = computed(() => {
    const type = this.supplier()?.type;
    return type ? BENEFICIARY_TYPE_LABELS[type] : '';
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
