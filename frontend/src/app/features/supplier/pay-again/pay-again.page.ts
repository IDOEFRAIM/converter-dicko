import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { SupplierService } from '../../../core/services/supplier.service';
import { extractErrorMessage } from '../../../core/services/api-error.util';
import { IdempotencyAttempt } from '../../../core/services/idempotency.util';
import { SupplierDetail } from '../../../core/models/supplier.model';
import { Purpose, PURPOSE_LABELS, PURPOSE_OPTIONS } from '../../../core/models/common.model';

/**
 * "Payer a nouveau" un fournisseur enregistre. Le montant est TOUJOURS saisi ici :
 * jamais repris d'un ordre precedent. Le backend cree un nouveau devis (pricing courant)
 * puis un nouvel ordre — aucun taux/frais/montant CNY d'une transaction passee n'est reutilise.
 * Une cle d'idempotence est generee pour cette tentative et conservee tant qu'elle n'a pas abouti.
 */
@Component({
  selector: 'app-pay-again-page',
  standalone: true,
  imports: [
    RouterLink,
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './pay-again.page.html',
})
export class PayAgainPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly supplierService = inject(SupplierService);

  readonly purposeOptions = PURPOSE_OPTIONS;
  readonly purposeLabels = PURPOSE_LABELS;

  readonly supplier = signal<SupplierDetail | null>(null);
  readonly loading = signal(true);
  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);

  private readonly idempotency = new IdempotencyAttempt();

  readonly form = new FormGroup({
    amountXof: new FormControl<number | null>(null, {
      validators: [Validators.required, Validators.min(1)],
    }),
    purpose: new FormControl<string>(''),
    purposeDetails: new FormControl(''),
  });

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) {
      this.errorMessage.set('Fournisseur introuvable.');
      this.loading.set(false);
      return;
    }
    this.supplierService.get(id).subscribe({
      next: (response) => {
        this.supplier.set(response.data);
        if (response.data.purpose) {
          this.form.controls.purpose.setValue(response.data.purpose);
        }
        this.loading.set(false);
      },
      error: (error) => {
        this.errorMessage.set(extractErrorMessage(error));
        this.loading.set(false);
      },
    });
  }

  submit(): void {
    const supplier = this.supplier();
    if (!supplier || this.submitting()) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const v = this.form.getRawValue();
    const request = {
      amountXof: String(v.amountXof),
      purpose: (v.purpose || null) as Purpose | null,
      purposeDetails: v.purposeDetails?.trim() || null,
    };
    // Meme montant/motif rejoue apres un echec => meme cle ; montant modifie => nouvelle cle.
    const idempotencyKey = this.idempotency.keyFor({ supplierId: supplier.id, ...request });

    this.submitting.set(true);
    this.errorMessage.set(null);

    this.supplierService.payAgain(supplier.id, request, idempotencyKey).subscribe({
      next: (response) => {
        this.submitting.set(false);
        this.idempotency.complete();
        this.router.navigate(['/orders', response.data.id]);
      },
      error: (error) => {
        this.submitting.set(false);
        // La cle est CONSERVEE tant que la requete est identique : un nouveau clic rejoue la
        // meme tentative sans risque de doublon.
        this.errorMessage.set(extractErrorMessage(error));
      },
    });
  }
}
