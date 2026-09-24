import { AppBarActionsDirective } from '../../../shared/directives/app-bar-actions.directive';
import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Quote, QuoteDirection } from '../../../core/models/quote.model';
import { extractErrorMessage } from '../../../core/services/api-error.util';
import { QuoteService } from '../../../core/services/quote.service';
import { CorridorComponent } from '../../../shared/components/corridor/corridor.component';
import { TicketRow, TransferTicketComponent } from '../../../shared/components/transfer-ticket/transfer-ticket.component';
import { MoneyPipe } from '../../../shared/pipes/money.pipe';

/**
 * "Payer un fournisseur" — copie de `QuoteCreatePage` (mobile) : corridor, "VOUS ENVOYEZ"
 * + montant en grand, puis le ticket du devis directement sur le meme ecran, "Continuer"
 * (accepte le devis) vers le beneficiaire. Le frontend transmet l'intention telle quelle :
 * taux, frais et montant recu viennent exclusivement du backend.
 *
 * Route `/pay/pools/:id/checkout` : meme ecran, contribution a une Ruee (poolId transmis
 * a la creation de l'ordre).
 */
@Component({
  selector: 'app-quote-create-page',
  standalone: true,
  imports: [
    DatePipe,
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    CorridorComponent,
    TransferTicketComponent,
    AppBarActionsDirective,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './quote-create.page.html',
  styleUrl: './quote-create.page.scss',
})
export class QuoteCreatePage {
  private readonly quoteService = inject(QuoteService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly money = new MoneyPipe();

  /** Present uniquement sur `/pay/pools/:id/checkout`. */
  readonly poolId = this.route.snapshot.paramMap.get('id');

  readonly direction = signal<QuoteDirection>('SEND_XOF');
  readonly loading = signal(false);
  readonly accepting = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly quote = signal<Quote | null>(null);

  readonly amountControl = new FormControl<number | null>(null, {
    validators: [Validators.required, Validators.min(1)],
  });

  readonly isExpired = computed(() => {
    const q = this.quote();
    return !!q && (q.status === 'EXPIRED' || new Date(q.expiresAt).getTime() <= Date.now());
  });

  readonly ticketRows = computed<TicketRow[]>(() => {
    const q = this.quote();
    return q
      ? [
          { label: 'Taux', value: `1 CNY = ${q.customerRate} XOF` },
          { label: 'Frais', value: this.money.transform(q.feeXof, 'XOF') },
        ]
      : [];
  });

  setDirection(direction: QuoteDirection): void {
    this.direction.set(direction);
    this.amountControl.reset();
    this.errorMessage.set(null);
  }

  submit(): void {
    if (this.amountControl.invalid || this.loading()) {
      this.amountControl.markAsTouched();
      return;
    }
    this.loading.set(true);
    this.errorMessage.set(null);

    const amount = String(this.amountControl.value);
    this.quoteService
      .create({
        direction: this.direction(),
        amountXof: this.direction() === 'SEND_XOF' ? amount : null,
        amountCny: this.direction() === 'RECEIVE_CNY' ? amount : null,
      })
      .subscribe({
        next: (response) => {
          this.loading.set(false);
          this.quote.set(response.data);
        },
        error: (error) => {
          this.loading.set(false);
          this.errorMessage.set(extractErrorMessage(error));
        },
      });
  }

  continueToOrder(): void {
    const quote = this.quote();
    if (!quote || this.accepting()) {
      return;
    }
    this.accepting.set(true);
    this.errorMessage.set(null);
    this.quoteService.accept(quote.id).subscribe({
      next: (response) => {
        this.accepting.set(false);
        const queryParams: Record<string, string> = { quoteId: response.data.id };
        if (this.poolId) {
          queryParams['poolId'] = this.poolId;
        }
        this.router.navigate(['/order/new'], { queryParams });
      },
      error: (error) => {
        this.accepting.set(false);
        this.errorMessage.set(extractErrorMessage(error));
      },
    });
  }

  reset(): void {
    this.quote.set(null);
    this.errorMessage.set(null);
  }
}
