import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Subscription } from 'rxjs';
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
import { BeneficiaryType, BENEFICIARY_IDENTIFIER_LABELS, BENEFICIARY_TYPE_OPTIONS } from '../../../core/models/order.model';
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
export class SupplierFormPage implements OnInit, OnDestroy {
  private typeSubscription?: Subscription;

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

  /** Code QR nouvellement choisi (pas encore televerse). */
  readonly qrCodeFile = signal<File | null>(null);
  readonly qrCodePreviewUrl = signal<string | null>(null);
  readonly existingQrCodeFileName = signal<string | null>(null);

  /**
   * Rempli des qu'un premier essai de {@link submit} cree reellement le fournisseur -- si seul
   * l'envoi du QR echoue ensuite (reseau...), un nouvel essai met a jour ce MEME fournisseur au
   * lieu d'en creer un second en double.
   */
  private createdSupplierId: string | null = null;

  /**
   * Identifiant du devis en attente, transmis quand ce formulaire est ouvert DEPUIS la creation
   * d'un ordre (voir order-create.page.html, lien "+ Enregistrer un nouveau fournisseur"/"Ajouter
   * un fournisseur Alipay/WeChat") -- retour beta-testeur sept. 2026 : "la facon de lui proposer
   * un fournisseur lors de la conversion, c'est tres mal gere". Sans lui, un succes renvoyait
   * TOUJOURS vers la fiche du fournisseur qui vient d'etre cree, un cul-de-sac qui obligeait
   * l'utilisateur a retrouver seul le chemin retour vers son ordre en cours.
   */
  private returnToOrderQuoteId: string | null = null;

  readonly isEdit = computed(() => this.supplierId() !== null);
  readonly title = computed(() => (this.isEdit() ? 'Modifier le fournisseur' : 'Nouveau fournisseur'));

