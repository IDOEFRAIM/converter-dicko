import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/auth/auth_session.dart';
import '../../features/auth/presentation/login_page.dart';
import '../../features/auth/presentation/register_page.dart';
import '../../features/auth/presentation/splash_page.dart';
import '../../features/home/presentation/home_page.dart';
import '../../features/more/presentation/more_page.dart';
import '../../shared/widgets/coming_soon_page.dart';
import 'app_shell.dart';

/// Routeur central — un chemin par feature reelle (mission section 45),
/// garde d'authentification reactive branchee sur [AuthSession] (redirection
/// automatique vers /login sur un 401, sans que la couche reseau ne
/// connaisse la navigation — voir [ApiClient]).
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
      StatefulShellRoute.indexedStack(
        builder: (context, state, navigationShell) => AppShell(navigationShell: navigationShell),
        branches: [
          StatefulShellBranch(
            routes: [GoRoute(path: '/home', builder: (context, state) => const HomePage())],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: '/pay',
                builder: (context, state) => const ComingSoonPage(title: 'Payer un fournisseur', icon: Icons.send_outlined),
              ),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: '/suppliers',
                builder: (context, state) =>
                    const ComingSoonPage(title: 'Fournisseurs', icon: Icons.storefront_outlined),
              ),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: '/activity',
                builder: (context, state) =>
                    const ComingSoonPage(title: 'Activite', icon: Icons.receipt_long_outlined),
              ),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: '/more',
                builder: (context, state) => const MorePage(),
                routes: [
                  GoRoute(
                    path: 'rates',
                    builder: (context, state) => const ComingSoonPage(title: 'Taux', icon: Icons.show_chart),
                  ),
                  GoRoute(
                    path: 'rate-alerts',
                    builder: (context, state) =>
                        const ComingSoonPage(title: 'Alertes de taux', icon: Icons.notifications_active_outlined),
                  ),
                  GoRoute(
                    path: 'wallet',
                    builder: (context, state) =>
                        const ComingSoonPage(title: 'Portefeuille', icon: Icons.account_balance_wallet_outlined),
                  ),
                  GoRoute(
                    path: 'notifications',
                    builder: (context, state) =>
                        const ComingSoonPage(title: 'Notifications', icon: Icons.notifications_outlined),
                  ),
                  GoRoute(
                    path: 'business',
                    builder: (context, state) => const ComingSoonPage(
                      title: 'Espace professionnel',
                      icon: Icons.business_center_outlined,
                    ),
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
