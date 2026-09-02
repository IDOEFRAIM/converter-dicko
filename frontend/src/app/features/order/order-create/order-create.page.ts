import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { OrderService } from '../../../core/services/order.service';
import { extractErrorMessage } from '../../../core/services/api-error.util';
import { BeneficiaryType } from '../../../core/models/order.model';

@Component({
  selector: 'app-order-create-page',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatProgressSpinnerModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './order-create.page.html',
})
export class OrderCreatePage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly orderService = inject(OrderService);

  readonly quoteId = signal<string | null>(null);
  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly beneficiaryTypes: { value: BeneficiaryType; label: string }[] = [
    { value: 'ALIPAY', label: 'Alipay' },
    { value: 'WECHAT_PAY', label: 'WeChat Pay' },
    { value: 'CHINESE_BANK_ACCOUNT', label: 'Compte bancaire chinois' },
  ];

  readonly form = new FormGroup({
    type: new FormControl<BeneficiaryType>('ALIPAY', { nonNullable: true, validators: [Validators.required] }),
    fullName: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    identifier: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    bankName: new FormControl(''),
    bankBranch: new FormControl(''),
    note: new FormControl(''),
  });

  get isBankAccount(): boolean {
    return this.form.controls.type.value === 'CHINESE_BANK_ACCOUNT';
  }

  ngOnInit(): void {
    const quoteId = this.route.snapshot.queryParamMap.get('quoteId');
    if (!quoteId) {
      this.errorMessage.set('Aucun devis a associer. Recommencez depuis un nouveau devis.');
      return;
    }
    this.quoteId.set(quoteId);
  }

  submit(): void {
    const quoteId = this.quoteId();
    if (!quoteId || this.form.invalid || this.loading()) {
      this.form.markAllAsTouched();
      return;
    }
    if (this.isBankAccount && !this.form.controls.bankName.value) {
      this.form.controls.bankName.setErrors({ required: true });
      this.form.controls.bankName.markAsTouched();
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);
    const value = this.form.getRawValue();

    this.orderService
      .create({
        quoteId,
        beneficiary: {
          type: value.type,
          fullName: value.fullName,
          identifier: value.identifier,
          bankName: value.bankName || null,
          bankBranch: value.bankBranch || null,
        },
        note: value.note || null,
      })
      .subscribe({
        next: (response) => {
          this.loading.set(false);
          this.router.navigate(['/orders', response.data.id]);
        },
        error: (error) => {
          this.loading.set(false);
          this.errorMessage.set(extractErrorMessage(error));
        },
      });
  }
}
