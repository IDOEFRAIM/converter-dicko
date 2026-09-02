import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { AdminTreasuryService } from '../../../core/services/admin-treasury.service';
import { NotificationService } from '../../../core/services/notification.service';
import { extractErrorMessage } from '../../../core/services/api-error.util';
import { Currency, TreasuryAccount, TreasuryTransaction } from '../../../core/models/treasury.model';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { MoneyPipe } from '../../../shared/pipes/money.pipe';

@Component({
  selector: 'app-admin-treasury-page',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    DatePipe,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatTableModule,
    PageHeaderComponent,
    MoneyPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './admin-treasury.page.html',
  styleUrl: './admin-treasury.page.scss',
})
export class AdminTreasuryPage implements OnInit {
  private readonly adminTreasuryService = inject(AdminTreasuryService);
  private readonly notification = inject(NotificationService);

  readonly xof = signal<TreasuryAccount | null>(null);
  readonly cny = signal<TreasuryAccount | null>(null);
  readonly transactions = signal<TreasuryTransaction[]>([]);
  readonly loading = signal(true);
  readonly submitting = signal(false);
  readonly selectedCurrency = signal<Currency>('CNY');
  readonly displayedColumns = ['type', 'amount', 'balanceAfter', 'reason', 'createdAt'];

  readonly form = new FormGroup({
    currency: new FormControl<Currency>('CNY', { nonNullable: true, validators: [Validators.required] }),
    amount: new FormControl<number | null>(null, { validators: [Validators.required] }),
    reason: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });

  ngOnInit(): void {
    this.loadAccounts();
    this.loadTransactions('CNY');
  }

  private loadAccounts(): void {
    this.loading.set(true);
    this.adminTreasuryService.account('XOF').subscribe((response) => this.xof.set(response.data));
    this.adminTreasuryService.account('CNY').subscribe({
      next: (response) => {
        this.cny.set(response.data);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  selectCurrency(currency: Currency): void {
    this.selectedCurrency.set(currency);
    this.loadTransactions(currency);
  }

  private loadTransactions(currency: Currency): void {
    this.adminTreasuryService.transactions(currency, 0, 20).subscribe((response) => {
      this.transactions.set(response.data.content);
    });
  }

  deposit(): void {
    this.performAdjustment((request) => this.adminTreasuryService.deposit(request), 'Depot enregistre.');
  }

  adjust(): void {
    this.performAdjustment((request) => this.adminTreasuryService.adjust(request), 'Ajustement enregistre.');
  }

  private performAdjustment(
    call: (request: { currency: Currency; amount: string; reason: string }) => ReturnType<AdminTreasuryService['deposit']>,
    successMessage: string,
  ): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    const value = this.form.getRawValue();

    call({ currency: value.currency, amount: String(value.amount), reason: value.reason }).subscribe({
      next: () => {
        this.submitting.set(false);
        this.notification.success(successMessage);
        this.form.reset({ currency: value.currency, amount: null, reason: '' });
        this.loadAccounts();
        this.loadTransactions(this.selectedCurrency());
      },
      error: (error) => {
        this.submitting.set(false);
        this.notification.error(extractErrorMessage(error));
      },
    });
  }
}
