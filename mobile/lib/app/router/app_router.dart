import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/auth/auth_session.dart';
import '../../features/achievements/presentation/my_gains_page.dart';
import '../../features/auth/presentation/login_page.dart';
import '../../features/auth/presentation/register_page.dart';
import '../../features/auth/presentation/splash_page.dart';
import '../../features/business/presentation/business_page.dart';
import '../../features/home/presentation/home_page.dart';
import '../../features/more/presentation/experience_profile_settings_page.dart';
import '../../features/more/presentation/more_page.dart';
import '../../features/notifications/presentation/notifications_page.dart';
import '../../features/orders/presentation/order_create_page.dart';
import '../../features/orders/presentation/order_detail_page.dart';
import '../../features/orders/presentation/order_list_page.dart';
import '../../features/orders/presentation/order_tracking_page.dart';
import '../../features/payment/presentation/payment_submit_page.dart';
import '../../features/pools/presentation/my_pools_page.dart';
import '../../features/pools/presentation/pool_create_page.dart';
import '../../features/pools/presentation/pool_detail_page.dart';
import '../../features/pools/presentation/pool_join_page.dart';
import '../../features/preferred_rate/presentation/preferred_rate_page.dart';
import '../../features/quote/presentation/quote_create_page.dart';
import '../../features/rates/presentation/rate_alerts_page.dart';
import '../../features/rates/presentation/rate_history_page.dart';
import '../../features/suppliers/models/supplier_models.dart';
import '../../features/suppliers/presentation/pay_again_page.dart';
import '../../features/suppliers/presentation/supplier_detail_page.dart';
import '../../features/suppliers/presentation/supplier_form_page.dart';
import '../../features/suppliers/presentation/supplier_list_page.dart';
import '../../shared/models/current_user.dart';
import '../../shared/widgets/coming_soon_page.dart';
import 'app_shell.dart';

/// Routeur central — un chemin par feature reelle (mission section 45),
/// garde d'authentification reactive branchee sur [AuthSession] (redirection
/// automatique vers /login sur un 401, sans que la couche reseau ne
/// connaisse la navigation — voir [ApiClient]).
///
/// IMPORTANT (retour d'utilisation reelle) : les ecrans "destination"
/// (detail/creation d'ordre, detail/edition/pay-again d'un fournisseur,
/// paiement) sont des routes IMBRIQUEES DANS LA COQUILLE A ONGLETS, jamais
/// des routes racine par-dessus elle. Une premiere version les poussait par-
/// dessus la coquille (barre du bas masquee pendant le parcours) ; des tests
/// reels ont montre que les utilisateurs s'y perdaient (aucune barre de
/// navigation visible, aucun moyen fiable de revenir a un onglet). La barre
/// du bas reste maintenant TOUJOURS visible, et le bouton retour standard de
/// chaque ecran remonte vers la racine de son onglet.
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

      // ---- Coquille a onglets : TOUT le reste vit ici, la barre du bas ----
      // ---- ne disparait plus jamais pendant un parcours.               ----
      StatefulShellRoute.indexedStack(
        builder: (context, state, navigationShell) => AppShell(navigationShell: navigationShell),
        branches: [
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: '/home',
                builder: (context, state) => const HomePage(),
                routes: [
                  GoRoute(path: 'gains', builder: (context, state) => const MyGainsPage()),
                ],
              ),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: '/pay',
                builder: (context, state) => const QuoteCreatePage(),
                routes: [
                  GoRoute(
                    path: 'orders/new',
                    builder: (context, state) {
                      final args = state.extra as OrderCreateArgs;
                      return OrderCreatePage(quote: args.quote, poolId: args.poolId);
                    },
                  ),
                  // Routes statiques ('pools', 'pools/new', 'pools/join') DOIVENT precéder
                  // 'pools/:id' dans cette liste : go_router retient la premiere correspondance,
                  // sans quoi 'new'/'join' seraient captures comme un identifiant de Ruee.
                  GoRoute(path: 'pools', builder: (context, state) => const MyPoolsPage()),
                  GoRoute(path: 'pools/new', builder: (context, state) => const PoolCreatePage()),
                  GoRoute(path: 'pools/join', builder: (context, state) => const PoolJoinPage()),
                  GoRoute(
                    path: 'pools/:id',
                    builder: (context, state) => PoolDetailPage(poolId: state.pathParameters['id']!),
                    routes: [
                      GoRoute(
                        path: 'checkout',
                        builder: (context, state) => QuoteCreatePage(poolId: state.pathParameters['id']),
                      ),
                    ],
                  ),
                ],
              ),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: '/suppliers',
                builder: (context, state) => const SupplierListPage(),
                routes: [
                  GoRoute(path: 'new', builder: (context, state) => const SupplierFormPage()),
                  GoRoute(
                    path: ':id',
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
                ],
              ),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: '/activity',
                builder: (context, state) => const OrderListPage(),
                routes: [
                  GoRoute(
                    path: 'orders/:id',
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
                ],
              ),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: '/more',
                builder: (context, state) => const MorePage(),
                routes: [
                  GoRoute(path: 'rates', builder: (context, state) => const RateHistoryPage()),
                  GoRoute(path: 'rate-alerts', builder: (context, state) => const RateAlertsPage()),
                  GoRoute(path: 'preferred-rate', builder: (context, state) => const PreferredRatePage()),
                  GoRoute(
                    path: 'wallet',
                    builder: (context, state) =>
                        const ComingSoonPage(title: 'Portefeuille', icon: Icons.account_balance_wallet_outlined),
                  ),
                  GoRoute(path: 'notifications', builder: (context, state) => const NotificationsPage()),
                  GoRoute(path: 'business', builder: (context, state) => const BusinessPage()),
                  GoRoute(
                    path: 'experience-profile',
                    builder: (context, state) =>
                        ExperienceProfileSettingsPage(current: state.extra as ExperienceProfile),
                  ),
                ],
              ),
            ],
          ),
        ],
      ),
    ],
  );
}
