import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { SupplierService } from '../../../core/services/supplier.service';
import { NotificationService } from '../../../core/services/notification.service';
import { extractErrorMessage } from '../../../core/services/api-error.util';
import { SupplierDetail, SupplierRequest } from '../../../core/models/supplier.model';
import { BeneficiaryType, BENEFICIARY_TYPE_OPTIONS } from '../../../core/models/order.model';
import { Currency, PURPOSE_OPTIONS } from '../../../core/models/common.model';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';

/**
 * Creation et modification d'un fournisseur (meme formulaire). La modification n'affecte
 * jamais un ordre deja cree a partir de ce fournisseur : le backend en fige un snapshot
 * immuable a la creation de l'ordre.
 */
@Component({
  selector: 'app-supplier-form-page',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    PageHeaderComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './supplier-form.page.html',
})
export class SupplierFormPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly supplierService = inject(SupplierService);
  private readonly notification = inject(NotificationService);

  readonly typeOptions = BENEFICIARY_TYPE_OPTIONS;
  readonly purposeOptions = PURPOSE_OPTIONS;
  readonly currencyOptions: Currency[] = ['CNY', 'XOF'];

  readonly supplierId = signal<string | null>(null);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly isEdit = computed(() => this.supplierId() !== null);
  readonly title = computed(() => (this.isEdit() ? 'Modifier le fournisseur' : 'Nouveau fournisseur'));

  readonly form = new FormGroup({
    type: new FormControl<BeneficiaryType>('ALIPAY', {
      nonNullable: true,
      validators: [Validators.required],
    }),
    displayName: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    accountNumber: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    accountName: new FormControl(''),
    legalName: new FormControl(''),
    phone: new FormControl(''),
    email: new FormControl('', { validators: [Validators.email] }),
    country: new FormControl(''),
    city: new FormControl(''),
    province: new FormControl(''),
    bankName: new FormControl(''),
    bankBranch: new FormControl(''),
    bankAddress: new FormControl(''),
    swiftCode: new FormControl(''),
    currency: new FormControl<Currency>('CNY', { nonNullable: true, validators: [Validators.required] }),
    purpose: new FormControl<string>(''),
    notes: new FormControl(''),
  });

  get isBankAccount(): boolean {
    return this.form.controls.type.value === 'CHINESE_BANK_ACCOUNT';
  }

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.supplierId.set(id);
      this.loadExisting(id);
    }
  }

  private loadExisting(id: string): void {
    this.loading.set(true);
    this.supplierService.get(id).subscribe({
      next: (response) => {
        this.patchFrom(response.data);
        this.loading.set(false);
      },
      error: (error) => {
        this.errorMessage.set(extractErrorMessage(error));
        this.loading.set(false);
      },
    });
  }

  private patchFrom(supplier: SupplierDetail): void {
    this.form.patchValue({
      type: supplier.type,
      displayName: supplier.displayName,
      accountNumber: supplier.accountNumber,
      accountName: supplier.accountName ?? '',
      legalName: supplier.legalName ?? '',
      phone: supplier.phone ?? '',
      email: supplier.email ?? '',
      country: supplier.country ?? '',
      city: supplier.city ?? '',
      province: supplier.province ?? '',
      bankName: supplier.bankName ?? '',
      bankBranch: supplier.bankBranch ?? '',
      bankAddress: supplier.bankAddress ?? '',
      swiftCode: supplier.swiftCode ?? '',
      currency: supplier.currency,
      purpose: supplier.purpose ?? '',
      notes: supplier.notes ?? '',
    });
  }

  submit(): void {
    if (this.saving() || this.loading()) {
      return;
    }
    if (this.isBankAccount && !this.form.controls.bankName.value) {
      this.form.controls.bankName.setErrors({ required: true });
      this.form.controls.bankName.markAsTouched();
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const v = this.form.getRawValue();
    const payload: SupplierRequest = {
      type: v.type,
      displayName: v.displayName.trim(),
      accountNumber: v.accountNumber.trim(),
      accountName: v.accountName?.trim() || null,
      legalName: v.legalName?.trim() || null,
      phone: v.phone?.trim() || null,
      email: v.email?.trim() || null,
      country: v.country?.trim() || null,
      city: v.city?.trim() || null,
      province: v.province?.trim() || null,
      bankName: v.bankName?.trim() || null,
      bankBranch: v.bankBranch?.trim() || null,
      bankAddress: v.bankAddress?.trim() || null,
      swiftCode: v.swiftCode?.trim() || null,
      currency: v.currency,
      purpose: (v.purpose || null) as SupplierRequest['purpose'],
      notes: v.notes?.trim() || null,
    };

    this.saving.set(true);
    this.errorMessage.set(null);
    const id = this.supplierId();
    const request$ = id
      ? this.supplierService.update(id, payload)
      : this.supplierService.create(payload);

    request$.subscribe({
      next: (response) => {
        this.saving.set(false);
        this.notification.success(id ? 'Fournisseur mis a jour.' : 'Fournisseur enregistre.');
        this.router.navigate(['/suppliers', response.data.id]);
      },
      error: (error) => {
        this.saving.set(false);
        this.errorMessage.set(extractErrorMessage(error));
      },
    });
  }

  cancel(): void {
    const id = this.supplierId();
    this.router.navigate(id ? ['/suppliers', id] : ['/suppliers']);
  }
}
