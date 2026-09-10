import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { AdminKycService } from '../../../core/services/admin-kyc.service';
import { NotificationService } from '../../../core/services/notification.service';
import { extractErrorMessage } from '../../../core/services/api-error.util';
import { openPendingTab, resolveBlobTab } from '../../../core/services/file-download.util';
import {
  KYC_DOCUMENT_TYPE_LABELS,
  KycAdminSubmission,
  KycFileKind,
} from '../../../core/models/kyc.model';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';
import { openConfirmDialog } from '../../../shared/components/confirm-dialog/confirm-dialog.component';

@Component({
  selector: 'app-admin-kyc-page',
  standalone: true,
  imports: [
    DatePipe,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTableModule,
    PageHeaderComponent,
    EmptyStateComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './admin-kyc.page.html',
  styleUrl: './admin-kyc.page.scss',
})
export class AdminKycPage implements OnInit {
  private readonly adminKycService = inject(AdminKycService);
  private readonly notification = inject(NotificationService);
  private readonly dialog = inject(MatDialog);

  readonly submissions = signal<KycAdminSubmission[]>([]);
  readonly loading = signal(true);
  readonly processingId = signal<string | null>(null);
  readonly displayedColumns = ['user', 'documentType', 'submittedAt', 'files', 'actions'];

  documentLabel(submission: KycAdminSubmission): string {
    return KYC_DOCUMENT_TYPE_LABELS[submission.documentType] ?? submission.documentType;
  }

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.adminKycService.pending(0, 30).subscribe({
      next: (response) => {
        this.submissions.set(response.data.content);
        this.loading.set(false);
      },
      error: (error) => {
        this.loading.set(false);
        this.notification.error(extractErrorMessage(error));
      },
    });
  }

  viewFile(submission: KycAdminSubmission, kind: KycFileKind): void {
    const tab = openPendingTab();
    this.adminKycService.downloadFile(submission.id, kind).subscribe({
      next: (blob) => resolveBlobTab(tab, blob, `kyc-${kind}-${submission.userFullName}`),
      error: (error) => {
        tab?.close();
        this.notification.error(extractErrorMessage(error));
      },
    });
  }

  approve(submission: KycAdminSubmission): void {
    if (this.processingId()) {
      return;
    }
    openConfirmDialog(this.dialog, {
      title: "Approuver l'identite",
      message: `Confirmer l'identite de ${submission.userFullName} ? Le compte pourra alors payer un fournisseur.`,
      confirmLabel: 'Approuver',
    }).subscribe((confirmed) => {
      if (!confirmed) {
        return;
      }
      this.processingId.set(submission.id);
      this.adminKycService.approve(submission.id).subscribe({
        next: () => {
          this.processingId.set(null);
          this.notification.success('Identite verifiee.');
          this.load();
        },
        error: (error) => {
          this.processingId.set(null);
          this.notification.error(extractErrorMessage(error));
        },
      });
    });
  }

  reject(submission: KycAdminSubmission): void {
    if (this.processingId()) {
      return;
    }
    openConfirmDialog(this.dialog, {
      title: 'Rejeter le dossier',
      message: "L'utilisateur devra soumettre un nouveau dossier.",
      confirmLabel: 'Rejeter',
      requireReasonLabel: 'Motif du rejet',
    }).subscribe((reason) => {
      if (!reason || typeof reason !== 'string') {
        return;
      }
      this.processingId.set(submission.id);
      this.adminKycService.reject(submission.id, { reason }).subscribe({
        next: () => {
          this.processingId.set(null);
          this.notification.success('Dossier rejete.');
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
