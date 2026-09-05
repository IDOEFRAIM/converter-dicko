import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { OrderService } from '../../../core/services/order.service';
import { PaymentService } from '../../../core/services/payment.service';
import { SettingsService } from '../../../core/services/settings.service';
import { extractErrorMessage } from '../../../core/services/api-error.util';
import { IdempotencyAttempt } from '../../../core/services/idempotency.util';
import { OrderDetail } from '../../../core/models/order.model';
import { Payment, PaymentMethod } from '../../../core/models/payment.model';
import { MoneyPipe } from '../../../shared/pipes/money.pipe';

const METHOD_LABELS: Record<PaymentMethod, string> = {
  MOBILE_MONEY: 'Mobile Money',
  WAVE: 'Wave',
  BANK_TRANSFER: 'Virement bancaire',
};

/**
 * Soumission du paiement puis, immediatement, televersement de la
 * preuve — dans la meme page/session. Le backend n'expose pas
 * d'endpoint pour retrouver le paiement d'un ordre plus tard (seule la
 * reponse de soumission donne l'identifiant du paiement) : ce flux en
 * une seule visite garantit que la preuve peut toujours etre ajoutee.
 */
@Component({
  selector: 'app-payment-submit-page',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatProgressSpinnerModule,
    MoneyPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './payment-submit.page.html',
  styleUrl: './payment-submit.page.scss',
})
export class PaymentSubmitPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly orderService = inject(OrderService);
  private readonly paymentService = inject(PaymentService);
  private readonly settingsService = inject(SettingsService);

  readonly order = signal<OrderDetail | null>(null);
  readonly payment = signal<Payment | null>(null);
  readonly loading = signal(true);
  readonly submitting = signal(false);
  readonly uploading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly proofUploaded = signal(false);
  readonly methods = signal<{ value: PaymentMethod; label: string }[]>([]);

  private readonly idempotency = new IdempotencyAttempt();

  readonly form = new FormGroup({
    method: new FormControl<PaymentMethod>('MOBILE_MONEY', { nonNullable: true, validators: [Validators.required] }),
    receivedAmountXof: new FormControl<number | null>(null, { validators: [Validators.required, Validators.min(1)] }),
    transactionReference: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    payerPhone: new FormControl(''),
  });

  ngOnInit(): void {
    const orderId = this.route.snapshot.paramMap.get('id');
    if (!orderId) {
      this.errorMessage.set('Ordre introuvable.');
      this.loading.set(false);
      return;
    }

    this.orderService.get(orderId).subscribe({
      next: (response) => {
        this.order.set(response.data);
        this.form.controls.receivedAmountXof.setValue(Number(response.data.amountXof));
        this.loading.set(false);
      },
      error: (error) => {
        this.errorMessage.set(extractErrorMessage(error));
        this.loading.set(false);
      },
    });

    this.settingsService.getPublicSettings().subscribe((response) => {
      this.methods.set(
        response.data.enabledPaymentMethods.map((value) => ({
          value: value as PaymentMethod,
          label: METHOD_LABELS[value as PaymentMethod] ?? value,
        })),
      );
    });
  }

  submitPayment(): void {
    const order = this.order();
    if (!order || this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const request = {
      method: value.method,
      receivedAmountXof: String(value.receivedAmountXof),
      transactionReference: value.transactionReference,
      payerPhone: value.payerPhone || null,
    };
    // Ex. apres un 409 DUPLICATE_TRANSACTION_REFERENCE : l'utilisateur corrige la reference,
    // le corps change => nouvelle cle, pas de 409 IDEMPOTENCY_KEY_REUSED parasite.
    const idempotencyKey = this.idempotency.keyFor({ orderId: order.id, ...request });

    this.submitting.set(true);
    this.errorMessage.set(null);

    this.paymentService
      .submit(order.id, request, idempotencyKey)
      .subscribe({
        next: (response) => {
          this.submitting.set(false);
          this.idempotency.complete();
          this.payment.set(response.data);
        },
        error: (error) => {
          this.submitting.set(false);
          // Panne reseau (statut 0) : l'issue reelle cote serveur est inconnue -- la cle
          // d'idempotence est CONSERVEE (voir IdempotencyAttempt.keyFor, non appelee ici) donc
          // un nouveau clic avec le meme formulaire rejoue exactement la meme tentative, sans
          // risque de double declaration. Jamais annoncer un echec certain dans ce cas precis.
          if (error instanceof HttpErrorResponse && error.status === 0) {
            this.errorMessage.set(
              'Impossible de confirmer la reponse du serveur. Vous pouvez reessayer en toute securite.',
            );
          } else {
            this.errorMessage.set(extractErrorMessage(error));
          }
        },
      });
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    const payment = this.payment();
    if (!file || !payment || this.uploading()) {
      return;
    }
    this.uploading.set(true);
    this.errorMessage.set(null);

    this.paymentService.uploadProof(payment.id, file).subscribe({
      next: () => {
        this.uploading.set(false);
        this.proofUploaded.set(true);
      },
      error: (error) => {
        this.uploading.set(false);
        this.errorMessage.set(extractErrorMessage(error));
      },
    });
  }

  goToOrder(): void {
    const order = this.order();
    if (order) {
      this.router.navigate(['/orders', order.id]);
    }
  }
}
