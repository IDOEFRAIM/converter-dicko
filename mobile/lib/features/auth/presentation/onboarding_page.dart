import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../core/auth/auth_session.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../core/theme/app_typography.dart';
import '../../../shared/widgets/icon_badge.dart';
import '../../../shared/widgets/primary_action.dart';

class _OnboardingSlide {
  final IconData icon;
  final String title;
  final String description;

  const _OnboardingSlide({required this.icon, required this.title, required this.description});
}

const _slides = <_OnboardingSlide>[
  _OnboardingSlide(
    icon: Icons.swap_horiz,
    title: 'Envoyez de l\'argent en Chine',
    description:
        'Convertissez vos francs CFA (XOF) en yuans (CNY) et faites-les parvenir a un '
        'beneficiaire en Chine : compte bancaire, Alipay ou WeChat Pay.',
  ),
  _OnboardingSlide(
    icon: Icons.checklist_rtl,
    title: 'En 3 etapes simples',
    description:
        '1. Indiquez le montant a envoyer.\n'
        '2. Choisissez ou ajoutez un beneficiaire.\n'
        '3. Payez par Mobile Money et declarez votre paiement.',
  ),
  _OnboardingSlide(
    icon: Icons.track_changes_outlined,
    title: 'Suivez chaque transfert',
    description:
        'Suivez le statut de votre transfert en temps reel, de votre paiement jusqu\'a '
        'la reception par votre beneficiaire.',
  ),
  _OnboardingSlide(
    icon: Icons.bolt_outlined,
    title: 'Groupez-vous pour gagner plus',
    description:
        'Lancez ou rejoignez une Ruee avec d\'autres utilisateurs : objectif atteint a temps '
        '= reduction pour chacun sur son prochain transfert.',
  ),
];

/// Introduction a l'application, montree UNE SEULE FOIS avant la toute
/// premiere connexion/inscription (voir `AuthSession.onboardingSeen` et le
/// routeur, `app_router.dart`) -- retour client sept. 2026 : "a la premiere
/// ouverture ou connexion, y'a pas vraiment de systeme pour expliquer comment
/// ca fonctionne... dans les appli, y'a toujours un truc comme ca".
class OnboardingPage extends StatefulWidget {
  static const routePath = '/onboarding';

  const OnboardingPage({super.key});

  @override
  State<OnboardingPage> createState() => _OnboardingPageState();
}

class _OnboardingPageState extends State<OnboardingPage> {
  final _pageController = PageController();
  int _page = 0;

  @override
  void dispose() {
    _pageController.dispose();
    super.dispose();
  }

  bool get _isLastSlide => _page == _slides.length - 1;

  void _next() {
    if (_isLastSlide) {
      _finish();
      return;
    }
    _pageController.nextPage(duration: const Duration(milliseconds: 280), curve: Curves.easeOut);
  }

  /// Persiste le choix puis laisse le routeur rediriger lui-meme vers /login
  /// (`refreshListenable: authSession`, voir `AuthSession.markOnboardingSeen`)
  /// -- aucune navigation explicite necessaire ici.
  void _finish() => context.read<AuthSession>().markOnboardingSeen();

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.ivory,
      body: SafeArea(
        child: Column(
          children: [
            Align(
              alignment: Alignment.topRight,
              child: Padding(
                padding: const EdgeInsets.only(right: AppSpacing.md, top: AppSpacing.xs),
                child: TextButton(
                  // Actif meme sur le dernier slide : "Passer" et "Commencer" font alors
                  // exactement la meme chose (_finish) -- un bouton grise sans raison visible
                  // y semblerait casse plutot qu'intentionnel.
                  onPressed: _finish,
                  child: Text('Passer', style: AppTypography.body.copyWith(color: AppColors.inkMuted)),
                ),
              ),
            ),
            Expanded(
              child: PageView.builder(
                controller: _pageController,
                itemCount: _slides.length,
                onPageChanged: (index) => setState(() => _page = index),
                itemBuilder: (context, index) => _SlideView(slide: _slides[index]),
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(AppSpacing.xl, 0, AppSpacing.xl, AppSpacing.xl),
              child: Column(
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      for (var i = 0; i < _slides.length; i++)
                        AnimatedContainer(
                          duration: const Duration(milliseconds: 200),
                          margin: const EdgeInsets.symmetric(horizontal: 3),
                          width: i == _page ? 20 : 6,
                          height: 6,
                          decoration: BoxDecoration(
                            color: i == _page ? Theme.of(context).colorScheme.primary : AppColors.outline,
                            borderRadius: BorderRadius.circular(AppSpacing.radiusPill),
                          ),
                        ),
                    ],
                  ),
                  const SizedBox(height: AppSpacing.xl),
                  PrimaryAction(
                    label: _isLastSlide ? 'Commencer' : 'Suivant',
                    onPressed: _next,
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _SlideView extends StatelessWidget {
  final _OnboardingSlide slide;

  const _SlideView({required this.slide});

  @override
  Widget build(BuildContext context) {
    final accent = Theme.of(context).colorScheme.primary;
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: AppSpacing.xxl),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          IconBadge(icon: slide.icon, color: accent, size: 112),
          const SizedBox(height: AppSpacing.xxl),
          Text(
            slide.title,
            textAlign: TextAlign.center,
            style: AppTypography.titleLarge(AppColors.ink),
          ),
          const SizedBox(height: AppSpacing.md),
          Text(
            slide.description,
            textAlign: TextAlign.center,
            style: AppTypography.body.copyWith(color: AppColors.inkMuted),
          ),
        ],
      ),
    );
  }
}
