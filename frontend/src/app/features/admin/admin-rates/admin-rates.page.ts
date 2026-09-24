import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { AdminRateService } from '../../../core/services/admin-rate.service';
import { NotificationService } from '../../../core/services/notification.service';
import { extractErrorMessage } from '../../../core/services/api-error.util';
import { RateSource } from '../../../core/models/rate.model';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';

@Component({
  selector: 'app-admin-rates-page',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    DatePipe,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatTableModule,
    PageHeaderComponent,
    EmptyStateComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './admin-rates.page.html',
  styleUrl: './admin-rates.page.scss',
})
export class AdminRatesPage implements OnInit {
  private readonly adminRateService = inject(AdminRateService);
  private readonly notification = inject(NotificationService);

  readonly history = signal<RateSource[]>([]);
  readonly loadingHistory = signal(true);
  readonly publishing = signal(false);
  readonly displayedColumns = ['cfaPerCny', 'note', 'effectiveFrom', 'effectiveTo'];

  readonly form = new FormGroup({
    cfaPerCny: new FormControl<number | null>(null, { validators: [Validators.required, Validators.min(0.000001)] }),
    note: new FormControl(''),
  });

  ngOnInit(): void {
    this.loadHistory();
  }

  private loadHistory(): void {
    this.loadingHistory.set(true);
    this.adminRateService.history(0, 20).subscribe({
      next: (response) => {
        this.history.set(response.data.content);
        this.loadingHistory.set(false);
      },
      error: () => this.loadingHistory.set(false),
    });
  }

  publish(): void {
    if (this.form.invalid || this.publishing()) {
      this.form.markAllAsTouched();
      return;
    }
    this.publishing.set(true);
    const value = this.form.getRawValue();

    this.adminRateService
      .publish({ cfaPerCny: String(value.cfaPerCny), note: value.note || null })
      .subscribe({
        next: () => {
          this.publishing.set(false);
          this.notification.success('Taux publie.');
          this.form.reset();
          this.loadHistory();
        },
        error: (error) => {
          this.publishing.set(false);
          this.notification.error(extractErrorMessage(error));
        },
      });
  }
}
