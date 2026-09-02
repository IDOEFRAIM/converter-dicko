# Audit métier — Passe 2 (revue critique approfondie, 2026-09-02)

> Deuxième audit, orienté « système financier » : CORRECTION MÉTIER + COHÉRENCE + ATOMICITÉ +
> IDEMPOTENCE + CONCURRENCE + TRAÇABILITÉ + RÉSILIENCE + EXTENSIBILITÉ. Aucun correctif de la
> passe 1 n'est retiré. Le code réel est la source de vérité (prime sur la documentation).
> Format : **EXISTANT / PROBLÈME / RISQUE / RECOMMANDATION / IMPACT**.
> Suite de [AUDIT_BUSINESS_LOGIC.md](AUDIT_BUSINESS_LOGIC.md) et de [FINANCIAL_INVARIANTS.md](FINANCIAL_INVARIANTS.md).

---

## A. Vérification des changements de la passe 1

| Élément passe 1 | Vérification passe 2 (lecture du code) | Verdict |
|---|---|---|
| Garde `TreasuryAccount.consume/release` (`amount ≤ reservedBalance`) | Garde bien **avant** toute mutation, lève `INSUFFICIENT_TREASURY`. | ✅ conforme |
| Garde `Wallet.consume/release` | Idem, `INSUFFICIENT_WALLET_BALANCE`. | ✅ conforme |
| `SettlementService.execute` conditionne `consume` à `order.isTreasuryReserved()` | Confirmé (avec `WARN` si non réservé). | ✅ conforme — `isTreasuryReserved()` qualifié en §Analyses |
| Framework `common/idempotency` | `tryInsert`/`findExisting` en deux transactions distinctes (piège PostgreSQL évité). **`complete()` en `REQUIRES_NEW` séparé de l'action métier** → fenêtre de crash. | ⚠️ à durcir (P2-3) |
| `V15` (`request_hash` → `VARCHAR(64)`) | Appliquée, `ApplicationStartupIT` valide (`ddl-auto=validate`). | ✅ conforme |
| `RateProvider.isAvailable(...)` | Présent, testé unitairement, non câblé dans `PreferredRateService` (raison Hibernate documentée). | ✅ conforme — mais aucune politique de fraîcheur (P2-7) |
| `NotificationService.create` en `REQUIRES_NEW` + exception absorbée | Confirmé. | ✅ conforme — garantie à documenter (§Analyses) |
| Audit `SETTING_UPDATED` | Confirmé (`{previousValue, newValue}`). | ✅ conforme |

---

## B. Problèmes trouvés

### P2-1 · Réservations de trésorerie bloquées par des ordres abandonnés — 🔴 Critique

- **EXISTANT** : à la création d'un `Order`, si `TREASURY_RESERVE_ON_ORDER=true` (défaut), `amountCny` est **réservé** sur le compte CNY. L'ordre passe `AWAITING_PAYMENT`. Il n'existe **aucun mécanisme d'expiration d'ordre** : `OrderStatus.EXPIRED` est dans l'enum et autorisé par `OrderStateMachine` (`AWAITING_PAYMENT → EXPIRED`), mais **rien ne déclenche jamais cette transition**. `ORDER_AUTO_EXPIRE_ENABLED` (seed `V4`, valeur `true`) n'est **lu par aucun code**. `orders` n'a plus de colonne de deadline (retirée en V9). Seul `PreferredRateScheduler` expire les réservations *Wallet* (à J+3).
- **PROBLÈME** : un client qui crée un ordre puis ne paie jamais et n'annule jamais **immobilise définitivement** la liquidité CNY réservée. `MAX_OPEN_ORDERS_PER_USER` (défaut 3) limite le nombre par utilisateur mais N clients abandonnant chacun 3 ordres épuisent la trésorerie sans qu'aucun décaissement n'ait lieu.
- **RISQUE** : 🔴 fuite de liquidité, blocage progressif du corridor. Scénario ciblé par la mission §7 (« ressources réservées bloquées parce que personne ne touche à la ressource »).
- **RECOMMANDATION** : mécanisme d'expiration d'ordre **sûr et idempotent** :
  - colonne `orders.payment_deadline_at` (migration `V16`), fixée à la création = `created_at + ORDER_PAYMENT_WINDOW_MINUTES` — **stockée**, donc immuable par ordre (un changement de paramètre ne ré-expire jamais rétroactivement — cohérent avec « données financières historiques immuables ») ;
  - nouveau paramètre `ORDER_PAYMENT_WINDOW_MINUTES` (défaut 720 = 12 h, configurable/audité/testable) ;
  - `OrderExpirationScheduler` (`fixedDelay`, même patron que `PreferredRateScheduler`) → `OrderExpirationService.expireOverdue(asOf)` : par ordre, `findByIdForUpdate` + re-vérif `status == AWAITING_PAYMENT` + `asOf ≥ deadline` + `transition(→ EXPIRED)` + `releaseReservationIfNeeded` + audit `ORDER_EXPIRED` + notification ; **piloté par `ORDER_AUTO_EXPIRE_ENABLED`** (le paramètre orphelin est enfin branché).