  readonly form = new FormGroup({
    type: new FormControl<BeneficiaryType>('ALIPAY', {
      nonNullable: true,
      validators: [Validators.required],
    }),
    displayName: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    accountNumber: new FormControl(''),
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

  get requiresQrCode(): boolean {
    const type = this.form.controls.type.value;
    return type === 'ALIPAY' || type === 'WECHAT_PAY';
  }

  get identifierLabel(): string {
    return BENEFICIARY_IDENTIFIER_LABELS[this.form.controls.type.value as BeneficiaryType];
  }

  get hasAnyQrCode(): boolean {
    return this.qrCodeFile() !== null || this.existingQrCodeFileName() !== null;
  }

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.supplierId.set(id);
      this.loadExisting(id);
    }
    this.returnToOrderQuoteId = this.route.snapshot.queryParamMap.get('returnToOrder');
    // bankName/accountNumber n'ont aucun Validator statique (obligatoires uniquement pour
    // CHINESE_BANK_ACCOUNT, impose via setErrors() dans submit()) : sans ce nettoyage, changer
    // de type APRES un premier submit() en erreur laisse ces erreurs "required" figees pour
    // toujours sur le FormGroup (jamais reevaluees), qui reste alors invalid en silence -- le
    // code QR d'un Alipay/WeChat Pay ne pouvait plus jamais etre soumis.
    this.typeSubscription = this.form.controls.type.valueChanges.subscribe(() => {
      if (!this.isBankAccount) {
        this.form.controls.bankName.setErrors(null);
        this.form.controls.accountNumber.setErrors(null);
      }
    });
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
    this.existingQrCodeFileName.set(supplier.qrCodeFileName);
    this.form.patchValue({
      type: supplier.type,
      displayName: supplier.displayName,
      accountNumber: supplier.accountNumber ?? '',
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

  /** Un QR est une IMAGE, jamais un texte : selection via un `<input type="file">` masque. */
  onQrCodeFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    if (!file) {
      return;
    }
    this.qrCodeFile.set(file);
    const previousUrl = this.qrCodePreviewUrl();
    if (previousUrl) {
      URL.revokeObjectURL(previousUrl);
    }
    this.qrCodePreviewUrl.set(URL.createObjectURL(file));
    input.value = '';
  }

  submit(): void {
    if (this.saving() || this.loading()) {
      return;
    }
    if (!this.isBankAccount) {
      this.form.controls.bankName.setErrors(null);
      this.form.controls.accountNumber.setErrors(null);
    }
    if (this.isBankAccount && !this.form.controls.bankName.value) {
      this.form.controls.bankName.setErrors({ required: true });
      this.form.controls.bankName.markAsTouched();
    }
    if (this.isBankAccount && !this.form.controls.accountNumber.value) {
      this.form.controls.accountNumber.setErrors({ required: true });
      this.form.controls.accountNumber.markAsTouched();
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    // Le code QR est l'identifiant reel d'un Alipay/WeChat Pay (jamais un texte) : un fournisseur
    // qu'on vient de creer sans QR ne pourrait servir a aucun ordre (Supplier#isReadyForPayment
    // cote backend) -- on l'exige donc a la creation. En edition, un QR deja televerse suffit.
    if (this.requiresQrCode && !this.hasAnyQrCode) {
      this.errorMessage.set(`Le code QR ${this.identifierLabel.replace('Code QR ', '')} est obligatoire.`);
      return;
    }

    const v = this.form.getRawValue();
    const payload: SupplierRequest = {
      type: v.type,
      displayName: v.displayName.trim(),
      accountNumber: v.accountNumber?.trim() || null,
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
    const targetId = this.supplierId() ?? this.createdSupplierId;
    const request$ = targetId
      ? this.supplierService.update(targetId, payload)
      : this.supplierService.create(payload);

    request$.subscribe({
      next: (response) => {
        this.createdSupplierId = response.data.id;
        const qrCodeFile = this.qrCodeFile();
        if (!qrCodeFile) {
          this.saving.set(false);
          this.notification.success(targetId ? 'Fournisseur mis a jour.' : 'Fournisseur enregistre.');
          this.navigateAfterSave(response.data.id);
          return;
        }
        this.supplierService.uploadQrCode(response.data.id, qrCodeFile).subscribe({
          next: () => {
            this.saving.set(false);
            this.notification.success(targetId ? 'Fournisseur mis a jour.' : 'Fournisseur enregistre.');
            this.navigateAfterSave(response.data.id);
          },
          error: (error) => {
            this.saving.set(false);
            this.errorMessage.set(extractErrorMessage(error));
          },
        });
      },
      error: (error) => {
        this.saving.set(false);
        this.errorMessage.set(extractErrorMessage(error));
      },
    });
  }

  cancel(): void {
    if (this.returnToOrderQuoteId) {
      this.router.navigate(['/order/new'], { queryParams: { quoteId: this.returnToOrderQuoteId } });
      return;
    }
    const id = this.supplierId();
    this.router.navigate(id ? ['/suppliers', id] : ['/suppliers']);
  }

  /** Retourne vers l'ordre en cours (si ouvert depuis la creation d'un ordre) plutot que vers la
   * fiche du fournisseur -- voir la Javadoc de {@link returnToOrderQuoteId}. */
  private navigateAfterSave(newSupplierId: string): void {
    if (this.returnToOrderQuoteId) {
      // newSupplierId transmis pour que l'ordre pre-selectionne directement CE fournisseur
      // (voir order-create.page.ts ngOnInit) plutot que de laisser l'utilisateur le re-chercher
      // dans une liste qu'il vient de faire grandir lui-meme.
      this.router.navigate(['/order/new'], {
        queryParams: { quoteId: this.returnToOrderQuoteId, newSupplierId },
      });
      return;
    }
    this.router.navigate(['/suppliers', newSupplierId]);
  }

  ngOnDestroy(): void {
    const url = this.qrCodePreviewUrl();
    if (url) {
      URL.revokeObjectURL(url);
    }
    this.typeSubscription?.unsubscribe();
  }
}
