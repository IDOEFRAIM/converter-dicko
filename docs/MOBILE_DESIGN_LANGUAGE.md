# Converter Mobile — Langage de design « Le Comptoir »

Statut : direction de design (2026-09). S'applique au client Flutter (`mobile/`).
Objectif : une identité visuelle **sérieuse, professionnelle et difficile à copier**, qui
donne le sentiment d'entrer dans **son propre comptoir de change** pour le corridor
Burkina Faso ↔ Chine — pas dans un énième portefeuille grand public.

Ce document fixe le cap. Il ne réécrit pas l'app d'un coup : chaque « lot » applique une
tranche cohérente, testée puis commitée.

---

## 1. Le concept — pourquoi on ne ressemble à personne

La concurrence fintech converge vers le même moule : gros arrondis, dégradés multicolores,
illustrations 3D, confettis. On prend l'exact opposé : **la précision d'une salle des
marchés + la matérialité d'un registre relié.**

Deux éléments portent toute l'identité :

1. **Le Corridor vivant.** La route 🇧🇫 → 🇨🇳 n'est pas un pictogramme : c'est un fil animé
   le long duquel circule la lumière (splash, hero d'accueil, suivi d'ordre). Colonne
   vertébrale de la marque. Impossible à copier au pixel : c'est un **système** de
   géométrie + mouvement, pas une image.

2. **La surface Registre.** On abandonne la carte blanche plate + filet gris. À la place,
   deux matières :
   - **Papier** (`AppColors.paper`) — base chaude, léger grain gravé, règles en creux.
   - **Laque** (`AppColors.lacquer`) — navy profond des moments premium (hero, badge
     débloqué, reçu), grain subtil + **filet d'or** (`AppColors.keyline`) d'un trait.

Les chiffres (montants, taux, XP, références) sont rendus en **figures tabulaires** : ils
ne « sautent » jamais quand ils changent, et **s'incrémentent en animation**
(`CountUpText`). C'est le tic d'un tableau de cotation — lu comme du sérieux.

---

## 2. Système

