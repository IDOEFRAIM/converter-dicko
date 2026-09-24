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
    // Espace client : MIROIR de la coquille a onglets mobile (mobile/lib/app/router/
    // app_router.dart). `data.tab` rattache chaque ecran a son onglet (barre du bas),
    // `data.title` est le titre de la barre d'application, `data.root` marque la racine
    // d'un onglet (pas de fleche retour). Les anciennes URL restent redirigees.
    path: '',
    loadComponent: () =>
      import('./layouts/client-layout/client-layout.component').then((m) => m.ClientLayoutComponent),
    canActivate: [authGuard],
    children: [
      // ---- Onglet Accueil ----
      {
        path: 'home',
        data: { tab: 'home', root: true, title: 'Accueil', hideAppBar: true },
        loadComponent: () => import('./features/home/home.page').then((m) => m.HomePage),
      },
      {
        path: 'home/gains',
        data: { tab: 'home', title: 'Mes gains' },
        loadComponent: () => import('./features/gains/my-gains.page').then((m) => m.MyGainsPage),
      },
      { path: 'dashboard', pathMatch: 'full', redirectTo: 'home' },

      // ---- Onglet Payer ----
      {
        path: 'pay',
        data: { tab: 'pay', root: true, title: 'Payer un fournisseur' },
        loadComponent: () =>
          import('./features/quote/quote-create/quote-create.page').then((m) => m.QuoteCreatePage),
      },
      {
        path: 'pay/pools',
        data: { tab: 'pay', title: 'Mes Ruees' },
        loadComponent: () => import('./features/pools/my-pools/my-pools.page').then((m) => m.MyPoolsPage),
      },
      {
        path: 'pay/pools/new',
        data: { tab: 'pay', title: 'Lancer une Ruee' },
        loadComponent: () =>
          import('./features/pools/pool-create/pool-create.page').then((m) => m.PoolCreatePage),
      },
      {
        path: 'pay/pools/join',
        data: { tab: 'pay', title: 'Rejoindre une Ruee' },
        loadComponent: () => import('./features/pools/pool-join/pool-join.page').then((m) => m.PoolJoinPage),
      },
      {
        path: 'pay/pools/:id',
        data: { tab: 'pay', title: 'Ruee collective' },
        loadComponent: () =>
          import('./features/pools/pool-detail/pool-detail.page').then((m) => m.PoolDetailPage),
      },
      {
        path: 'pay/pools/:id/checkout',
        data: { tab: 'pay', title: 'Payer un fournisseur' },
        loadComponent: () =>
          import('./features/quote/quote-create/quote-create.page').then((m) => m.QuoteCreatePage),
      },
      { path: 'quote/new', pathMatch: 'full', redirectTo: 'pay' },
      // Anciennes URL (premiere version PWA) -> ecrans calques sur le mobile.
      { path: 'pools', pathMatch: 'full', redirectTo: 'pay/pools' },
      { path: 'pools/new', pathMatch: 'full', redirectTo: 'pay/pools/new' },
      { path: 'pools/join', pathMatch: 'full', redirectTo: 'pay/pools/join' },
      { path: 'pools/:id', pathMatch: 'full', redirectTo: 'pay/pools/:id' },
      { path: 'gains', pathMatch: 'full', redirectTo: 'home/gains' },
      {
        path: 'quote/:id',
        data: { tab: 'pay', title: 'Votre devis' },
        loadComponent: () =>
          import('./features/quote/quote-detail/quote-detail.page').then((m) => m.QuoteDetailPage),
      },
      {
        path: 'order/new',
        data: { tab: 'pay', title: 'Beneficiaire' },
        loadComponent: () =>
          import('./features/order/order-create/order-create.page').then((m) => m.OrderCreatePage),
      },

      // ---- Onglet Fournisseurs ----
      {
        path: 'suppliers',
        data: {
          tab: 'suppliers',
          root: true,
          title: 'Fournisseurs',
          action: { icon: 'add', link: '/suppliers/new', label: 'Ajouter un fournisseur' },
        },
        loadComponent: () =>
          import('./features/supplier/supplier-list/supplier-list.page').then((m) => m.SupplierListPage),
      },
      {
        path: 'suppliers/new',
        data: { tab: 'suppliers', title: 'Nouveau fournisseur' },
        loadComponent: () =>
          import('./features/supplier/supplier-form/supplier-form.page').then((m) => m.SupplierFormPage),
      },
      {
        path: 'suppliers/:id',
        data: { tab: 'suppliers', title: 'Fournisseur' },
        loadComponent: () =>
          import('./features/supplier/supplier-detail/supplier-detail.page').then(
            (m) => m.SupplierDetailPage,
          ),
      },
      {
        path: 'suppliers/:id/edit',
        data: { tab: 'suppliers', title: 'Modifier le fournisseur' },
        loadComponent: () =>
          import('./features/supplier/supplier-form/supplier-form.page').then((m) => m.SupplierFormPage),
      },
      {
        path: 'suppliers/:id/pay-again',
        data: { tab: 'suppliers', title: 'Payer a nouveau' },
        loadComponent: () =>
          import('./features/supplier/pay-again/pay-again.page').then((m) => m.PayAgainPage),
      },

      // ---- Onglet Activite ----
      {
        path: 'activity',
        data: { tab: 'activity', root: true, title: 'Activite' },
        loadComponent: () => import('./features/history/history.page').then((m) => m.HistoryPage),
      },
      { path: 'history', pathMatch: 'full', redirectTo: 'activity' },
      {
        path: 'orders/:id',
        data: { tab: 'activity', title: 'Transfert' },
        loadComponent: () =>
          import('./features/order/order-detail/order-detail.page').then((m) => m.OrderDetailPage),
      },
      {
        path: 'orders/:id/payment',
        data: { tab: 'activity', title: 'Paiement du transfert' },
        loadComponent: () =>
          import('./features/payment/payment-submit/payment-submit.page').then((m) => m.PaymentSubmitPage),
      },
      {
        path: 'orders/:id/tracking',
        data: { tab: 'activity', title: 'Suivi du transfert' },
        loadComponent: () =>
          import('./features/order/order-tracking/order-tracking.page').then((m) => m.OrderTrackingPage),
      },

      // ---- Onglet Messages ----
      {
        path: 'support',
        data: { tab: 'support', root: true, title: 'Messagerie' },
        loadComponent: () => import('./features/support/support.page').then((m) => m.SupportPage),
      },

      // ---- Onglet Plus ----
      {
        path: 'more',
        data: { tab: 'more', root: true, title: 'Plus' },
        loadComponent: () => import('./features/more/more.page').then((m) => m.MorePage),
      },
      {
        path: 'rates',
        data: { tab: 'more', title: 'Taux XOF / CNY' },
        loadComponent: () =>
          import('./features/rate/rate-history/rate-history.page').then((m) => m.RateHistoryPage),
      },
      {
        path: 'rate-alerts',
        data: { tab: 'more', title: 'Mes alertes' },
        loadComponent: () =>
          import('./features/rate/rate-alerts/rate-alerts.page').then((m) => m.RateAlertsPage),
      },
      {
        path: 'wallet',
        data: { tab: 'more', title: 'Portefeuille' },
        loadComponent: () => import('./features/wallet/wallet.page').then((m) => m.WalletPage),
      },
      {
        path: 'preferred-rate',
        data: { tab: 'more', title: 'Taux preferentiel' },
        loadComponent: () =>
          import('./features/preferred-rate/preferred-rate.page').then((m) => m.PreferredRatePage),
      },
      {
        path: 'notifications',
        data: { tab: 'more', title: 'Notifications' },
        loadComponent: () =>
          import('./features/notifications/notifications.page').then((m) => m.NotificationsPage),
      },
      {
        path: 'business',
        data: { tab: 'more', title: 'Espace professionnel' },
        loadComponent: () => import('./features/business/business.page').then((m) => m.BusinessPage),
      },
      {
        path: 'kyc',
        data: { tab: 'more', title: "Verification d'identite" },
        loadComponent: () => import('./features/kyc/kyc.page').then((m) => m.KycPage),
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
  {
    // Publique (aucun guard) : lisible avant meme la creation d'un compte (lien depuis
    // l'inscription), et depuis le menu utilisateur une fois connecte.
    path: 'privacy',
    loadComponent: () =>
      import('./features/legal/privacy-policy/privacy-policy.page').then((m) => m.PrivacyPolicyPage),
  },
  { path: '', pathMatch: 'full', redirectTo: 'login' },
  { path: '**', redirectTo: 'login' },
];
