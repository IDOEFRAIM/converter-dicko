import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../core/auth/auth_session.dart';
import '../core/config/app_config.dart';
import '../core/network/api_client.dart';
import '../core/storage/celebrated_badges_store.dart';
import '../core/theme/app_theme.dart';
import '../features/achievements/application/badge_celebration_controller.dart';
import '../features/achievements/data/achievement_api.dart';
import '../features/auth/data/auth_repository.dart';
import '../features/business/data/business_api.dart';
import '../features/home/application/home_controller.dart';
import '../features/notifications/data/notification_api.dart';
import '../features/orders/data/order_api.dart';
import '../features/payment/data/payment_api.dart';
import '../features/pools/data/pool_api.dart';
import '../features/preferred_rate/data/preferred_rate_api.dart';
import '../features/quote/data/quote_api.dart';
import '../features/rates/data/rate_history_api.dart';
import '../features/settings/data/settings_api.dart';
import '../features/suppliers/data/supplier_api.dart';
import 'router/app_router.dart';

/// Racine de l'application : composition des dependances (config -> session
/// -> reseau -> repositories -> controllers) et du routeur. Un seul endroit
/// construit ces objets — aucune feature n'instancie son propre client HTTP
/// ou sa propre session (mission section 13).
///
/// Seuls les objets veritablement globaux (config, session, client HTTP, les
/// "Api" sans etat) vivent ici. Les controllers propres a un ecran (creation
/// de devis, detail fournisseur...) sont fournis localement par cet ecran,
/// pour ne pas garder leur etat en memoire une fois l'ecran quitte.
class ConverterApp extends StatelessWidget {
  const ConverterApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MultiProvider(
      providers: [
        Provider<AppConfig>(create: (_) => AppConfig.fromDefine()),
        ChangeNotifierProvider<AuthSession>(create: (_) => AuthSession()),
        Provider<ApiClient>(
          create: (context) => ApiClient(
            config: context.read<AppConfig>(),
            authSession: context.read<AuthSession>(),
          ),
        ),
        Provider<AuthRepository>(
          create: (context) => AuthRepository(client: context.read<ApiClient>(), session: context.read<AuthSession>()),
        ),
        Provider<RateHistoryApi>(create: (context) => RateHistoryApi(context.read<ApiClient>())),
        Provider<OrderApi>(create: (context) => OrderApi(context.read<ApiClient>())),
        Provider<SupplierApi>(create: (context) => SupplierApi(context.read<ApiClient>())),
        Provider<QuoteApi>(create: (context) => QuoteApi(context.read<ApiClient>())),
        Provider<PaymentApi>(create: (context) => PaymentApi(context.read<ApiClient>())),
        Provider<SettingsApi>(create: (context) => SettingsApi(context.read<ApiClient>())),
        Provider<NotificationApi>(create: (context) => NotificationApi(context.read<ApiClient>())),
        Provider<BusinessApi>(create: (context) => BusinessApi(context.read<ApiClient>())),
        Provider<PreferredRateApi>(create: (context) => PreferredRateApi(context.read<ApiClient>())),
        Provider<AchievementApi>(create: (context) => AchievementApi(context.read<ApiClient>())),
        Provider<PoolApi>(create: (context) => PoolApi(context.read<ApiClient>())),
        Provider<CelebratedBadgesStore>(create: (_) => const CelebratedBadgesStore()),
        ChangeNotifierProvider<HomeController>(
          create: (context) => HomeController(
            rateHistoryApi: context.read<RateHistoryApi>(),
            orderApi: context.read<OrderApi>(),
            supplierApi: context.read<SupplierApi>(),
            achievementApi: context.read<AchievementApi>(),
          ),
        ),
        // Celebration "nouveau rang debloque" (mission "differenciation
        // marketing") : global, comme HomeController — un franchissement de
        // palier peut survenir apres n'importe quel parcours, pas seulement
        // en revenant sur l'accueil.
        ChangeNotifierProvider<BadgeCelebrationController>(
          create: (context) => BadgeCelebrationController(
            notificationApi: context.read<NotificationApi>(),
            store: context.read<CelebratedBadgesStore>(),
          ),
        ),
      ],
      child: _RouterHost(),
    );
  }
}

/// Separe dans son propre widget car le routeur doit etre construit UNE
/// SEULE fois par instance d'[AuthSession] (via `late final` implicite d'un
/// State) — le reconstruire a chaque rebuild de [ConverterApp] perdrait la
/// pile de navigation courante.
class _RouterHost extends StatefulWidget {
  @override
  State<_RouterHost> createState() => _RouterHostState();
}

class _RouterHostState extends State<_RouterHost> {
  late final _router = buildAppRouter(context.read<AuthSession>());

  @override
  Widget build(BuildContext context) {
    // watch (pas read) : le theme doit se rafraichir immediatement quand le
    // client change son habillage (voir AuthRepository.updateExperienceProfile),
    // sans jamais reconstruire le routeur lui-meme (voir le commentaire de
    // classe sur la perte de pile de navigation).
    final experienceProfile = context.watch<AuthSession>().experienceProfile;
    return MaterialApp.router(
      title: 'Converter',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.forProfile(experienceProfile),
      routerConfig: _router,
    );
  }
}
