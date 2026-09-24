// Smoke test minimal : verifie qu'un widget partage central (le corridor
// Burkina Faso -> Chine, section 8 de la mission) se construit sans lever
// d'exception. Volontairement independant de la composition complete de
// l'app (reseau/session/routeur) pour rester rapide et deterministe --
// les tests d'integration reseau/navigation viendront avec un environnement
// Flutter reel (voir la note "Flutter runtime validation: NOT PERFORMED ON
// THIS MACHINE" du rapport de lot).

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';

import 'package:mobile/core/auth/auth_session.dart';
import 'package:mobile/shared/widgets/corridor.dart';

void main() {
  testWidgets('Corridor renders both flags and labels at every level', (WidgetTester tester) async {
    for (final level in CorridorLevel.values) {
      // CorridorLevel.hero lit AuthSession.experienceGradient (degrade de marque) --
      // fourni pour tous les niveaux par simplicite, sans effet sur compact/normal.
      await tester.pumpWidget(
        ChangeNotifierProvider<AuthSession>(
          create: (_) => AuthSession(),
          child: MaterialApp(
            home: Scaffold(body: Corridor(level: level)),
          ),
        ),
      );

      expect(find.text('🇧🇫'), findsOneWidget);
      expect(find.text('🇨🇳'), findsOneWidget);
      expect(find.text('Burkina Faso'), findsOneWidget);
      expect(find.text('Chine'), findsOneWidget);
    }
  });
}
