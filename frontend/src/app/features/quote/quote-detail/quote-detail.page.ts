import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { QuoteService } from '../../../core/services/quote.service';
import { NotificationService } from '../../../core/services/notification.service';
import { extractErrorMessage } from '../../../core/services/api-error.util';
import { Quote } from '../../../core/models/quote.model';
import { CountdownTimerComponent } from '../../../shared/components/countdown-timer/countdown-timer.component';
import { StatusBadgeComponent } from '../../../shared/components/status-badge/status-badge.component';
import { MoneyPipe } from '../../../shared/pipes/money.pipe';

/**
 * Affiche le resultat calcule par le backend (RateEngine) tel quel :
 * customerRate, frais, montant final. Aucune valeur n'est recalculee
 * ni reproduite cote frontend.
 */
@Component({
  selector: 'app-quote-detail-page',
  standalone: true,
  imports: [
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatProgressSpinnerModule,
    CountdownTimerComponent,
    StatusBadgeComponent,
    MoneyPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './quote-detail.page.html',
  styleUrl: './quote-detail.page.scss',
})
export class QuoteDetailPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly quoteService = inject(QuoteService);
  private readonly notification = inject(NotificationService);

  readonly quote = signal<Quote | null>(null);
  readonly loading = signal(true);
  readonly actionInProgress = signal(false);
  readonly errorMessage = signal<string | null>(null);
  private poolId: string | null = null;

  ngOnInit(): void {
    this.poolId = this.route.snapshot.queryParamMap.get('poolId');
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) {
      this.errorMessage.set('Devis introuvable.');
      this.loading.set(false);
      return;
    }
    this.load(id);
  }

  private load(id: string): void {
    this.loading.set(true);
    this.quoteService.get(id).subscribe({
      next: (response) => {
        this.quote.set(response.data);
        this.loading.set(false);
      },
      error: (error) => {
        this.errorMessage.set(extractErrorMessage(error));
        this.loading.set(false);
      },
    });
  }

  accept(): void {
    const quote = this.quote();
    if (!quote || this.actionInProgress()) {
      return;
    }
    this.actionInProgress.set(true);
    this.quoteService.accept(quote.id).subscribe({
      next: (response) => {
        this.actionInProgress.set(false);
        this.notification.success('Devis accepte.');
        const queryParams: Record<string, string> = { quoteId: response.data.id };
        if (this.poolId) {
          queryParams['poolId'] = this.poolId;
        }
        this.router.navigate(['/order/new'], { queryParams });
      },
      error: (error) => {
        this.actionInProgress.set(false);
        this.notification.error(extractErrorMessage(error));
      },
    });
  }

  cancel(): void {
    const quote = this.quote();
    if (!quote || this.actionInProgress()) {
      return;
    }
    this.actionInProgress.set(true);
    this.quoteService.cancel(quote.id).subscribe({
      next: (response) => {
        this.actionInProgress.set(false);
        this.quote.set(response.data);
        this.notification.success('Devis annule.');
      },
      error: (error) => {
        this.actionInProgress.set(false);
        this.notification.error(extractErrorMessage(error));
      },
    });
  }

  onExpired(): void {
    const quote = this.quote();
    if (quote && quote.status === 'ACTIVE') {
      // Rafraichit pour refleter EXPIRED tel que le backend le determine
      // (source de verite unique sur l'expiration).
      this.load(quote.id);
    }
  }
}
