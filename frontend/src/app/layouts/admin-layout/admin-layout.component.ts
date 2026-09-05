import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-admin-layout',
  standalone: true,
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatToolbarModule,
    MatSidenavModule,
    MatListModule,
    MatButtonModule,
    MatIconModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './admin-layout.component.html',
  styleUrl: './admin-layout.component.scss',
})
export class AdminLayoutComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly currentUser = this.auth.currentUser;

  readonly links = [
    { path: '/admin/cost-rates', icon: 'calculate', label: 'Coût de revient' },
    { path: '/admin/rates', icon: 'currency_exchange', label: 'Taux préférentiel' },
    { path: '/admin/orders', icon: 'receipt_long', label: 'Ordres' },
    { path: '/admin/payments', icon: 'payments', label: 'Paiements' },
    { path: '/admin/settlements', icon: 'send', label: 'Reglements' },
    { path: '/admin/treasury', icon: 'account_balance', label: 'Tresorerie' },
  ];

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
