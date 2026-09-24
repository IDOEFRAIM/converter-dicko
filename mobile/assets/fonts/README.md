# Polices de marque — Lot E (« Le Comptoir »)

Statut : **câblé, en attente des fichiers `.ttf`.** Tant que ce dossier ne contient
pas les polices et que le bloc `fonts:` du `pubspec.yaml` est commenté, l'app tourne
en **police système** — aucun risque de build.

Voir `docs/MOBILE_DESIGN_LANGUAGE.md` §2 (Typographie) et §5 (Lot E).

## Pourquoi le mécanisme natif et pas `google_fonts`

Le paquet `google_fonts` télécharge la police au premier usage (requête réseau vers
`fonts.gstatic.com`). Inacceptable pour une app de paiement fournisseur utilisée en
connectivité intermittente : premier lancement en police de repli puis reflow, et
hors-ligne la police n'arrive jamais. Le désactiver (`allowRuntimeFetching = false`)
impose de toute façon d'embarquer le `.ttf` — c'est exactement ce que fait le bloc
`fonts:` de `pubspec.yaml`, sans nouvelle dépendance (et sans `flutter pub get` à
valider, indisponible sur la machine de dev actuelle).

## Cibles

| Rôle | Famille | Usage dans le code | Licence |
|------|---------|--------------------|---------|
| Display / titres / corps | **Sora** | `AppTypography._base` (⇒ `displayXl`, `titleLarge`, `titleMedium`, `body`, `bodyStrong`, `caption`, `eyebrow`, `button`) | SIL OFL 1.1 |
| Chiffres (chasse fixe) | **IBM Plex Mono** | `AppTypography._figure` (⇒ `metricLarge`, `metricMedium`, `figureLarge`, `figureMedium`, `figureSmall`) | SIL OFL 1.1 |

Sora : <https://fonts.google.com/specimen/Sora> · dépôt <https://github.com/soratype/Sora>
IBM Plex Mono : <https://fonts.google.com/specimen/IBM+Plex+Mono> · dépôt <https://github.com/IBM/plex>

SIL OFL 1.1 autorise la redistribution embarquée dans une application. Déposer aussi
le texte de licence : `assets/fonts/OFL-Sora.txt`, `assets/fonts/OFL-IBMPlexMono.txt`.

## Fichiers à déposer ici

Graisses réellement utilisées : **400, 600, 700, 800** (voir `app_typography.dart`).
IBM Plex Mono ne fournit pas de 800 → Flutter prend la plus proche (700).

```
assets/fonts/
  Sora-Regular.ttf        (400)
  Sora-SemiBold.ttf       (600)
  Sora-Bold.ttf           (700)
  Sora-ExtraBold.ttf      (800)
  IBMPlexMono-Regular.ttf     (400)
  IBMPlexMono-Medium.ttf      (500)
  IBMPlexMono-SemiBold.ttf    (600)
  IBMPlexMono-Bold.ttf        (700)
  OFL-Sora.txt
  OFL-IBMPlexMono.txt
```

## Activation (3 étapes, ~2 min)

### 1. Déposer les fichiers ci-dessus dans ce dossier.

### 2. `pubspec.yaml` — décommenter le bloc `fonts:` (déjà présent, section `flutter:`) :

```yaml
  fonts:
    - family: Sora
      fonts:
        - asset: assets/fonts/Sora-Regular.ttf
        - asset: assets/fonts/Sora-SemiBold.ttf
          weight: 600
        - asset: assets/fonts/Sora-Bold.ttf
          weight: 700
        - asset: assets/fonts/Sora-ExtraBold.ttf
          weight: 800
    - family: IBM Plex Mono
      fonts:
        - asset: assets/fonts/IBMPlexMono-Regular.ttf
        - asset: assets/fonts/IBMPlexMono-Medium.ttf
          weight: 500
        - asset: assets/fonts/IBMPlexMono-SemiBold.ttf
          weight: 600
        - asset: assets/fonts/IBMPlexMono-Bold.ttf
          weight: 700
```

### 3. `lib/core/theme/app_typography.dart` — basculer les deux constantes :

```dart
static const String? displayFontFamily = 'Sora';
static const String? figureFontFamily = 'IBM Plex Mono';
```

Puis `flutter pub get` && `flutter run`. Rien d'autre à toucher : tous les styles
dérivent de `_base` / `_figure`, et `AppTheme` lit `displayFontFamily` pour le repli.

## Vérification

- Un montant (`figure*`) et un titre (`titleLarge`) doivent visiblement changer de
  dessin ; les chiffres restent en chasse fixe (largeur constante pendant `CountUpText`).
- `flutter build apk --release` doit passer (assets bien empaquetés).
- Tester hors-ligne : la police doit s'afficher au **premier** lancement (preuve que
  rien n'est téléchargé).
