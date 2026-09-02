import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from '../../core/services/auth.service';
import { OrderService } from '../../core/services/order.service';
import { OrderSummary } from '../../core/models/order.model';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge.component';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state.component';
import { MoneyPipe } from '../../shared/pipes/money.pipe';

@Component({
  selector: 'app-dashboard-page',
  standalone: true,
  imports: [
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatProgressSpinnerModule,
    StatusBadgeComponent,
    EmptyStateComponent,
    MoneyPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './dashboard.page.html',
  styleUrl: './dashboard.page.scss',
})
export class DashboardPage implements OnInit {
  private readonly authService = inject(AuthService);
  private readonly orderService = inject(OrderService);

  readonly currentUser = this.authService.currentUser;
  readonly recentOrders = signal<OrderSummary[]>([]);
  readonly loading = signal(true);

  ngOnInit(): void {
    this.orderService.list(0, 5).subscribe({
      next: (response) => {
        this.recentOrders.set(response.data.content);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }
}
