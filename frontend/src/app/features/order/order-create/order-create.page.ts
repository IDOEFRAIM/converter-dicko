import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { OrderService } from '../../../core/services/order.service';
import { SupplierService } from '../../../core/services/supplier.service';
import { extractErrorMessage } from '../../../core/services/api-error.util';
import { IdempotencyAttempt } from '../../../core/services/idempotency.util';
import {
  BeneficiaryType,
  BENEFICIARY_IDENTIFIER_HINTS,
  BENEFICIARY_IDENTIFIER_LABELS,
  BENEFICIARY_TYPE_OPTIONS,
  CreateOrderRequest,
} from '../../../core/models/order.model';
import { Purpose, PURPOSE_OPTIONS } from '../../../core/models/common.model';
import { SupplierSummary } from '../../../core/models/supplier.model';

type BeneficiarySource = 'SUPPLIER' | 'MANUAL';

/**
 * Choix du beneficiaire pour un ordre : un fournisseur deja enregistre (le backend copie
 * ses coordonnees dans un snapshot immuable), ou une saisie ponctuelle. Une cle d'idempotence
 * est generee pour la tentative et conservee tant qu'elle n'a pas abouti.
 */
@Component({
  selector: 'app-order-create-page',
  standalone: true,
  imports: [
    RouterLink,
    ReactiveFormsModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatFormFieldModule,
    MatIconModule,
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
  private readonly supplierService = inject(SupplierService);

  readonly quoteId = signal<string | null>(null);
  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly suppliers = signal<SupplierSummary[]>([]);
  /** null tant que non vérifié ; false = liquidité CNY actuellement insuffisante pour ce devis. */
  readonly liquiditySufficient = signal<boolean | null>(null);

  readonly beneficiaryTypes = BENEFICIARY_TYPE_OPTIONS;
  readonly purposeOptions = PURPOSE_OPTIONS;

  readonly hasSuppliers = computed(() => this.suppliers().length > 0);

  private readonly idempotency = new IdempotencyAttempt();

  readonly form = new FormGroup({
    source: new FormControl<BeneficiarySource>('MANUAL', { nonNullable: true }),
    supplierId: new FormControl<string>(''),
    type: new FormControl<BeneficiaryType>('ALIPAY', { nonNullable: true }),
    fullName: new FormControl('', { nonNullable: true }),
    identifier: new FormControl('', { nonNullable: true }),
    bankName: new FormControl(''),
    bankBranch: new FormControl(''),
    purpose: new FormControl<string>(''),
    purposeDetails: new FormControl(''),
    note: new FormControl(''),
  });

  get useSupplier(): boolean {
    return this.form.controls.source.value === 'SUPPLIER';
  }

  get isBankAccount(): boolean {
    return this.form.controls.type.value === 'CHINESE_BANK_ACCOUNT';
  }

  get identifierLabel(): string {
    return BENEFICIARY_IDENTIFIER_LABELS[this.form.controls.type.value as BeneficiaryType];
  }

  get identifierHint(): string | null {
    return BENEFICIARY_IDENTIFIER_HINTS[this.form.controls.type.value as BeneficiaryType] ?? null;
  }

  ngOnInit(): void {
    const quoteId = this.route.snapshot.queryParamMap.get('quoteId');
    if (!quoteId) {
      this.errorMessage.set('Aucun devis a associer. Recommencez depuis un nouveau devis.');
      return;
    }
    this.quoteId.set(quoteId);

    // Vérifie la faisabilité AVANT que l'utilisateur ne saisisse le bénéficiaire :
    // le devis est-il utilisable et la liquidité CNY couvre-t-elle son montant ?
    this.orderService.checkFeasibility(quoteId).subscribe({
      next: (response) => this.liquiditySufficient.set(response.data.sufficientLiquidity),
      error: (error) => this.errorMessage.set(extractErrorMessage(error)),
    });

    this.supplierService.list(0, 100, 'ACTIVE').subscribe({
      next: (response) => {
        this.suppliers.set(response.data.content);
        // Si le client a deja des fournisseurs, on le lui propose par defaut.
        if (response.data.content.length > 0) {
          this.form.controls.source.setValue('SUPPLIER');
        }
      },
      error: () => undefined,
    });
  }

  submit(): void {
    const quoteId = this.quoteId();
    if (!quoteId || this.loading()) {
      return;
    }

    if (!this.validate()) {
      return;
    }

    const v = this.form.getRawValue();
    const purpose = (v.purpose || null) as Purpose | null;
    const purposeDetails = v.purposeDetails?.trim() || null;
    const note = v.note?.trim() || null;

    const request: CreateOrderRequest = this.useSupplier
      ? { quoteId, beneficiary: null, supplierId: v.supplierId || null, purpose, purposeDetails, note }
      : {
          quoteId,
          beneficiary: {
            type: v.type,
            fullName: v.fullName.trim(),
            identifier: v.identifier.trim(),
            bankName: v.bankName?.trim() || null,
            bankBranch: v.bankBranch?.trim() || null,
          },
          purpose,
          purposeDetails,
          note,
        };

    // Meme requete rejouee apres un echec => meme cle ; requete modifiee => nouvelle cle.
    const idempotencyKey = this.idempotency.keyFor(request);

    this.loading.set(true);
    this.errorMessage.set(null);
    this.orderService.create(request, idempotencyKey).subscribe({
      next: (response) => {
        this.loading.set(false);
        this.idempotency.complete();
        this.router.navigate(['/orders', response.data.id]);
      },
      error: (error) => {
        this.loading.set(false);
        this.errorMessage.set(extractErrorMessage(error));
      },
    });
  }

  private validate(): boolean {
    const c = this.form.controls;
    if (this.useSupplier) {
      if (!c.supplierId.value) {
        c.supplierId.setErrors({ required: true });
        c.supplierId.markAsTouched();
        return false;
      }
      return true;
    }

    let ok = true;
    if (!c.fullName.value.trim()) {
      c.fullName.setErrors({ required: true });
      c.fullName.markAsTouched();
      ok = false;
    }
    if (!c.identifier.value.trim()) {
      c.identifier.setErrors({ required: true });
      c.identifier.markAsTouched();
      ok = false;
    }
    if (this.isBankAccount && !c.bankName.value) {
      c.bankName.setErrors({ required: true });
      c.bankName.markAsTouched();
      ok = false;
    }
    return ok;
  }
}