- **IMPACT** : 1 migration (`V16` : colonne + backfill + `NOT NULL`), 1 clé `SettingKey` + seed, 1 service + 1 scheduler, `Order` constructeur (+1 param → 2 sites), 1 valeur d'enum `AuditAction` (`ORDER_EXPIRED` existe déjà), tests. `expireOverdue(Instant asOf)` prend l'instant en paramètre → testable sans attendre 12 h ni toucher d'état global.

### P2-2 · Montant du paiement non validé contre le montant attendu — 🔴 Critique

- **EXISTANT** : `PaymentService.submit` enregistre `receivedAmountXof` **tel quel** depuis le client. `confirm` fait `treasuryService.deposit(XOF, payment.getReceivedAmountXof(), ...)`. Le `amountCny` réservé/consommé au settlement provient du **`Quote`** (`order.amountCny`), pas du montant reçu.
- **PROBLÈME** : rien n'empêche un client de déclarer `receivedAmountXof = 1` pour un ordre de 100 000 XOF. Si un administrateur confirme (erreur humaine, preuve ambiguë), la plateforme encaisse 1 XOF mais décaissera `amountCny` calculé sur 100 000 → **perte de change directe**. Aucun signal système ne distingue paiement exact / sous-paiement / sur-paiement.
- **RISQUE** : 🔴 perte financière directe, non détectée par le système (repose entièrement sur la vigilance de l'admin).
- **RECOMMANDATION** : `submit` rejette si `receivedAmountXof` s'écarte de `expectedAmountXof` (= `order.amountXof`) au-delà d'une tolérance : nouveau paramètre `PAYMENT_AMOUNT_TOLERANCE_XOF` (défaut `0` = exact). Sur-paiement **et** sous-paiement au-delà de la tolérance rejetés (flux manuel : le client doit payer le montant exact). Nouveau code `PAYMENT_AMOUNT_MISMATCH` (400). Montant attendu et montant reçu restent tous deux persistés (traçabilité).
- **IMPACT** : 1 clé `SettingKey` + seed (`V16`), 1 `ErrorCode`, garde dans `submit`, tests. Défaut `0` ⇒ aucun test de flux existant cassé (ils paient déjà le montant exact).

### P2-3 · Idempotence : `complete()` non atomique avec l'effet métier — 🟠 Élevé

- **EXISTANT** : `IdempotencyGuard.guard` : (1) `tryInsert` (`REQUIRES_NEW`, commit de la ligne *pending*) → (2) `action.get()` (transaction métier propre, commit) → (3) `complete()` (`REQUIRES_NEW`, commit de la réponse).
- **PROBLÈME** : crash du processus **entre (2) et (3)** → l'effet métier est committé (ordre créé, trésorerie créditée…) mais la clé reste `pending`. Tout rejeu avec la même clé → `409 IDEMPOTENT_REQUEST_IN_PROGRESS` **définitivement** (aucune purge). Le client ne peut plus savoir que sa requête a abouti. Exactement le scénario « timeout côté client puis retry » (mission §15).
- **RISQUE** : 🟠 blocage permanent d'une clé après incident ; incohérence perçue (effet appliqué mais rapporté « en cours »).
- **RECOMMANDATION** : rendre `guard()` `@Transactional` (REQUIRED) et `complete()` `REQUIRED` (rejoint la transaction de `guard`) ⇒ **effet métier + enregistrement de la réponse committent atomiquement, ou pas du tout**. `tryInsert` reste `REQUIRES_NEW` (la capture doit être durable *avant* l'action pour revendiquer la clé). Nouvelle unique fenêtre de crash : entre le commit de `tryInsert` et le commit de `guard` → **aucun effet métier appliqué**, clé `pending` orpheline (rejeu → 409, mais rien de financier n'a eu lieu) — strictement sûr. Décision assumée : **les échecs ne sont pas mis en cache** (`releasePending` sur exception) — un rejeu ré-exécute et obtient la même erreur déterministe ; mettre en cache une erreur figerait un échec potentiellement transitoire.
- **IMPACT** : annotations sur 2 méthodes + Javadoc. Ajout d'un test « échec puis rejeu réussi ».

### P2-4 · Ledger trésorerie : une réservation peut être résolue deux fois — 🟠 Élevé

- **EXISTANT** : `uq_treasury_tx_order_type UNIQUE (order_id, type) WHERE order_id IS NOT NULL AND type IN ('RESERVATION','RELEASE','WITHDRAWAL')` (V2). Empêche deux `RESERVATION`, deux `RELEASE`, deux `WITHDRAWAL` pour un même ordre — **mais pas un `RELEASE` suivi d'un `WITHDRAWAL`** (types distincts) pour le même ordre.
- **PROBLÈME** : une réservation d'ordre doit être résolue **exactement une fois** — soit libérée, soit consommée, jamais les deux, jamais deux fois. Aujourd'hui, seule la machine d'état de l'ordre l'empêche (un ordre `CANCELLED`/`REJECTED` ne peut pas atteindre `PROCESSING` puis `execute`). Aucun filet SQL.
- **RISQUE** : 🟠 si un chemin futur (ou un bug) contournait la machine d'état, la trésorerie serait doublement décrémentée pour un ordre.
- **RECOMMANDATION** : `V16` remplace `uq_treasury_tx_order_type` par deux index partiels plus stricts :
  - `uq_treasury_tx_reservation_per_order` : `UNIQUE (order_id) WHERE order_id IS NOT NULL AND type = 'RESERVATION'`
  - `uq_treasury_tx_resolution_per_order` : `UNIQUE (order_id) WHERE order_id IS NOT NULL AND type IN ('RELEASE','WITHDRAWAL')`
  La deuxième garantit qu'un ordre a **au plus une** résolution de réservation, quelle qu'elle soit.
- **IMPACT** : `V16` (`DROP INDEX` + 2 `CREATE UNIQUE INDEX`). Aucun code applicatif ne change. Les tests existants (`doubleReservation…`, `doubleConsumption…`) restent verts (le second mouvement du même type viole toujours).

### P2-5 · `TreasuryService.adjust` : correction négative sous le solde réservé — 🟡 Moyen

- **EXISTANT** : `adjust` vérifie `account.getBalance().add(delta).signum() >= 0`, **pas** `>= reservedBalance`.
- **PROBLÈME** : `adjust(CNY, -X)` amenant `balance` sous `reserved_balance` → mutation puis `flush` → violation `ck_treasury_accounts_reserved_le_balance` → `409 DUPLICATE_RESOURCE` (code trompeur, mission §23).
- **RISQUE** : 🟡 message d'erreur trompeur ; pas de corruption (la contrainte SQL tient).
- **RECOMMANDATION** : `adjust` vérifie aussi `newBalance >= account.getReservedBalance()` et lève `INSUFFICIENT_TREASURY`. Test.
- **IMPACT** : garde dans `adjust`, 1 test.

### P2-6 · Codes d'erreur imprécis sur violation de contrainte — 🟡 Moyen

- **EXISTANT** : `uq_orders_quote`, `uq_payments_order`, `uq_payments_txref`, `uq_settlements_order` → toutes traduites en `DUPLICATE_RESOURCE` (409). `DUPLICATE_TRANSACTION_REFERENCE` n'est **jamais levé**.
- **PROBLÈME** : sous vraie concurrence, la pré-vérification applicative (`existsByQuoteId`, `existsByOrderId`) est franchie par les deux requêtes, l'une gagne, l'autre reçoit `DUPLICATE_RESOURCE` au lieu du code métier attendu. Mission §23.
- **RISQUE** : 🟡 contrat d'erreur non déterministe (dépend du timing).
- **RECOMMANDATION** :
  - `PaymentService.submit` : pré-vérifier `paymentRepository.existsByMethodAndTransactionReference(...)` → `DUPLICATE_TRANSACTION_REFERENCE` ; `catch (DataIntegrityViolationException)` autour de l'`INSERT` → retraduire.
  - `OrderService.create` : `catch` sur `uq_orders_quote` → `QUOTE_ALREADY_USED`.
  - `SettlementService.create` : `catch` sur `uq_settlements_order` → `INVALID_SETTLEMENT_STATE`.
- **IMPACT** : `catch` ciblés + une méthode de repo. Tests de concurrence (§D).

### P2-7 · Fraîcheur du taux : un taux « courant » peut être arbitrairement ancien — 🟡 Moyen

- **EXISTANT** : `ManualRateProvider.currentRate` renvoie la ligne `rate_sources` avec `effective_to IS NULL`, **quel que soit son âge**.
- **PROBLÈME** : pour un corridor FX, un taux périmé = devis mal pricés. Mission §3 : distinguer `CURRENT` / `STALE` / `UNAVAILABLE` ; politique de fraîcheur **configurable, auditée, testable**.
- **RISQUE** : 🟡 mispricing silencieux si l'admin oublie de republier.
- **RECOMMANDATION** : nouveau paramètre `RATE_MAX_AGE_MINUTES` (défaut `0` = **désactivé**, rétrocompatible ; valeur > 0 active la politique) :
  - `CURRENT` : `effective_to IS NULL` **et** (`RATE_MAX_AGE_MINUTES = 0` **ou** `age(effective_from) ≤ RATE_MAX_AGE_MINUTES`) ;
  - `STALE` : `effective_to IS NULL` **et** `age > RATE_MAX_AGE_MINUTES` (politique active) ;
  - `UNAVAILABLE` : aucune ligne `effective_to IS NULL`.
  `currentRate` lève `RATE_SOURCE_UNAVAILABLE` (message « taux périmé ») quand `STALE` ; `isAvailable` renvoie `false` quand `STALE`/`UNAVAILABLE`. Chaque rejet pour péremption est journalisé (`WARN`).
- **IMPACT** : 1 clé `SettingKey` + seed (`V16`) ; `ManualRateProvider` dépend désormais de `SettingsService` + `Clock` (reste sans dépendance à `quote`/`order`). Tests.

---

## C. Analyses demandées — réponses techniques

### §7 · Expiration — réponse claire

| Ressource | Réserve quelque chose ? | Expiration aujourd'hui | Verdict passe 2 |
|---|---|---|---|
| `Quote` | Non. | Paresseuse, **cohérente et testée**. | ✅ suffisant — pas de scheduler (rien n'est bloqué). |
| `Order` | **Oui** — `RESERVATION` CNY. | **Aucune.** `EXPIRED` inatteignable. | 🔴 **insuffisant** → scheduler ajouté (P2-1). |
| `PreferredRateRequest` | **Oui** — `RESERVE` Wallet. | `PreferredRateScheduler` → `expire` à J+3 + `release`. | ✅ suffisant. |
| `Exchange` | Non (déjà débité). | `progressOne` termine à T+2 h max. | ✅ suffisant. |

### §11 · `order.isTreasuryReserved()` : source de vérité ou cache ?

**Cache dénormalisé, maintenu transactionnellement cohérent.** Source de vérité = ledger
`treasury_transactions` (présence d'une ligne `RESERVATION` pour `order_id` non résolue par un
`RELEASE`/`WITHDRAWAL`). Le booléen `orders.treasury_reserved` est écrit **dans la même
transaction** que la ligne `RESERVATION` (création) et que la ligne `RELEASE` (annulation/rejet/
expiration) — il ne peut jamais diverger au commit. Conservé pour éviter une agrégation sur le
ledger à chaque lecture d'ordre. Documenté (`Order`, `SettlementService.execute`,
`FINANCIAL_INVARIANTS.md` §4).

### §13 · `TreasuryAccount` / `Wallet` : source de vérité, projection ou cache ?

**Projection matérialisée** du ledger append-only, maintenue transactionnellement : chaque
mutation de solde écrit **dans la même transaction** une ligne de ledger portant `balance_after` /
`reserved_after`. Le ledger est la **source de vérité d'audit** ; le solde matérialisé est la
valeur de travail (verrous pessimistes, `CHECK`). Réconciliation possible sans rejouer tout
l'historique grâce à `*_after`. Documenté en Javadoc de `TreasuryAccount` / `Wallet` +
`FINANCIAL_INVARIANTS.md` §7.

### §18 · Niveau de garantie des notifications

**Best-effort, canal `IN_APP` uniquement.** Jamais une condition de succès d'une opération
financière (`REQUIRES_NEW` + exception absorbée). Une perte n'est **pas silencieuse** :
`NotificationService.create` journalise l'échec en `ERROR` avec `type` + `userId` — récupérable
depuis les logs. Ce n'est **pas** un *outbox* durable. Niveau suffisant pour un MVP `IN_APP` ;
un *transactional outbox* est de la dette explicite (§E). Documenté en Javadoc.

### §22 · Observabilité — `correlationId` ?

**Non ajouté.** `traceId` (par requête, présent dans `ErrorResponse` et les logs) couvre la
corrélation d'un incident ponctuel. Le suivi d'un **flux métier long** est déjà assuré par le
chaînage d'entités : `quote.id → order.quote_id → payment.order_id → settlement.order_id →
treasury_transactions.order_id`, tous requêtables, et par `audit_logs (entity_type, entity_id)`.
Dans un monolithe synchrone (1 transaction par requête), un `correlationId` distinct apporterait
une valeur marginale pour un coût réel — **écarté** (mission §22). Réévaluable si des traitements
asynchrones multi-requêtes apparaissent.

### §25 · Contrat d'API — fuite de données internes ?

`QuoteResponse` **n'expose pas** `marketRate` ni `marginPercentage`. `OrderDetailResponse` /
`PaymentResponse` / `SettlementResponse` / `TreasuryAccountResponse` / `WalletResponse`
n'exposent que des champs pertinents pour l'appelant. `password_hash` porté par aucun DTO. Aucun
`storage_key` ni chemin interne exposé (preuves servies via endpoint dédié
`Content-Disposition: attachment`). **Aucune fuite identifiée.**

---

## D. Tests de concurrence ajoutés

| Scénario | Attendu | Fichier |
|---|---|---|
| Create Order x2 (même devis accepté) | Exactement un `201`, l'autre `409 QUOTE_ALREADY_USED` ; **une seule** ligne `RESERVATION` CNY. | `OrderConcurrencyIT` (nouveau) |
| Settlement execute x2 (même règlement) | Un `EXECUTED`, l'autre `409 INVALID_SETTLEMENT_STATE` ; **une seule** ligne `WITHDRAWAL`. | `SettlementFlowIT` (ajout) |
| `TreasuryService.adjust` sous le réservé | `409 INSUFFICIENT_TREASURY`, compte inchangé. | `TreasuryServiceIT` (ajout) |
| `Order` expiré → réservation libérée | Ordre `EXPIRED`, `reserved_balance` CNY restauré, idempotent (2ᵉ passage = no-op). | `OrderExpirationServiceIT` (nouveau) |
| Paiement `received < expected` (tolérance 0) | `400 PAYMENT_AMOUNT_MISMATCH`, aucun paiement créé, ordre reste `AWAITING_PAYMENT`. | `PaymentFlowIT` (ajout) |
| Idempotence : échec métier puis rejeu réussi | 1ᵉʳ appel `409`, la capture est libérée, 2ᵉ appel (conditions corrigées) `201`. | `IdempotencyGuardIT` (ajout) |
| `RateEngine` fee fixe + % combinés / RECEIVE_CNY | Frais appliqués une seule fois, sur le bon montant, arrondis centralisés. | `RateEngineTest` (ajout) |
| Taux périmé (`RATE_MAX_AGE_MINUTES` > 0) | `currentRate` → `503 RATE_SOURCE_UNAVAILABLE` ; `isAvailable` → `false`. | `RateStalenessIT` (nouveau) |

---

## E. Dette technique restante (vrais sujets)

1. **Purge des clés d'idempotence expirées** — `expires_at` écrit mais jamais balayé. Un futur `@Scheduled` quotidien (`DELETE WHERE expires_at < now()`) fermerait la (rare) fenêtre de clé `pending` orpheline après crash. Non bloquant.
2. **Transactional outbox pour les notifications** — si une notification devient « à garantie de livraison », table outbox écrite dans la transaction métier + relais. Pas nécessaire pour `IN_APP` best-effort.
3. **`AdminUserSummary.orderCount` / `totalAmountCfa`** — toujours codés en dur à 0 (non financier, non demandé).
4. **Multi-instance** — `RateLimitFilter` (Caffeine local) et les `@Scheduled` sans verrou distribué (ShedLock) supposent une instance unique. Documenté ; à traiter au moment d'un déploiement horizontal.
5. **`RateProvider` multi-sources** — `MarketRateProvider` / `P2PRateProvider` restent des noms réservés ; l'agrégation (médiane, etc.) n'a de sens qu'avec ≥ 2 sources réelles.
