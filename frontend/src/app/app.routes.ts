import { Routes } from '@angular/router';
import { adminGuard } from './core/guards/admin.guard';
import { authGuard } from './core/guards/auth.guard';
import { guestGuard } from './core/guards/guest.guard';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./layouts/auth-layout/auth-layout.component').then((m) => m.AuthLayoutComponent),
    canActivate: [guestGuard],
    children: [
      { path: 'login', loadComponent: () => import('./features/auth/login/login.page').then((m) => m.LoginPage) },
      {
        path: 'register',
        loadComponent: () => import('./features/auth/register/register.page').then((m) => m.RegisterPage),
      },
    ],
  },
  {
    path: '',
    loadComponent: () =>
      import('./layouts/client-layout/client-layout.component').then((m) => m.ClientLayoutComponent),
    canActivate: [authGuard],
    children: [
      {
        path: 'dashboard',
        loadComponent: () => import('./features/dashboard/dashboard.page').then((m) => m.DashboardPage),
      },
      {
        path: 'quote/new',
        loadComponent: () =>
          import('./features/quote/quote-create/quote-create.page').then((m) => m.QuoteCreatePage),
      },
      {
        path: 'quote/:id',
        loadComponent: () =>
          import('./features/quote/quote-detail/quote-detail.page').then((m) => m.QuoteDetailPage),
      },
      {
        path: 'order/new',
        loadComponent: () =>
          import('./features/order/order-create/order-create.page').then((m) => m.OrderCreatePage),
      },
      {
        path: 'orders/:id',
        loadComponent: () =>
          import('./features/order/order-detail/order-detail.page').then((m) => m.OrderDetailPage),
      },
      {
        path: 'orders/:id/payment',
        loadComponent: () =>
          import('./features/payment/payment-submit/payment-submit.page').then((m) => m.PaymentSubmitPage),
      },
      {
        path: 'history',
        loadComponent: () => import('./features/history/history.page').then((m) => m.HistoryPage),
      },
      {
        path: 'wallet',
        loadComponent: () => import('./features/wallet/wallet.page').then((m) => m.WalletPage),
      },
      {
        path: 'preferred-rate',
        loadComponent: () =>
          import('./features/preferred-rate/preferred-rate.page').then((m) => m.PreferredRatePage),
      },
      {
        path: 'notifications',
        loadComponent: () =>
          import('./features/notifications/notifications.page').then((m) => m.NotificationsPage),
      },
    ],
  },
  {
    path: 'admin',
    loadComponent: () =>
      import('./layouts/admin-layout/admin-layout.component').then((m) => m.AdminLayoutComponent),
    canActivate: [adminGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      {
        path: 'dashboard',
        loadComponent: () =>
          import('./features/admin/admin-dashboard/admin-dashboard.page').then((m) => m.AdminDashboardPage),
      },
      {
        path: 'rates',
        loadComponent: () =>
          import('./features/admin/admin-rates/admin-rates.page').then((m) => m.AdminRatesPage),
      },
      {
        path: 'orders',
        loadComponent: () =>
          import('./features/admin/admin-orders/admin-orders.page').then((m) => m.AdminOrdersPage),
      },
      {
        path: 'orders/:id',
        loadComponent: () =>
          import('./features/admin/admin-order-detail/admin-order-detail.page').then(
            (m) => m.AdminOrderDetailPage,
          ),
      },
      {
        path: 'payments',
        loadComponent: () =>
          import('./features/admin/admin-payments/admin-payments.page').then((m) => m.AdminPaymentsPage),
      },
      {
        path: 'settlements',
        loadComponent: () =>
          import('./features/admin/admin-settlements/admin-settlements.page').then(
            (m) => m.AdminSettlementsPage,
          ),
      },
      {
        path: 'settlements/:id',
        loadComponent: () =>
          import('./features/admin/admin-settlement-detail/admin-settlement-detail.page').then(
            (m) => m.AdminSettlementDetailPage,
          ),
      },
      {
        path: 'treasury',
        loadComponent: () =>
          import('./features/admin/admin-treasury/admin-treasury.page').then((m) => m.AdminTreasuryPage),
      },
    ],
  },
  { path: '', pathMatch: 'full', redirectTo: 'login' },
  { path: '**', redirectTo: 'login' },
];