### Couleur
Palette de marque inchangée (Deep Navy / Warm Ivory / Ochre / China Red). Ajouts :
`paper`, `paperEdge`, `lacquer`, `lacquerEdge`, `keyline` (laiton, plus rare que l'ochre),
`onLacquer*`, et **`signal`** (teal) réservé au « vivant » (taux qui bat), jamais un statut
métier. Aucun widget n'écrit `Color(0xFF…)` — toujours `AppColors`.

### Typographie
Ce qui porte l'identité même sans police custom : **contraste de graisse et de taille
beaucoup plus fort**, tracking resserré sur les titres, et `FontFeature.tabularFigures()`
+ `slashedZero()` sur tout ce qui est chiffre (`AppTypography.figure*`).

Polices de marque (Lot E) : **câblées, en attente des fichiers**. Tout le texte passe par
`AppTypography._base` (titres/corps) ou `._figure` (chiffres) ; ces deux styles lisent
`displayFontFamily` / `figureFontFamily`, seul point de bascule, à `null` (système) tant
que les `.ttf` ne sont pas déposés. Cible : **Sora** (display géométrique) + **IBM Plex
Mono** (chiffres). Mécanisme **natif** (`pubspec.yaml` `fonts:` + assets embarqués), pas
`google_fonts` : ce paquet télécharge la police au premier usage (réseau), rédhibitoire
en connectivité intermittente. Procédure : `mobile/assets/fonts/README.md`.

### Mouvement (`AppMotion`)
Vocabulaire unique : `instant / quick / base / slow` + `ambient` (boucle du corridor),
courbes `enter / exit / emphatic`, et un **ressort** (`AppMotion.spring`) partagé — cartes
qui se posent, chiffres qui roulent, badge qui « frappe » à l'écran. Respecte
`MediaQuery.disableAnimations` (accessibilité).

### Matière
`Pressable` (enfoncement ressort au toucher, pour toute carte actionnable),
`LedgerGrain` (grain déterministe en overlay sur les surfaces sombres),
ombres douces basses plutôt que le seul filet.

---

## 3. Gamification — sérieuse, jamais gadget

Interdits : confettis cartoon, mascotte, sons stridents, « niveau supérieur ! » clignotant.

- **Rangs = sceaux gravés** (medaillon `CustomPainter`, un motif par profil d'expérience) —
  pas un autocollant coloré.
- **XP = méridien** : un arc qui se remplit *le long de la courbe du corridor*.
- **Transferts terminés = tampons** dans un passeport / carnet (déjà amorcé dans
  « Mes gains », on pousse la métaphore).
- **PRO** : aucune de ces fioritures. Une **console d'opérateur** sobre — série en cours,
  volume du mois présenté comme un cadran d'objectif, export PDF. Le sérieux *est* sa
  gamification.
- Le franchissement d'un palier se **célèbre** au moment où il arrive (overlay laque +
  sceau qui se grave), puis se retrouve dans « Mes gains ». Jamais seulement un compteur
  qui a bougé en silence.

Règle d'or inchangée : **aucune donnée inventée.** XP, volumes, paliers sont dérivés des
ordres `COMPLETED` réels. On dramatise la présentation, jamais les chiffres.

---

## 4. Rétention — par la valeur, pas par la manipulation

Ce qui fait rester : la lisibilité d'un vrai outil (taux du moment net, dernière opération
en un coup d'œil, suivi honnête), la sensation de progression (méridien, carnet), et des
moments soignés (célébration de rang, reçu laqué). Pas de « streak anxiogène », pas de
notification culpabilisante, pas de dark pattern à la fermeture de compte.

---

## 5. Feuille de route

- **Lot A — Fondations** (fait) : tokens couleur + mouvement, échelle typo à fort
  contraste + figures tabulaires, `LedgerGrain`, `CountUpText`, `Pressable`, carte
  « papier ».
- **Lot B — Corridor + sceaux** (fait) : `CorridorFlow` animé, sceau de rang
  `CustomPainter`, méridien XP ; refonte du hero d'accueil, de « Mes gains » et de
  l'overlay de rang.
- **Lot C — Suivi & reçu** (fait) : `CorridorTimeline` — le suivi d'un transfert tracé le
  long de la courbe du corridor sur surface laque (stations le long du fil, filet d'or sur
  la portion parcourue, nœud lumineux pulsé à l'étape en cours), doublé de la liste en
  clair (libellés + horodatages en figures tabulaires) ; reçu « laque + or » : le
  justificatif d'un transfert terminé présenté comme une pièce scellée (`order_detail`).
- **Lot D — Console PRO** (fait) : `_ProConsole` dans « Mes gains » — surface papier sobre
  (aucun sceau, aucune laque : le sérieux *est* la gamification du PRO). `ObjectiveDial`
  (jauge d'atelier graduée + aiguille) affiche le volume du mois contre un **objectif que
  l'utilisateur se fixe lui-même** (`ProObjectiveStore`, `flutter_secure_storage` — jamais
  un objectif imposé ni inventé). Instruments « Série » (mois calendaires consécutifs avec
  ≥ 1 transfert terminé, dérivés de l'historique réel) et « Transferts ». Export du rapport
  PDF conservé.
- **Lot E — polices de marque** (câblé, en attente des fichiers) : seam unique
  `AppTypography.displayFontFamily` / `figureFontFamily` (à `null` = système), repli
  `AppTheme.fontFamily` aligné, bloc `fonts:` prêt à décommenter dans `pubspec.yaml`, kit
  d'activation `mobile/assets/fonts/README.md` (Sora + IBM Plex Mono, mécanisme natif —
  **pas** `google_fonts`, qui télécharge au premier usage). Activation = déposer 8 `.ttf`
  + décommenter + basculer 2 constantes.
- **Lot F — Ticket & copie** (fait) : `TransferTicket` — la forme monétaire d'une opération
  (« vous envoyez / bénéficiaire reçoit / taux / frais / référence ») rendue comme une
  **pièce physique** : surface papier, perforation + ligne de déchire (`CustomPainter` qui
  perce deux « bites » dans la couleur de fond), montants en `figureLarge` tabulaire,
  `FittedBox` anti-débordement, et **copie d'un tap** (`Clipboard` + `HapticFeedback`
  discret, morph « copié ✓ ») sur référence / taux / frais. Remplace l'empilement plat
  dans `quote_create` (devis) et `order_detail` (récap) ; le n° de transfert vit désormais
  *dans* le ticket. `MoneyDisplay` n'est plus utilisé.
- **Lot G — Suivi vivant** (fait) : montée en puissance de `CorridorTimeline` (bande peinte)
  — ombre portée sous l'arc (relief), graduations « radar » perpendiculaires, **lumière qui
  avance en temps réel** le long de la portion parcourue (comète : traînée qui s'estompe +
  cœur + halo flou), et stations rendues en **jauges circulaires** par tonalité (anneau or
  plein = fait ; balise `signal` pulsée = étape courante ; anneau rouge + croix = échec ;
  anneau pointillé = remboursement). Deux `AnimationController` (`_flow` comète, `_pulse`
  halo), coupés dès l'état terminal ou si `reduceMotion`. La liste en clair reste statique
  (lisibilité / accessibilité).
- **Lot H — Dépôt de preuve** (fait) : `ProofDropzone` remplace le bouton « Choisir un
  fichier » nu de la page paiement. Un **cadre de capture** (repères d'angle peints,
  surface papier) au repos ; pendant l'envoi, **un seul balayage or** doux traverse le
  cadre en boucle lente (`_SweepPainter`, jamais un laser stroboscopique) + bordure or ;
  une fois reçue, la preuve devient une **miniature scellée sur laque** (`Image.file`
  `cacheWidth: 300` pour le parc bas de gamme, filet or, cachet « PREUVE SCELLÉE »).
  Le contrôleur expose `proofPreviewPath` / `proofFileName` (chemin local de l'image
  choisie) — aucun changement de flux : toujours galerie, jamais la caméra.
- **Lot I — Ruée collective** (fait) : la barre linéaire + bandeau vert plat du détail de
  Ruée deviennent une **jauge manomètre** (`CollectionGauge`, qui réutilise `ObjectiveDial`
  — même famille d'instruments que la console PRO et le méridien) sur carte **laque** ;
  à l'objectif l'aiguille se fige et un **sceau se frappe** au centre (`PoolSeal` :
  disque laqué, double filet d'or, guilloché, coche gravée — **pas de confettis**).
  `ParticipantMonograms` : les participants en **jetons gravés qui se chevauchent** (+N),
  jamais de photos (le backend n'expose que l'initiale du prénom). `_SuccessBanner` et
  `_ParticipantTile` repassés en laque / papier + `Monogram` ; touche cohérente sur
  `my_pools_page` (filet de progression or). Le tuilage profil-dégradé de la carte cède
  la place à la laque : l'instrument prime sur l'habillage.
