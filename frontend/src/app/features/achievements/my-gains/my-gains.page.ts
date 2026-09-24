import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AchievementService } from '../../../core/services/achievement.service';
import { OrderService } from '../../../core/services/order.service';
import { extractErrorMessage } from '../../../core/services/api-error.util';
import { AchievementSummary, achievementHasBadge, achievementTierProgress } from '../../../core/models/achievement.model';
import { OrderHistoryEntry } from '../../../core/models/order.model';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';
import { StatusBadgeComponent } from '../../../shared/components/status-badge/status-badge.component';
import { MoneyPipe } from '../../../shared/pipes/money.pipe';

/**
 * "Mes gains" (mission "differenciation marketing", Lot 2) : un compteur sobre pour PRO, un
 * badge + XP pour STUDENT_MALE/FEMALE — memes donnees reelles (volume transfere, transferts
 * termines) que mobile. Le "Livre memoire" (selfies locaux) et la console PRO (objectif mensuel
 * local, export PDF) restent des embellissements mobile-only (stockage local d'appareil, sans
 * contrepartie serveur) — hors scope ici, qui reprend le coeur de donnees partage par les deux
 * plateformes.
 */
@Component({
  selector: 'app-my-gains-page',
  standalone: true,
  imports: [DatePipe, RouterLink, MatIconModule, MatProgressSpinnerModule, EmptyStateComponent, StatusBadgeComponent, MoneyPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './my-gains.page.html',
  styleUrl: './my-gains.page.scss',
})
export class MyGainsPage implements OnInit {
  private readonly achievementService = inject(AchievementService);
  private readonly orderService = inject(OrderService);

  readonly loadingSummary = signal(true);
  readonly summaryErrorMessage = signal<string | null>(null);
  readonly summary = signal<AchievementSummary | null>(null);

  readonly loadingHistory = signal(true);
  readonly historyErrorMessage = signal<string | null>(null);
  readonly completedTransfers = signal<OrderHistoryEntry[]>([]);

  readonly isPro = computed(() => this.summary()?.experienceProfile === 'PRO');
  readonly hasBadge = computed(() => {
    const summary = this.summary();
    return summary !== null && achievementHasBadge(summary);
  });
  readonly tierProgress = computed(() => {
    const summary = this.summary();
    return summary ? achievementTierProgress(summary) : null;
  });

  ngOnInit(): void {
    this.loadSummary();
    this.loadHistory();
  }

  private loadSummary(): void {
    this.loadingSummary.set(true);
    this.achievementService.summary().subscribe({
      next: (response) => {
        this.summary.set(response.data);
        this.loadingSummary.set(false);
      },
      error: (error) => {
        this.summaryErrorMessage.set(extractErrorMessage(error));
        this.loadingSummary.set(false);
      },
    });
  }

  private loadHistory(): void {
    this.loadingHistory.set(true);
    this.orderService.history({ status: 'COMPLETED', size: 20 }).subscribe({
      next: (response) => {
        this.completedTransfers.set(response.data.content);
        this.loadingHistory.set(false);
      },
      error: (error) => {
        this.historyErrorMessage.set(extractErrorMessage(error));
        this.loadingHistory.set(false);
      },
    });
  }
}
