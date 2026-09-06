import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/auth/auth_session.dart';
import '../../features/auth/presentation/login_page.dart';
import '../../features/auth/presentation/register_page.dart';
import '../../features/auth/presentation/splash_page.dart';
import '../../features/business/presentation/business_page.dart';
import '../../features/home/presentation/home_page.dart';
import '../../features/more/presentation/more_page.dart';
import '../../features/notifications/presentation/notifications_page.dart';
import '../../features/orders/presentation/order_create_page.dart';
import '../../features/orders/presentation/order_detail_page.dart';
import '../../features/orders/presentation/order_list_page.dart';
import '../../features/orders/presentation/order_tracking_page.dart';
import '../../features/payment/presentation/payment_submit_page.dart';
import '../../features/quote/models/quote_models.dart';
import '../../features/quote/presentation/quote_create_page.dart';
import '../../features/rates/presentation/rate_alerts_page.dart';
import '../../features/rates/presentation/rate_history_page.dart';
import '../../features/suppliers/models/supplier_models.dart';
import '../../features/suppliers/presentation/pay_again_page.dart';
import '../../features/suppliers/presentation/supplier_detail_page.dart';
import '../../features/suppliers/presentation/supplier_form_page.dart';
import '../../features/suppliers/presentation/supplier_list_page.dart';
import '../../shared/widgets/coming_soon_page.dart';
import 'app_shell.dart';

/// Routeur central — un chemin par feature reelle (mission section 45),
/// garde d'authentification reactive branchee sur [AuthSession] (redirection
/// automatique vers /login sur un 401, sans que la couche reseau ne
/// connaisse la navigation — voir [ApiClient]).
///
/// Les ecrans "destination" (detail/creation d'ordre, detail/edition/pay-again
/// d'un fournisseur) sont des routes de NIVEAU RACINE (`push`ees par-dessus la
/// coquille a onglets) — masquer la barre du bas pendant ces parcours est un
/// choix delibere (mission section 39, priorite au flux plutot qu'a la
/// persistance visuelle des onglets pendant une transaction).
GoRouter buildAppRouter(AuthSession authSession) {
  return GoRouter(
    initialLocation: SplashPage.routePath,
    refreshListenable: authSession,
    redirect: (context, state) {
      final location = state.matchedLocation;
      final onSplash = location == SplashPage.routePath;
      final onAuthPages = location == LoginPage.routePath || location == RegisterPage.routePath;

      if (!authSession.initialized) {
        return onSplash ? null : SplashPage.routePath;
      }
      if (!authSession.isAuthenticated) {
        return onAuthPages ? null : LoginPage.routePath;
      }
      // Authentifie : ne jamais rester sur le splash/login/register.
      if (onSplash || onAuthPages) {
        return '/home';
      }
      return null;
    },
    routes: [
      GoRoute(path: SplashPage.routePath, builder: (context, state) => const SplashPage()),
      GoRoute(
        path: LoginPage.routePath,
        name: LoginPage.routeName,
        builder: (context, state) => const LoginPage(),
      ),
      GoRoute(
        path: RegisterPage.routePath,
        name: RegisterPage.routeName,
        builder: (context, state) => const RegisterPage(),
      ),

      // ---- Ordres (racine, par-dessus la coquille) ----
      GoRoute(
        path: '/orders/new',
        builder: (context, state) => OrderCreatePage(quote: state.extra as Quote),
      ),
      GoRoute(
        path: '/orders/:id',
        builder: (context, state) => OrderDetailPage(orderId: state.pathParameters['id']!),
        routes: [
          GoRoute(
            path: 'tracking',
            builder: (context, state) => OrderTrackingPage(orderId: state.pathParameters['id']!),
          ),
          GoRoute(
            path: 'payment',
            builder: (context, state) => PaymentSubmitPage(orderId: state.pathParameters['id']!),
          ),
        ],
      ),

      // ---- Fournisseurs (racine, par-dessus la coquille) ----
      GoRoute(path: '/suppliers/new', builder: (context, state) => const SupplierFormPage()),
      GoRoute(
        path: '/suppliers/:id',
        builder: (context, state) => SupplierDetailPage(supplierId: state.pathParameters['id']!),
        routes: [
          GoRoute(
            path: 'edit',
            builder: (context, state) => SupplierFormPage(existing: state.extra as SupplierDetail?),
          ),
          GoRoute(
            path: 'pay-again',
            builder: (context, state) => PayAgainPage(supplierId: state.pathParameters['id']!),
          ),
        ],
      ),

      // ---- Coquille a onglets ----
      StatefulShellRoute.indexedStack(
        builder: (context, state, navigationShell) => AppShell(navigationShell: navigationShell),
        branches: [
          StatefulShellBranch(
            routes: [GoRoute(path: '/home', builder: (context, state) => const HomePage())],
          ),
          StatefulShellBranch(
            routes: [GoRoute(path: '/pay', builder: (context, state) => const QuoteCreatePage())],
          ),
          StatefulShellBranch(
            routes: [GoRoute(path: '/suppliers', builder: (context, state) => const SupplierListPage())],
          ),
          StatefulShellBranch(
            routes: [GoRoute(path: '/activity', builder: (context, state) => const OrderListPage())],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: '/more',
                builder: (context, state) => const MorePage(),
                routes: [
                  GoRoute(path: 'rates', builder: (context, state) => const RateHistoryPage()),
                  GoRoute(path: 'rate-alerts', builder: (context, state) => const RateAlertsPage()),
                  GoRoute(
                    path: 'wallet',
                    builder: (context, state) =>
                        const ComingSoonPage(title: 'Portefeuille', icon: Icons.account_balance_wallet_outlined),
                  ),
                  GoRoute(path: 'notifications', builder: (context, state) => const NotificationsPage()),
                  GoRoute(path: 'business', builder: (context, state) => const BusinessPage()),
                ],
              ),
            ],
          ),
        ],
      ),
    ],
  );
}
