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

Interdits : confettis cartoon, mascotte, sons stridents, « niveau supérieur ! » clignotant,
**et tout lexique de jeu** (guerrier, trophée, légende…) **ni jargon du métier du change**
(retour client sept. 2026 : « on sait pas vraiment de quoi tu parles »). Le vocabulaire est
celui du quotidien : une **échelle unique** de paliers `Débutant → Habitué → Expert →
Champion` (backend `AchievementService.CHANGER_TIERS`), un « Registre des opérations ».

- **Paliers = sceaux gravés** (medaillon `CustomPainter`, un motif par profil d'expérience) —
  pas un autocollant coloré. Le profil d'expérience est **fixé à l'inscription et non
  modifiable** ensuite (pas d'écran « Habillage »).
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

---

## 6. Corrections produit (2026-09)

Retours utilisateur post-refonte, traités hors « lots » :

- **#1 — Profil défini par l'identité, non modifiable** (fait) : à l'inscription, plus de
  « choisis ton habillage ». `ProfileIdentityPicker` pose **deux questions** — usage (mon
  activité pro → `PRO` / mes besoins personnels ou mes études → étudiant) puis civilité
  (Mme → `STUDENT_FEMALE` / M. → `STUDENT_MALE`) — et **dérive** le profil. Aucun écran ne
  permet de le changer ensuite : écran « Habillage », route `/more/experience-profile`,
  `AuthRepository.updateExperienceProfile` et `AuthSession.updateCurrentUser` supprimés
  (backend `PATCH /auth/me/experience-profile` conservé mais plus appelé).
- **#2 — Vocabulaire du quotidien, ni jeu ni jargon du métier** (fait) : une **échelle
  unique** de paliers `Débutant → Habitué → Expert → Champion` (backend
  `AchievementService.CHANGER_TIERS` ; remplace d'abord les deux listes genrées
  « guerrier / éclaireuse », puis `Cambiste → Courtier → Négociant → Maison de change`,
  retour client sept. 2026 : « on sait pas vraiment de quoi tu parles »). Copie mobile
  dé-ludifiée : « Registre des opérations » (ex-Livre des Gains / Carnet de route),
  « OPÉRATION N » (ex-PAGE N), « palier » (ex-« rang »), « points » (ex-« XP »), icône
  badge `workspace_premium` (ex-`military_tech`).
- **#4 — Rabais de Ruée progressif** (fait) : la réduction de marge n'est plus un
  pourcentage fixe. `rabais = base + PAR_PARTICIPANT·(N−1) + PAR_MILLION·⌊volume/1M⌋`,
  borné `[0, MAX]` (backend `PoolService.computeReward`, coefficients en `system_settings`
  via `V31`, testé par `PoolServiceRewardTest`). `PoolResponse` expose la valeur vivante **et
  le détail ligne à ligne** (`rewardParticipantBonusPercentage` = points dus au NOMBRE de
  participants, `rewardVolumeBonusPercentage` = points dus au VOLUME total échangé —
  `PoolService.computeRewardBreakdown`). Le détail de Ruée affiche un encart **« Rabais du
  taux — comment il se calcule »** : part de base / « N participants → −X pts » / « volume
  échangé → −Y pts » / total plafonné. Aucun calcul côté client.
- **#3 — Facture proforma fournisseur** (fait) : nouveau paquet backend
  `com.converter.order.proforma` (miroir de `order.receipt`, même garantie « aucun
  recalcul ») + endpoint `GET /api/v1/orders/{id}/proforma`. Disponible **quel que soit le
  statut** (une proforma s'émet avant paiement) mais **uniquement pour un ordre vers un
  fournisseur enregistré** (`supplierId != null` = signal du parcours « payer un
  fournisseur »). PDF PDFBox « FACTURE PROFORMA » : émetteur, acheteur (raison sociale /
  immatriculation / adresse depuis `BusinessProfile` si présent), référence, détail
  chiffré, bénéficiaire masqué, mention « sans valeur d'acquittement ».
  **Bascule à deux entrées** : un ordre est un *paiement fournisseur* si `supplierId != null`
  **ou** si `montant ≥ SUPPLIER_PAYMENT_THRESHOLD_XOF` (nouveau réglage, défaut 2 000 000,
  `V33`, `is_public`). En dessous : simple *échange personnel*. `OrderDetailResponse`
  expose `proformaAvailable` ; `OrderProformaService` refait la règle. Mobile :
  `OrderApi.downloadProforma`, carte « laque + or » **« PAIEMENT FOURNISSEUR · FACTURE
  PROFORMA — générée automatiquement »** sur `order_detail` quand `proformaAvailable`.
  Le pricing (taux/marge/frais) reste identique de part et d'autre du seuil.
- **#5 — Selfie souvenir → livre mémoire** (fait) : après un transfert **terminé**, une carte
  « Souvenir » sur `order_detail` propose un **selfie caméra** (`image_picker`,
  `CameraDevice.front`). Stockage **100 % local** : `MemoryBookStore` (index JSON dans
  `flutter_secure_storage` + fichiers dans `<documents>/memories/`), **rien envoyé au
  backend**. Le « filtre » est un cadre appliqué à l'**affichage** (`MemoryFrame` : double
  filet d'or + bandeau gravé date · montant), jamais composité dans le fichier. Le
  « Registre des opérations » (`my_gains`) affiche la vignette à la place de la pastille
  quand un souvenir existe, **et une galerie horizontale « Livre mémoire »** (`_MemoryBookStrip`)
  s'affiche en tête de « Mes gains » dès le premier souvenir. iOS : `NSCameraUsageDescription`
  ajouté ; Android : rien à déclarer (capture par intent).
- **#6 — KYC en libre-service** (fait) : nouveau module backend `com.converter.kyc` +
  table `kyc_submissions` (`V32`). L'utilisateur téléverse une pièce (recto + verso sauf
  passeport) + un selfie ; **revue manuelle interne** par un administrateur — pas de
  prestataire tiers. `POST/GET /api/v1/kyc/submissions` (user), `GET/POST
  /api/admin/kyc/submissions[/{id}/approve|reject|files/{kind}]` (admin, `ROLE_ADMIN`).
  L'approbation délègue à `UserService.verifyKyc` → `users.kyc_verified` reste l'**unique**
  gate consommé par `OrderService` (jamais deux logiques). Fichiers via `FileValidator` +
  `FileStorageService` (dossier `kyc`), jamais en base. Mobile : feature `kyc/` (stepper
  type de pièce → 3 photos caméra → envoi → statut PENDING/APPROVED/REJECTED avec motif),
  route `/more/kyc`, tuile « Vérification d'identité », et le blocage
  `KYC_VERIFICATION_REQUIRED` de la création d'ordre pointe désormais vers ce parcours.
- **Régime de texte + raccourcis d'accueil** (retour client, sept. 2026, capture de
  référence fournie — app concurrente à pastilles d'icônes colorées, très peu de texte) :
  passage sur l'ensemble des écrans mobile pour couper le texte décoratif (descriptions
  d'`EmptyState` qui ne faisaient que reformuler le titre, sous-titres explicatifs,
  libellés de bouton rallongés, `(optionnel)` répété sur chaque champ). Aucune logique
  changée, uniquement du contenu de chaînes. Sur l'accueil, le bouton pleine largeur
  « Payer un fournisseur » est remplacé par `QuickActionsRow` : 4 pastilles rondes
  colorées (Payer / Ruée / Taux préférentiel / Portefeuille) au-dessus de la carte de
  taux, qui gagne elle-même un petit bouton pilule « Convertir ». Élargissement
  **délibéré et strictement localisé** de la palette (`AppColors.shortcutOrange`,
  `shortcutViolet` + réutilisation de `signal`/`keyline` en fond clair) : la discipline
  « le rouge est un accent rare » (section 6/7) reste intacte partout ailleurs — ces
  teintes ne servent qu'aux 4 pastilles de l'accueil.
- **Detail de transfert (`order_detail`) — meme logique** : les deux cartes "laque + or"
  (justificatif, proforma) empilees l'une sur l'autre etaient le bloc le plus lourd/sombre
  de l'app. Remplacees par une carte claire commune (`_buildDownloadCard`) : pastille
  d'icone coloree (40px) + titre + une ligne + bouton pilule — meme grammaire que
  `QuickActionsRow`. Justificatif garde l'accent or (`keyline`, deja "officiel/premium") ;
  proforma reprend l'orange de "Payer" (meme parcours) ; le souvenir non-encore-pris
  reprend le violet de "Ruee" (moment personnel). Suivi/reglement gagnent une pastille
  discrete (36px, teinte `Theme.primary`) au lieu d'une simple icone posee.
- **Vision d'ensemble — le langage de pastille devient le standard de navigation**
  (retour client : "app intuitive, on doit clairement comprendre comment ca
  fonctionne"). Extraction en widget partage `shared/widgets/icon_badge.dart`
  (`IconBadge`), reutilise partout desormais :
  - `more_page` (menu "Plus") : ancienne liste plate de 7 lignes identiques ->
    3 sections regroupees par theme (TAUX / MON COMPTE / deconnexion isolee),
    chaque tuile porte la couleur de sa famille (signal = taux, keyline =
    portefeuille, ochre = identite — meme couleur que le bandeau KYC de
    l'accueil —, violet = pro).
  - `quote_create` (etape 1, "combien j'envoie") et `order_create` (etape 2,
    "a qui") gagnent chacune une pastille orange (meme accent que "Payer")
    a cote de leur eyebrow : le fil visuel du parcours de paiement est
    desormais continu de l'accueil jusqu'a l'ordre cree.
  Le commentaire de tete d'`AppColors` (section "Pastilles d'action") a ete
  mis a jour : ce n'est plus un elargissement localise a l'accueil, c'est le
  langage standard de comprehension de l'app — la discipline "rouge = accent
  rare" reste la seule regle non negociable.
- **Pastilles alignees par profil** (retour client : "les couleurs doivent
  etre alignees avec les differents types de profil"). Jusqu'ici les 4
  pastilles (envoyer/groupe/taux/premium) avaient les MEMES couleurs pour les
  trois habillages — incoherent avec le reste de l'app, ou `Theme.primary`
  varie deja selon [ExperienceProfile] (`AppTheme.forProfile`). Nouveau point
  d'acces unique `ExperiencePalette.accentFor(profile, AccentRole)`
  (`experience_theme.dart`) : 4 roles (`send`/`group`/`rate`/`premium`), une
  couleur+fond par role ET par profil :
  - **PRO** : shortcutOrange / shortcutViolet / signal / keyline (inchange).
  - **STUDENT_MALE ("Mode Epopee")** : les 3 teintes DEJA du degrade epique
    (chinaRed / epicPrimary / ochre) servent aux 3 premiers roles ; seul
    "premium" recoit une teinte reellement nouvelle (`epicAmber`, ambre).
  - **STUDENT_FEMALE ("Mode Histoire")** : blushPink/skyBlue (deja clairs)
    deviennent le FOND de la pastille plutot que l'icone (contraste
    insuffisant en premier plan) ; `storyPink`/`storyBlue`, plus satures,
    sont les 2 seules teintes nouvelles pour l'icone. "premium" reste l'or de
    marque (`keyline`) : le portefeuille est un repere universel, pas un
    moment d'habillage.
  Tous les ecrans qui posent une pastille de ces 4 roles (accueil, detail de
  transfert, menu "Plus", devis, beneficiaire) passent desormais par
  `accentFor` — aucun ne lit plus `AppColors.shortcut*`/`epic*`/`story*`
  directement (verifie par recherche). "Verification d'identite" (ochre) et
  "Notifications" (`Theme.primary`) restent hors de ces 4 roles : ce sont des
  reperes deja universels/deja adaptatifs, pas des concepts a decliner.
- **Paiement (`payment_submit`)** : pastille "envoyer" (meme role/couleur que
  devis/beneficiaire) a cote de "A PAYER" — le fil visuel du parcours de
  paiement est desormais continu de bout en bout (accueil -> devis ->
  beneficiaire -> paiement). Les cercles numerotes "1"/"2" des etapes
  restaient deja profil-adaptatifs (`Theme.of(context).colorScheme.primary`),
  aucun changement necessaire la.
