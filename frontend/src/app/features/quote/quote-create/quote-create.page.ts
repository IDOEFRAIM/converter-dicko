import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { QuoteService } from '../../../core/services/quote.service';
import { QuoteDirection } from '../../../core/models/quote.model';
import { extractErrorMessage } from '../../../core/services/api-error.util';
import { CorridorComponent } from '../../../shared/components/corridor/corridor.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';

/**
 * Point d'entree du parcours principal : "J'envoie X XOF" ou "Je veux
 * que le beneficiaire recoive Y CNY". Le frontend transmet
 * l'intention telle quelle — tout calcul (taux, frais, montant final)
 * est fait par le backend (voir QuoteService), jamais ici.
 */
@Component({
  selector: 'app-quote-create-page',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatButtonToggleModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    CorridorComponent,
    PageHeaderComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './quote-create.page.html',
  styleUrl: './quote-create.page.scss',
})
export class QuoteCreatePage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly quoteService = inject(QuoteService);
  private readonly router = inject(Router);

  readonly direction = signal<QuoteDirection>('SEND_XOF');
  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  /** Non nul quand cette page a ete ouverte depuis une Ruee collective (mission "differenciation
   * marketing", Lot 3) : l'ordre cree en bout de parcours y contribuera (voir order-create.page.ts). */
  readonly poolId = signal<string | null>(null);

  ngOnInit(): void {
    this.poolId.set(this.route.snapshot.queryParamMap.get('poolId'));
  }

  readonly amountControl = new FormControl<number | null>(null, {
    validators: [Validators.required, Validators.min(1)],
  });
  readonly form = new FormGroup({ amount: this.amountControl });

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
          const poolId = this.poolId();
          if (poolId) {
            this.router.navigate(['/quote', response.data.id], { queryParams: { poolId } });
          } else {
            this.router.navigate(['/quote', response.data.id]);
          }
        },
        error: (error) => {
          this.loading.set(false);
          this.errorMessage.set(extractErrorMessage(error));
        },
      });
  }
}
