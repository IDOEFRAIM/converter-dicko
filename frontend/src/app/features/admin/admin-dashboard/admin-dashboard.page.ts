import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';

@Component({
  selector: 'app-admin-dashboard-page',
  standalone: true,
  imports: [RouterLink, MatCardModule, MatIconModule, PageHeaderComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './admin-dashboard.page.html',
  styleUrl: './admin-dashboard.page.scss',
})
export class AdminDashboardPage {
  readonly sections = [
    {
      path: '/admin/cost-rates',
      icon: 'calculate',
      label: 'Taux client (pricing)',
      description: 'Le taux que voit le client + publier les paramètres du jour',
    },
    {
      path: '/admin/rates',
      icon: 'currency_exchange',
      label: 'Taux préférentiel (legacy)',
      description: 'Taux de marché manuel — module taux préférentiel uniquement, sans lien avec le pricing',
    },
    { path: '/admin/orders', icon: 'receipt_long', label: 'Ordres', description: 'Consulter tous les ordres' },
    { path: '/admin/payments', icon: 'payments', label: 'Paiements', description: 'Verifier les paiements soumis' },
    { path: '/admin/settlements', icon: 'send', label: 'Reglements', description: 'Executer les reglements CNY' },
    { path: '/admin/treasury', icon: 'account_balance', label: 'Tresorerie', description: 'Soldes XOF / CNY' },
  ];
}
