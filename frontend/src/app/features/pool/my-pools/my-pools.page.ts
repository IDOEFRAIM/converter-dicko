import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { PoolService } from '../../../core/services/pool.service';
import { extractErrorMessage } from '../../../core/services/api-error.util';
import { Pool, poolIsActive, poolProgress } from '../../../core/models/pool.model';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';
import { StatusBadgeComponent } from '../../../shared/components/status-badge/status-badge.component';
import { MoneyPipe } from '../../../shared/pipes/money.pipe';

/** "Mes Ruees" (mission "differenciation marketing", Lot 3) : creees ou rejointes, actives puis terminees. */
@Component({
  selector: 'app-my-pools-page',
  standalone: true,
  imports: [
    RouterLink,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    PageHeaderComponent,
    EmptyStateComponent,
    StatusBadgeComponent,
    MoneyPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './my-pools.page.html',
  styleUrl: './my-pools.page.scss',
})
export class MyPoolsPage implements OnInit {
  private readonly poolService = inject(PoolService);

  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly pools = signal<Pool[]>([]);

  readonly active = computed(() => this.pools().filter((p) => poolIsActive(p)));
  readonly closed = computed(() => this.pools().filter((p) => !poolIsActive(p)));

  readonly progress = poolProgress;

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.poolService.mine(0, 50).subscribe({
      next: (response) => {
        this.pools.set(response.data.content);
        this.loading.set(false);
      },
      error: (error) => {
        this.errorMessage.set(extractErrorMessage(error));
        this.loading.set(false);
      },
    });
  }
}
