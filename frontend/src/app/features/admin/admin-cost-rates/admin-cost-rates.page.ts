import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { AdminCostRateService } from '../../../core/services/admin-cost-rate.service';
import { NotificationService } from '../../../core/services/notification.service';
import { extractErrorMessage } from '../../../core/services/api-error.util';
import { CostRateConfiguration } from '../../../core/models/cost-rate.model';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';

/**
 * Publication du coût de revient du jour (`daily_cost_rate_configurations`). C'est cette
 * configuration qui alimente le pricing des devis depuis la Phase 3.1 — sans elle, aucun
 * client ne peut créer de devis. À ne pas confondre avec « Taux de change » (RateSource,
 * utilisé uniquement par le taux préférentiel).
 */
@Component({
  selector: 'app-admin-cost-rates-page',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    DatePipe,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatTableModule,
    PageHeaderComponent,
    EmptyStateComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './admin-cost-rates.page.html',
  styleUrl: './admin-cost-rates.page.scss',
})
export class AdminCostRatesPage implements OnInit {
  private readonly service = inject(AdminCostRateService);
  private readonly notification = inject(NotificationService);

  readonly current = signal<CostRateConfiguration | null>(null);
  readonly history = signal<CostRateConfiguration[]>([]);
  readonly loading = signal(true);
  readonly publishing = signal(false);
  readonly displayedColumns = [
    'businessDate',
    'rateXofUsd',
    'rateUsdCny',
    'fees',
    'breakEvenRate',
    'createdAt',
  ];

  private today(): string {
    return new Date().toISOString().slice(0, 10);
  }

  readonly form = new FormGroup({
    businessDate: new FormControl(this.today(), {
      nonNullable: true,
      validators: [Validators.required],
    }),
    rateXofUsd: new FormControl<number | null>(null, {
      validators: [Validators.required, Validators.min(0.000001)],
    }),
    rateUsdCny: new FormControl<number | null>(null, {
      validators: [Validators.required, Validators.min(0.000001)],
    }),
    feeXofUsdPercent: new FormControl<number | null>(0, {
      validators: [Validators.required, Validators.min(0), Validators.max(0.999999)],
    }),
    feeUsdCnyFixedUsd: new FormControl<number | null>(0, {
      validators: [Validators.required, Validators.min(0)],
    }),
    referenceAmountXof: new FormControl<number | null>(1000000, {
      validators: [Validators.required, Validators.min(0.01)],
    }),
    note: new FormControl(''),
  });

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.service.current().subscribe({
      next: (response) => {
        this.current.set(response.data);
        this.loadHistory();
      },
      error: (error: unknown) => {
        // 404 = aucune config publiée : cas normal au démarrage, pas une erreur à afficher.
        if (!(error instanceof HttpErrorResponse && error.status === 404)) {
          this.notification.error(extractErrorMessage(error));
        }
        this.current.set(null);
        this.loadHistory();
      },
    });
  }

  private loadHistory(): void {
    this.service.history(0, 30).subscribe({
      next: (response) => {
        this.history.set(response.data.content);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  publish(): void {
    if (this.form.invalid || this.publishing()) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.publishing.set(true);
    this.service
      .publish({
        businessDate: v.businessDate,
        rateXofUsd: String(v.rateXofUsd),
        rateUsdCny: String(v.rateUsdCny),
        feeXofUsdPercent: String(v.feeXofUsdPercent ?? 0),
        feeUsdCnyFixedUsd: String(v.feeUsdCnyFixedUsd ?? 0),
        referenceAmountXof: String(v.referenceAmountXof),
        note: v.note?.trim() || null,
      })
      .subscribe({
        next: (response) => {
          this.publishing.set(false);
          this.notification.success(
            `Configuration publiée. Coût de revient : ${response.data.breakEvenRate} XOF / CNY.`,
          );
          this.form.patchValue({ note: '' });
          this.reload();
        },
        error: (error) => {
          this.publishing.set(false);
          this.notification.error(extractErrorMessage(error));
        },
      });
  }
}
