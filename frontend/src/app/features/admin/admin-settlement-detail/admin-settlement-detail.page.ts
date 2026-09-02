import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AdminSettlementService } from '../../../core/services/admin-settlement.service';
import { NotificationService } from '../../../core/services/notification.service';
import { extractErrorMessage } from '../../../core/services/api-error.util';
import { openPendingTab, resolveBlobTab } from '../../../core/services/file-download.util';
import { Settlement } from '../../../core/models/settlement.model';
import { StatusBadgeComponent } from '../../../shared/components/status-badge/status-badge.component';
import { MoneyPipe } from '../../../shared/pipes/money.pipe';

@Component({
  selector: 'app-admin-settlement-detail-page',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    StatusBadgeComponent,
    MoneyPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './admin-settlement-detail.page.html',
  styleUrl: './admin-settlement-detail.page.scss',
})
export class AdminSettlementDetailPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly adminSettlementService = inject(AdminSettlementService);
  private readonly notification = inject(NotificationService);

  readonly settlement = signal<Settlement | null>(null);
  readonly loading = signal(true);
  readonly uploading = signal(false);
  readonly executing = signal(false);

  readonly executeForm = new FormGroup({
    settlementReference: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    notes: new FormControl(''),
  });

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.load(id);
    }
  }

  private load(id: string): void {
    this.loading.set(true);
    this.adminSettlementService.get(id).subscribe({
      next: (response) => {
        this.settlement.set(response.data);
        this.loading.set(false);
      },
      error: (error) => {
        this.notification.error(extractErrorMessage(error));
        this.loading.set(false);
      },
    });
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    const settlement = this.settlement();
    if (!file || !settlement || this.uploading()) {
      return;
    }
    this.uploading.set(true);
    this.adminSettlementService.uploadProof(settlement.id, file).subscribe({
      next: () => {
        this.uploading.set(false);
        this.notification.success('Preuve enregistree.');
        this.load(settlement.id);
      },
      error: (error) => {
        this.uploading.set(false);
        this.notification.error(extractErrorMessage(error));
      },
    });
  }

  viewProof(): void {
    const settlement = this.settlement();
    const proof = settlement?.proofs[0];
    if (!settlement || !proof) {
      return;
    }
    const tab = openPendingTab();
    this.adminSettlementService.downloadProof(settlement.id, proof.id).subscribe({
      next: (blob) => resolveBlobTab(tab, blob),
      error: (error) => {
        tab?.close();
        this.notification.error(extractErrorMessage(error));
      },
    });
  }

  execute(): void {
    const settlement = this.settlement();
    if (!settlement || this.executeForm.invalid || this.executing()) {
      this.executeForm.markAllAsTouched();
      return;
    }
    this.executing.set(true);
    const value = this.executeForm.getRawValue();

    this.adminSettlementService
      .execute(settlement.id, { settlementReference: value.settlementReference, notes: value.notes || null })
      .subscribe({
        next: (response) => {
          this.executing.set(false);
          this.settlement.set(response.data);
          this.notification.success('Reglement execute.');
        },
        error: (error) => {
          this.executing.set(false);
          this.notification.error(extractErrorMessage(error));
        },
      });
  }
}
