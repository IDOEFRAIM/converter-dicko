# Procedure de reconciliation quotidienne de la tresorerie

Statut : procedure operationnelle documentee (Phase 26, audit de fermeture 2026-09).
Aucun outil automatise n'existe pour l'executer : c'est une procedure manuelle,
a executer par un operateur habilite (ADMIN), quotidiennement.

## 1. Pourquoi cette procedure existe

Le solde de `treasury_accounts` (`balance`, `reserved_balance`) est une **projection
materialisee** du ledger append-only `treasury_transactions` — pas la source de verite.
Chaque ligne du ledger porte `balance_after`/`reserved_after`, ce qui permet de
reconstituer le solde attendu a tout instant sans rejouer tout l'historique, et donc de
detecter toute divergence entre "ce que dit le solde materialise" et "ce que dit le
ledger". Voir le Javadoc de `TreasuryAccount` (`backend/src/main/java/com/converter/
treasury/domain/TreasuryAccount.java:27-34`).

Deux contraintes SQL rendent certaines incoherences physiquement impossibles au niveau
base de donnees (defense en profondeur, pas une garantie que "tout va bien" cote metier) :
- `ck_treasury_accounts_balance_positive` : `balance >= 0`
- `ck_treasury_accounts_reserved_le_balance` : `reserved_balance <= balance`

Cette procedure verifie ce que ces contraintes NE couvrent PAS : que le solde
materialise correspond reellement a la somme des mouvements du ledger, et que le
ledger correspond reellement aux ordres/paiements/remboursements traites par ailleurs.

## 2. Frequence et responsable

- **Frequence** : une fois par jour ouvre, hors heures de forte activite (ex. tot le
  matin, avant l'ouverture aux clients).
  DECISION REQUIRED : l'heure exacte et le jour de la semaine (tous les jours vs.
  jours ouvres uniquement) restent a fixer par le responsable operationnel.
- **Responsable** : un compte ADMIN authentifie. Toutes les requetes ci-dessous
  passent par l'API `/api/admin/treasury/**`, deja protegee par `hasRole('ADMIN')`
  (voir `SecurityConfig`).

## 3. Etape 1 — Solde materialise vs. dernier `balance_after` du ledger

Pour chaque devise (`XOF`, `CNY`) :

```
GET /api/admin/treasury/accounts/{currency}
GET /api/admin/treasury/accounts/{currency}/transactions?size=1&sort=createdAt,desc
```

Le `balance` retourne par le premier appel DOIT etre strictement egal au
`balanceAfter` de la transaction la plus recente retournee par le second appel (idem
pour `reservedBalance` / `reservedAfter`).

**Si ces deux valeurs divergent** : c'est une incoherence CRITIQUE — le solde
materialise et le ledger, qui committent pourtant dans la meme transaction applicative
(voir `TreasuryService`), ne sont plus alignes. Cela ne devrait jamais arriver sauf
intervention manuelle directe en base (hors API). Escalader immediatement, ne pas
tenter de corriger via `/adjust` avant d'avoir identifie la cause (cf. Runbook,
scenario "Treasury discrepancy").

## 4. Etape 2 — Ledger vs. mouvements metier attendus

Chaque ligne du ledger a un `type` (`DEPOSIT`, `WITHDRAWAL`, `RESERVATION`, `RELEASE`,
`ADJUSTMENT`, `REFUND`) et, pour les mouvements lies a un ordre, un `orderId`. Pour la
journee ecoulee :

1. Lister les transactions du ledger de la journee :
   `GET /api/admin/treasury/accounts/{currency}/transactions` (paginer jusqu'a couvrir
   la fenetre de temps voulue — le tri est du plus recent au plus ancien).
2. Croiser avec :
   - les `Order` passes en `SETTLED` ce jour-la (chacun doit avoir une `RESERVATION`
     puis soit une consommation implicite via `Settlement`, soit une `RELEASE` s'il a
     ete annule/expire) ;
   - les `Refund` passes en `PROCESSED` ce jour-la (chacun doit avoir exactement une
     ligne `REFUND`) ;
   - les `DEPOSIT`/`ADJUSTMENT` manuels effectues par un admin ce jour-la (ils
     n'ont pas de garde-fou SQL d'unicite — l'`Idempotency-Key` est leur seule
     protection contre un double enregistrement reel, voir `AdminTreasuryController`
     javadoc ligne 42-45 — verifier qu'aucun couple montant/motif/horodatage suspect
     ne se repete sans justification).
3. Tout `orderId` de commande `SETTLED`/annulee/remboursee SANS mouvement de ledger
   correspondant, ou tout mouvement de ledger SANS commande/remboursement
   correspondant, est une anomalie a documenter et investiguer.

## 4bis. Etape 2bis — Coherence Order ↔ Payment ↔ Settlement ↔ Refund

Complement de l'etape 2, du point de vue des quatre entites metier plutot que du seul
ledger — quatre categories d'anomalie a rechercher explicitement (audit de fermeture
2026-09, Phase 23) :

1. **Missing settlement** — un `Order` en statut `SETTLED` sans `Settlement` associe
   en statut `EXECUTED` (`GET /api/admin/settlements?orderId=...` ou parcours de la
   liste paginee des settlements du jour). Ne devrait jamais arriver : `Order` ne
   passe `SETTLED` qu'en reaction a l'execution reussie du `Settlement` correspondant.
   Une occurrence indique soit une ecriture directe en base hors API, soit un bug a
   escalader immediatement.
2. **Refund mismatch** — un `Refund` en statut `PROCESSED` dont le paiement associe
   n'est PAS en statut `CONFIRMED`, ou dont l'ordre associe a neanmoins ete `SETTLED`
   APRES le traitement du refund (devrait etre impossible : `Settlement.execute()`
   est bloque explicitement si un refund a deja ete traite sur le meme paiement — une
   occurrence signifierait que cette garde a ete contournee, a traiter en CRITICAL).
3. **Unexpected pending state** — un `Payment`, `Settlement` ou `Refund` reste en
   statut `PENDING` au-dela d'un delai raisonnable (proposition pour le pilote :
   24h — DECISION REQUIRED, aucune exigence metier formelle n'existe encore sur ce
   delai). Chaque cas correspond a un scenario du Runbook ("Paiement bloque",
   "Reglement bloque") — y renvoyer plutot que d'improviser une action ici.
4. **Unusual adjustment** — tout mouvement `ADJUSTMENT`/`DEPOSIT` manuel (via
   `POST /api/admin/treasury/adjust|deposit`) dont le motif ne renvoie a aucun ticket
   connu, ou dont le montant est disproportionne par rapport a l'activite habituelle.
   Sans `Idempotency-Key`, ces deux endpoints n'ont aucun filet SQL contre un double
   enregistrement reel (voir `AdminTreasuryController`) : un doublon suspect de
   montant/motif/horodatage rapproches est le signal le plus probable d'un incident
   operationnel (double-clic, script rejoue) plutot que d'une fraude.

## 5. Etape 3 — Coherence `available()` vs. activite en cours

`available() = balance - reservedBalance`. Un `reservedBalance` XOF ou CNY qui reste
elevé alors qu'aucune commande n'est en attente de reglement (`AWAITING_PAYMENT`/
`RESERVED`) indique une reservation orpheline (candidate typique : un ordre qui aurait
du expirer/etre libere mais ne l'a pas ete — verifier `OrderExpirationScheduler`,
Runbook scenario "Order expire bloque").

## 6. Ce qu'il faut consigner

Pour chaque execution de cette procedure, consigner (hors du systeme, ex. tableur ou
ticket) :
- date/heure d'execution, operateur ;
- solde `balance`/`reservedBalance` observe par devise ;
- toute anomalie relevee aux etapes 3-5, avec le(s) `orderId`/`transactionId`
  concerne(s) ;
- action prise (aucune / ticket ouvert / correction via `/adjust` avec motif).

DECISION REQUIRED : aucun outillage n'automatise aujourd'hui ce rapprochement (pas de
job planifie, pas d'export). Une automatisation (ex. job quotidien qui exécute les
etapes 1 et 3 et alerte en cas de divergence) est une amelioration future raisonnable,
mais represente un choix d'investissement a arbitrer, pas une implementation
"minimale et justifiee" au sens des regles de cette mission.

## 7. Correction d'une anomalie confirmee

Uniquement via `POST /api/admin/treasury/adjust`, avec :
- un motif (`reason`) explicite et tracable renvoyant au ticket d'investigation ;
- un en-tete `Idempotency-Key` (fortement recommande, cf. javadoc du controleur) ;
- **jamais** de correction directe en base de donnees hors API : cela contournerait
  a la fois l'ecriture du ledger et les contraintes CHECK, et romprait l'invariant
  "chaque mutation de solde committe avec sa ligne de ledger".
