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
        path: 'orders/:id/tracking',
        loadComponent: () =>
          import('./features/order/order-tracking/order-tracking.page').then((m) => m.OrderTrackingPage),
      },
      {
        path: 'history',
        loadComponent: () => import('./features/history/history.page').then((m) => m.HistoryPage),
      },
      {
        path: 'suppliers',
        loadComponent: () =>
          import('./features/supplier/supplier-list/supplier-list.page').then((m) => m.SupplierListPage),
      },
      {
        path: 'suppliers/new',
        loadComponent: () =>
          import('./features/supplier/supplier-form/supplier-form.page').then((m) => m.SupplierFormPage),
      },
      {
        path: 'suppliers/:id',
        loadComponent: () =>
          import('./features/supplier/supplier-detail/supplier-detail.page').then(
            (m) => m.SupplierDetailPage,
          ),
      },
      {
        path: 'suppliers/:id/edit',
        loadComponent: () =>
          import('./features/supplier/supplier-form/supplier-form.page').then((m) => m.SupplierFormPage),
      },
      {
        path: 'suppliers/:id/pay-again',
        loadComponent: () =>
          import('./features/supplier/pay-again/pay-again.page').then((m) => m.PayAgainPage),
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
      {
        path: 'rates',
        loadComponent: () =>
          import('./features/rate/rate-history/rate-history.page').then((m) => m.RateHistoryPage),
      },
      {
        path: 'rate-alerts',
        loadComponent: () =>
          import('./features/rate/rate-alerts/rate-alerts.page').then((m) => m.RateAlertsPage),
      },
      {
        path: 'business',
        loadComponent: () => import('./features/business/business.page').then((m) => m.BusinessPage),
      },
      {
        path: 'support',
        loadComponent: () => import('./features/support/support.page').then((m) => m.SupportPage),
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
        path: 'cost-rates',
        loadComponent: () =>
          import('./features/admin/admin-cost-rates/admin-cost-rates.page').then(
            (m) => m.AdminCostRatesPage,
          ),
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
        path: 'kyc',
        loadComponent: () =>
          import('./features/admin/admin-kyc/admin-kyc.page').then((m) => m.AdminKycPage),
      },
      {
        path: 'users',
        loadComponent: () =>
          import('./features/admin/admin-users/admin-users.page').then((m) => m.AdminUsersPage),
      },
      {
        path: 'users/:id',
        loadComponent: () =>
          import('./features/admin/admin-user-detail/admin-user-detail.page').then(
            (m) => m.AdminUserDetailPage,
          ),
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
      {
        path: 'support',
        loadComponent: () =>
          import('./features/admin/admin-support-list/admin-support-list.page').then(
            (m) => m.AdminSupportListPage,
          ),
      },
      {
        path: 'support/:userId',
        loadComponent: () =>
          import('./features/admin/admin-support-thread/admin-support-thread.page').then(
            (m) => m.AdminSupportThreadPage,
          ),
      },
    ],
  },
  { path: '', pathMatch: 'full', redirectTo: 'login' },
  { path: '**', redirectTo: 'login' },
];
