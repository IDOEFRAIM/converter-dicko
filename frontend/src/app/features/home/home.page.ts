import { DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatBadgeModule } from '@angular/material/badge';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AchievementSummary } from '../../core/models/achievement.model';
import { BENEFICIARY_TYPE_LABELS, OrderHistoryEntry } from '../../core/models/order.model';
import { PublicRateHistoryEntry } from '../../core/models/rate-history.model';
import { SupplierSummary } from '../../core/models/supplier.model';
import { AchievementService } from '../../core/services/achievement.service';
import { AuthService } from '../../core/services/auth.service';
import { InboxNotificationService } from '../../core/services/inbox-notification.service';
import { OrderService } from '../../core/services/order.service';
import { RateHistoryService } from '../../core/services/rate-history.service';
import { SupplierService } from '../../core/services/supplier.service';
import { ExperienceCopy } from '../../core/theme/experience-copy';
import { CorridorHeroComponent } from '../../shared/components/corridor-hero/corridor-hero.component';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge.component';
import { MoneyPipe } from '../../shared/pipes/money.pipe';

type LoadState = 'loading' | 'error' | 'data';

/**
 * Accueil — copie conforme de `HomePage` (mobile/lib/features/home/presentation/home_page.dart) :
 * salutation + cloche, rappel KYC, corridor hero 🇧🇫 -> 🇨🇳, raccourcis en pastilles,
 * taux du moment, "Mes gains", derniere operation, fournisseurs. Uniquement des donnees
 * reelles de l'API — aucune valeur fictive.
 */
@Component({
  selector: 'app-home-page',
  standalone: true,
  imports: [
    RouterLink,
    DatePipe,
    MatBadgeModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    CorridorHeroComponent,
    StatusBadgeComponent,
    MoneyPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './home.page.html',
  styleUrl: './home.page.scss',
})
export class HomePage implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly rateHistoryService = inject(RateHistoryService);
  private readonly orderService = inject(OrderService);
  private readonly supplierService = inject(SupplierService);
  private readonly achievementService = inject(AchievementService);
  private readonly notificationService = inject(InboxNotificationService);

  readonly user = this.auth.currentUser;
  readonly profile = this.auth.experienceProfile;
  readonly typeLabels = BENEFICIARY_TYPE_LABELS;

  readonly greeting = computed(() => {
    const user = this.user();
    const profile = this.profile();
    const emoji = ExperienceCopy.greetingEmoji(profile);
    return user
      ? `${ExperienceCopy.greeting(profile, user.firstName)} ${emoji}`
      : `Bonjour ${emoji}`;
  });
  readonly rateEyebrow = computed(() => ExperienceCopy.homeRateEyebrow(this.profile()));
  readonly showKycBanner = computed(() => this.user()?.kycVerified === false);

  readonly rateState = signal<LoadState>('loading');
  readonly rate = signal<PublicRateHistoryEntry | null>(null);
  readonly lastOperationState = signal<LoadState>('loading');
  readonly lastOperation = signal<OrderHistoryEntry | null>(null);
  readonly suppliersState = signal<LoadState>('loading');
  readonly suppliers = signal<SupplierSummary[]>([]);
  readonly achievementsState = signal<LoadState>('loading');
  readonly achievements = signal<AchievementSummary | null>(null);
  readonly unreadCount = signal(0);

  ngOnInit(): void {
    this.loadRate();
    this.loadLastOperation();
    this.loadSuppliers();
    this.achievementService.summary().subscribe({
      next: (r) => {
        this.achievements.set(r.data);
        this.achievementsState.set('data');
      },
      // Purement indicatif : un echec ne bloque jamais le reste de l'accueil.
      error: () => this.achievementsState.set('error'),
    });
    this.notificationService.unreadCount().subscribe({
      next: (r) => this.unreadCount.set(r.data.unreadCount),
      error: () => undefined,
    });
  }

  loadRate(): void {
    this.rateState.set('loading');
    this.rateHistoryService.history({ size: 1 }).subscribe({
      next: (r) => {
        this.rate.set(r.data.content[0] ?? null);
        this.rateState.set('data');
      },
      error: () => this.rateState.set('error'),
    });
  }

  loadLastOperation(): void {
    this.lastOperationState.set('loading');
    this.orderService.history({ size: 1 }).subscribe({
      next: (r) => {
        this.lastOperation.set(r.data.content[0] ?? null);
        this.lastOperationState.set('data');
      },
      error: () => this.lastOperationState.set('error'),
    });
  }

  loadSuppliers(): void {
    this.suppliersState.set('loading');
    this.supplierService.list(0, 5).subscribe({
      next: (r) => {
        this.suppliers.set(r.data.content);
        this.suppliersState.set('data');
      },
      error: () => this.suppliersState.set('error'),
    });
  }

  initial(name: string): string {
    return name ? name[0].toUpperCase() : '?';
  }
}
