import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { WalletService } from '../../core/services/wallet.service';
import { PreferredRateService } from '../../core/services/preferred-rate.service';
import { extractErrorMessage } from '../../core/services/api-error.util';
import { Wallet, WalletTransaction, WalletTransactionType } from '../../core/models/wallet.model';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { MoneyPipe } from '../../shared/pipes/money.pipe';

const TYPE_LABELS: Record<WalletTransactionType, string> = {
  CREDIT: 'Depot',
  DEBIT: 'Debit',
  RESERVE: 'Reservation',
  RELEASE: 'Liberation',
};

/**
 * Solde interne XOF du client. Toutes les valeurs affichees viennent
 * telles quelles du backend -- aucun calcul financier cote frontend.
 */
@Component({
  selector: 'app-wallet-page',
  standalone: true,
  imports: [
    RouterLink,
    DatePipe,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    EmptyStateComponent,
    PageHeaderComponent,
    MoneyPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './wallet.page.html',
  styleUrl: './wallet.page.scss',
})
export class WalletPage implements OnInit {
  private readonly walletService = inject(WalletService);
  private readonly preferredRateService = inject(PreferredRateService);

  readonly wallet = signal<Wallet | null>(null);
  readonly transactions = signal<WalletTransaction[]>([]);
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);

  /**
   * Identifiants de demandes de taux preferentiel dont l'echange est
   * declenche mais pas encore termine : le debit Wallet correspondant
   * (meme referenceId) ne doit pas etre presente comme definitivement
   * clos tant que l'echange est en cours -- voir {@link engagedLabelFor}.
   */
  private readonly engagedReferenceIds = signal<Set<string>>(new Set());

  typeLabel(type: WalletTransactionType): string {
    return TYPE_LABELS[type];
  }

  /** Libelle explicite pour un debit encore engage dans un echange en cours ; sinon le libelle de type standard. */
  engagedLabelFor(tx: WalletTransaction): string | null {
    if (tx.type === 'DEBIT' && tx.referenceId && this.engagedReferenceIds().has(tx.referenceId)) {
      return `${this.moneyAmount(tx.amount)} engages pour cet echange`;
    }
    return null;
  }

  private moneyAmount(amount: string): string {
    const value = Number(amount);
    return Number.isFinite(value) ? new Intl.NumberFormat('fr-FR').format(value) + ' XOF' : amount + ' XOF';
  }

  ngOnInit(): void {
    this.walletService.get().subscribe({
      next: (response) => this.wallet.set(response.data),
      error: (error) => this.errorMessage.set(extractErrorMessage(error)),
    });
    this.walletService.transactions(0, 30).subscribe({
      next: (response) => {
        this.transactions.set(response.data.content);
        this.loading.set(false);
      },
      error: (error) => {
        this.errorMessage.set(extractErrorMessage(error));
        this.loading.set(false);
      },
    });
    this.preferredRateService.list(0, 20).subscribe({
      next: (response) => {
        const engaged = response.data.content
          .filter((r) => r.phase === 'EXCHANGE_IN_PROGRESS')
          .map((r) => r.id);
        this.engagedReferenceIds.set(new Set(engaged));
      },
    });
  }
}
