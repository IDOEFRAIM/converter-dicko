# Converter — Infrastructure de paiement Burkina Faso ↔ Chine (XOF ↔ RMB)

> **Document vivant, en trois parties.**
>
> - **Partie 0 — État réel du système** (ci-dessous) : décrit ce qui est **effectivement construit et testé** au 2026-09-01. C'est la référence de **statut** faisant autorité. Le détail exhaustif du backend (classes, schéma, endpoints) vit dans un document dédié : **[BACKEND.md](BACKEND.md)**.
> - **Partie I — Révision architecturale (Phase 2.5)** : la version faisant autorité pour le **raisonnement de conception du domaine métier** (séparation taux marché/client, `Quote`, `Settlement`, trésorerie, risque, KPI). Produite après étude de marché, **avant** le code des modules métier. Sa **roadmap par phases (§T) et ses formulations de statut sont dépassées** — se référer à la Partie 0.
> - **Partie II — Architecture initiale (Phase 1)** : conservée comme référence historique. Sous-sections **`[SUPERSEDED]`** remplacées par la Partie I ; le reste (sécurité applicative, garanties de concurrence, décisions Java/Maven) demeure valide.
>
> **Comment lire ce document.** Pour *savoir ce qui existe* → Partie 0 + BACKEND.md. Pour *comprendre pourquoi le domaine est modélisé ainsi* → Partie I. Pour *l'historique des décisions* → Partie II.

---

# Partie 0 — État réel du système (au 2026-09-01)

> Cette partie **remplace toute formulation de statut ou de roadmap** des Parties I et II
> (notamment §T « Roadmap révisée » et §K « Plan d'implémentation »). Le découpage par
> phases a servi de plan de construction ; il n'a plus valeur de suivi.

## 0.A Ce qui est construit

Le backend est un **monolithe modulaire Spring Boot 3.5 / Java 21** avec **PostgreSQL 16**,
**16 migrations Flyway** (`V1`→`V16`) et **183 tests** (unitaires + intégration Testcontainers).
Tous les modules du domaine métier prévus par la Partie I sont livrés, **plus** trois modules
non anticipés (`wallet`, `preferredrate`, `notification`), **plus** le socle `common/idempotency`.

> **Passes de durcissement métier (2026-09-01 → 2026-09-02).** Deux audits complets de la logique
> financière (EXISTANT / PROBLÈME / RISQUE / RECOMMANDATION / IMPACT), suivis de correctifs testés.
> **Passe 1** : gardes d'invariant `consume`/`release` (`TreasuryAccount`, `Wallet`), consommation
> de trésorerie conditionnée à une réservation réelle, **idempotence HTTP** sur 5 endpoints,
> notifications en `REQUIRES_NEW`, audit `SETTING_UPDATED`. **Passe 2** : **expiration d'ordre**
> (scheduler → libère les réservations CNY bloquées), **validation du montant du paiement**
> (anti sous-paiement), idempotence rendue **atomique** avec l'effet métier, index SQL
> « réservation résolue exactement une fois », précision des codes d'erreur, **politique de
> fraîcheur du taux** (CURRENT/STALE/UNAVAILABLE, configurable).
> Rapports : **[AUDIT_BUSINESS_LOGIC.md](AUDIT_BUSINESS_LOGIC.md)**,
> **[AUDIT_BUSINESS_LOGIC_PASS2.md](AUDIT_BUSINESS_LOGIC_PASS2.md)**,
> invariants consolidés : **[FINANCIAL_INVARIANTS.md](FINANCIAL_INVARIANTS.md)**.

| Domaine | Module(s) | Entités clés | État |
|---|---|---|---|
| Fondations | `common`, `config`, `security`, `storage`, `settings`, `audit` | `User`, `Role`, `SystemSetting`, `AuditLog` | ✅ livré |
| Comptes & accès | `auth`, `user`, `admin` | `User` (+ blocage) | ✅ livré |
| Taux & tarification | `rate` (`RateProvider`, `RateEngine`) | `RateSource` (append-only) | ✅ livré — `ManualRateProvider` seul actif |
| Devis | `quote` | `Quote` (snapshot immuable, verrou 30 min, expiration paresseuse) | ✅ livré |
| Ordres | `order` (`OrderStateMachine`) | `Order`, `Beneficiary`, `OrderStatusHistory` | ✅ livré |
| Paiement client XOF | `payment` | `Payment`, `PaymentProof` | ✅ livré — manuel, `MOBILE_MONEY` seul actif |
| Règlement CNY | `settlement` | `Settlement`, `SettlementProof` | ✅ livré — exécution 100 % manuelle |
| Trésorerie | `treasury` | `TreasuryAccount`, `TreasuryTransaction` (ledger) | ✅ livré — `Available/Reserved/Consumed/Released` |
| Solde interne client | `wallet` | `Wallet`, `WalletTransaction` (ledger) | ✅ livré — **hors périmètre Partie I** |
| Taux préférentiel | `preferredrate` (+ scheduler) | `PreferredRateRequest`, `Exchange` | ✅ livré — **hors périmètre Partie I** |
| Notifications | `notification` | `Notification` (`IN_APP` uniquement) | ✅ livré — **hors périmètre Partie I** |

## 0.B Ce qui n'est PAS construit (prévu Partie I, absent du code)

- **Module `risk` / `compliance`** (`RiskFlag`, file de revue) — §J, §K.12 : aucune table, aucun code, aucune valeur d'enum `RISK_*`.
- **Module `dashboard` / KPI admin** — §R : aucun `AdminDashboardController`.
- **`MarketRateProvider`, `P2PRateProvider`** — noms réservés dans l'enum `RateProviderType`, non implémentés.
- **Canaux de paiement au-delà de `MOBILE_MONEY`** (`WAVE`, `BANK_TRANSFER`) — valeurs d'enum réservées, non activées.
- ~~**Idempotence HTTP** (`Idempotency-Key`) jamais exploitée.~~ **Construit** lors de la passe de durcissement métier (2026-09-01) : module `common/idempotency`, migration `V15`, en-tête optionnel honoré sur `POST /api/v1/orders`, `POST /api/v1/orders/{orderId}/payments`, `POST /api/admin/settlements/{id}/execute`, `POST /api/admin/treasury/{deposit,adjust}`. Les index uniques SQL restent le filet de dernier recours. Détail : [BACKEND.md §7.5](BACKEND.md#75-idempotence-http-commonidempotency), rationale : [AUDIT_BUSINESS_LOGIC.md §17](AUDIT_BUSINESS_LOGIC.md).
- **Segmentation client** (`customer_segment`) — §D : colonne non ajoutée.
- **Jobs d'expiration** d'`Order` et de `Quote` — l'expiration est **paresseuse** (calculée à la lecture / transition). `OrderStatus.EXPIRED` est donc inaccessible en pratique ; `ORDER_AUTO_EXPIRE_ENABLED` n'est jamais lu.

La liste complète des écarts conception ↔ code est tenue dans **[BACKEND.md §20](BACKEND.md#20-écarts-connus--dette-technique)**.

## 0.C Écarts de modèle par rapport à la Partie I

| Sujet | Partie I | Code livré |
|---|---|---|
| Nom des colonnes monétaires | `amountCfa`, `feeCfa`… | `amount_xof`, `fee_xof`… (XOF explicite) |
| Statuts `Payment` | `SUBMITTED → VERIFIED \| REJECTED` | `SUBMITTED → CONFIRMED \| REJECTED` |
| `Quote` : statuts | `ACTIVE → CONFIRMED \| EXPIRED` | `ACTIVE → ACCEPTED \| EXPIRED \| CANCELLED` |
| `Quote` : sens | `amountCfa` uniquement | bidirectionnel : `SEND_XOF` **ou** `RECEIVE_CNY` |
| `Quote` : consommation | `409 RATE_CHANGED` à la confirmation | non implémenté ; le devis fige le taux dès sa création, garde `uq_orders_quote` |
| Verrou 30 min | démarre à la confirmation du devis | démarre à la **création** du devis (`expires_at = created_at + RATE_LOCK_DURATION_MINUTES`) |
| `Settlement` : `amountCny` | possible écart avec `order.amountCny` | strictement copié de l'ordre |
| `treasury_transactions` | pas de type `CONSUMPTION` | idem — `consume()` écrit un `WITHDRAWAL` lié à `order_id` |
| Préfixe d'URL | `/api/...` uniforme | `/api/...` (auth/settings/admin) **et** `/api/v1/...` (quote/order/payment/wallet/preferred-rate/notification) |

## 0.D Modules non prévus par la Partie I

- **`wallet`** — solde interne XOF par utilisateur (modèle `TreasuryAccount` scopé utilisateur : `Available = balance − reserved`). Ledger append-only. **Pas un compte bancaire** ; aucun canal d'alimentation réel branché (`deposit` existe en service, pas en endpoint).
- **`preferredrate`** — « échanger X XOF quand le taux atteint la cible ». La demande **réserve immédiatement** le montant sur le `Wallet` ; un scheduler (`fixedDelay` ~30 s) évalue chaque demande `ACTIVE` contre le taux client courant (`ManualRateProvider` + marge) et soit la déclenche (`Exchange`, cycle de progression simulé T+45 / T+90 / T+2 h), soit l'expire à J+3 (fonds restitués). Aucune source de taux externe.
- **`notification`** — messages internes `IN_APP` uniquement (9 types), créés par `quote`/`payment`/`preferredrate`. Aucun SMS/WhatsApp/email/push.

## 0.E Points de la Partie I confirmés par le code

- Monolithe modulaire, une base PostgreSQL, transactions ACID partagées.
- `BigDecimal` exclusivement ; UTC de bout en bout ; `Clock` injectable.
- Séparation **taux marché (`market_rate`) / marge (`margin_percentage`) / taux client (`customer_rate`)** — trois colonnes distinctes dans `quotes`, jamais fusionnées ; `market_rate`/`margin_percentage` **jamais exposés au client** (`QuoteResponse` ne les porte pas).
- `RateProvider` = port ; le domaine ne dépend jamais d'une source concrète.
- `Order` référence un `Quote` (`quote_id`), jamais un taux brut ; colonnes financières = copie figée, jamais recalculées.
- `Settlement` distinct de `Payment` ; décaissement CNY 100 % manuel, seulement tracé.
- Trésorerie : verrou pessimiste + `CHECK (reserved_balance <= balance)` + index unique `(order_id, type)` contre le double décaissement.
- Sécurité : RBAC double barrière, 404 (jamais 403) sur ressource d'autrui, JWT stateless avec revalidation du compte à chaque requête, anti brute-force par IP, CORS strict.

## 0.F Évolution Burkina Faso ↔ Chine (Phases 1-8, 2026-09-03)

> Complète la table de 0.A sans la réécrire — cette évolution s'ajoute au socle déjà stable
> au 2026-09-01, décrit ci-dessus. Backend passé de 20 à 26 migrations Flyway au fil de ces
> huit phases (voir les rapports de clôture de chaque phase pour le détail exact des tests).
> Phase 3 et Phase 7 n'ont nécessité aucune migration. Suite complète à la clôture de la
> Phase 8 : **437 tests, 0 échec, 0 erreur** (405 avant la Phase 8, +32 dans la Phase 8).

- **Phase 1 — `supplier`** (`V21`) : carnet de fournisseurs/bénéficiaires réutilisable, isolé
  par `owner_user_id`. **Distinct** du snapshot immuable `order.domain.Beneficiary` (inchangé) :
  un `Supplier` n'est jamais la source de vérité d'un règlement déjà effectué, ses champs ne
  sont copiés dans un `Beneficiary` qu'au moment de la création d'un `Order`.
- **Phase 2 — lien traçable + motif** (`V22`, `V23`) : `orders.supplier_id` (FK nullable, sans
  cascade — un fournisseur désactivé reste référencé par l'historique) et
  `orders.purpose`/`orders.purpose_details` (nullable). `OrderService.create()` accepte
  désormais `supplierId` **ou** `beneficiary` (exactement l'un des deux), jamais les deux
  comme source du même snapshot.
- **Phase 3 — suivi de transaction** : `GET /api/v1/orders/{id}/tracking`, vue agrégée en
  lecture seule — **aucune seconde machine d'état**. Documentation ci-dessous.
- **Phase 4 — payer à nouveau** : `POST /api/v1/suppliers/{id}/pay-again`, orchestration pure
  (`RepeatPaymentService`) au-dessus de `QuoteService.create`/`accept` et `OrderService.create`
  — **aucune logique financière propre**, aucun taux/frais/montant CNY d'une transaction passée
  jamais réutilisé. Documentation ci-dessous.
- **Phase 5 — historique public du taux client** : `GET /api/v1/rates/history`
  (`public_rate_snapshots`, `V24`) — série temporelle append-only du seul `customerRate`,
  jamais le `breakEvenRate` interne. Documentation ci-dessous.
- **Phase 6 — alertes de taux** : `POST /api/v1/rate-alerts`, `GET /api/v1/rate-alerts`,
  `POST /api/v1/rate-alerts/{id}/cancel` (`rate_alerts`, `V25`) — notification, jamais une
  transaction financière : aucun `Quote`/`Order`/`Payment`/`Settlement`/réservation
  `Wallet`/`Treasury`. Documentation ci-dessous.
- **Phase 7 — justificatif de transaction (PDF)** : `GET /api/v1/orders/{id}/receipt` —
  photographie documentaire des snapshots déjà figés (`Order`/`Payment`/`Settlement`/
  `Refund`/`Beneficiary`), aucun recalcul, aucune migration. Documentation ci-dessous.
- **Phase 8 — profil professionnel, historique enrichi, reporting** (`V26`) :
  `business_profiles` (`UNIQUE(user_id)`), module racine `business` (`profile` + `reporting`).
  `GET`/`PUT /api/v1/business-profile` (upsert idempotent), `GET /api/v1/orders/history`
  (historique filtrable/paginé, ouvert à **tout** utilisateur authentifié),
  `GET /api/v1/business/payments/summary` (agrégation SQL `GROUP BY`, réservée aux profils
  Business). La distinction PERSONAL/BUSINESS est portée **exclusivement** par l'existence
  d'une ligne `business_profiles` — **aucune colonne `accountType` sur `users`**. Aucun
  contact avec le pipeline financier : `business` ne dépend d'aucun de
  `Quote`/`Order`/`Payment`/`Settlement`/`Treasury`/`Wallet` en écriture. Documentation
  ci-dessous.

### `GET /api/v1/orders/{id}/tracking`

- **Authentification** : JWT requis (`Authorization: Bearer ...`), comme tout `/api/v1/**`.
  Sans jeton → `401 AUTHENTICATION_REQUIRED`.
- **Ownership** : strictement le propriétaire de l'ordre. Ordre d'un autre utilisateur ou
  inexistant → `404 ORDER_NOT_FOUND` dans les deux cas (jamais `403`, même convention que
  partout ailleurs — voir `OwnershipService`).
- **Réponse** (`OrderTrackingResponse`) :
  ```json
  {
    "orderId": "uuid",
    "currentStatus": "PROCESSING",
    "createdAt": "2026-09-03T10:00:00Z",
    "completedAt": null,
    "timeline": [
      { "code": "ORDER_CREATED", "status": "AWAITING_PAYMENT", "occurredAt": "...", "label": "Ordre cree" },
      { "code": "PAYMENT_SUBMITTED", "status": "PAYMENT_SUBMITTED", "occurredAt": "...", "label": "Paiement declare" }
    ]
  }
  ```
  `currentStatus`/`completedAt` sont lus directement depuis `Order`, jamais recalculés depuis
  la timeline. `TrackingEvent.status` est renseigné uniquement pour les événements qui
  correspondent réellement à une valeur de `OrderStatus` (dérivés d'`OrderStatusHistory`) ;
  `null` pour les événements d'enrichissement. `label` est une commodité d'affichage, jamais
  une source de vérité — le frontend doit tester `code`.
- **Codes d'événement** (`TrackingEventCode`, catalogue fermé) :
  - Dérivés d'`OrderStatusHistory` (source principale, jamais dupliquée) : `ORDER_CREATED`,
    `PAYMENT_SUBMITTED`, `PAYMENT_VERIFIED`, `PROCESSING`, `COMPLETED`, `CANCELLED`,
    `REJECTED`, `EXPIRED`.
  - Enrichissement, uniquement quand l'information n'existe dans **aucune** valeur
    d'`OrderStatus` : `PAYMENT_REJECTED` (`Payment.rejectedAt`, absent d'`OrderStatus`),
    `SETTLEMENT_EXECUTED` (`Settlement.executedAt`, aucun état intermédiaire entre
    `PROCESSING` et `COMPLETED`), `REFUND_PENDING`/`REFUND_PROCESSED` (`Refund`, jamais
    reflété sur `Order.status` — voir ci-dessous).
- **Cas terminaux** (`CANCELLED`/`REJECTED`/`EXPIRED`) : la timeline s'arrête proprement,
  aucun événement postérieur n'est jamais fabriqué (garanti par construction : ces
  enrichissements ne sont ajoutés que si l'entité correspondante existe réellement avec le
  bon statut, ce qui n'est structurellement pas possible après un état terminal).
- **Remboursement** : `Order.status` ne connaît **aucune** valeur `REFUNDED` (décision
  déjà actée, voir `RefundService`) — un remboursement reste une vérité portée entièrement
  par `Refund`, jamais copiée sur l'ordre. Le tracking expose au plus un événement
  `REFUND_PENDING` ou `REFUND_PROCESSED` (le remboursement actif du paiement, jamais un
  `REJECTED` historique), sans jamais modifier `currentStatus`.
- **Performance** : au plus 5 lectures ciblées par appel (ordre, historique, paiement,
  règlement, remboursement — chacun par clé, jamais de boucle) ; aucun `findAll()`.

### `POST /api/v1/suppliers/{supplierId}/pay-again`

**Principe** : une nouvelle entrée utilisateur dans le workflow financier existant, jamais un
raccourci. `Supplier` enregistré → **nouveau** `Quote` (pricing courant, jamais celui d'une
transaction passée) → **nouvel** `Order`. Aucun `Payment`/`Settlement` créé, aucune trésorerie
manipulée au-delà de la réservation déjà déclenchée par toute création d'ordre normale
(`TREASURY_RESERVE_ON_ORDER`).

- **Authentification** : JWT requis. Sans jeton → `401 AUTHENTICATION_REQUIRED`.
- **Ownership** : fournisseur d'un autre utilisateur ou inexistant → `404 SUPPLIER_NOT_FOUND`
  dans les deux cas (jamais `403`). Fournisseur désactivé → `409 SUPPLIER_INACTIVE`.
- **Requête** (`PayAgainRequest`) :
  ```json
  { "amountXof": 1000000, "purpose": "IMPORT_GOODS", "purposeDetails": "Paiement fournisseur electronique" }
  ```
  `amountXof` est **toujours explicitement fourni par le client** — jamais déduit du dernier
  paiement à ce fournisseur. `purpose` est optionnel : si omis, le motif par défaut du
  fournisseur (s'il en a un) est utilisé ; s'il est fourni, il a toujours priorité.
- **Réponse** : `OrderDetailResponse`, le même DTO que `POST /api/v1/orders` (`201 CREATED`) —
  aucune réponse parallèle. `supplierId` y référence le fournisseur utilisé, `quoteId` référence
  le **nouveau** devis créé pour cet appel (jamais un `quoteId` réutilisé).
- **Idempotence** : en-tête `Idempotency-Key` optionnel, même contrat que partout ailleurs
  (`user + endpoint + clé`, l'identifiant du fournisseur fait partie de l'identité de
  l'endpoint) — un rejeu avec la même clé et le même corps renvoie le même `Order`, jamais un
  second ; même clé et corps différent → `409 IDEMPOTENCY_KEY_REUSED`.
- **Snapshot** : le `Beneficiary` du nouvel `Order` est une copie figée des coordonnées du
  fournisseur **au moment de l'appel** — une modification ultérieure du fournisseur n'affecte
  jamais un ordre déjà créé (même invariant que la Phase 2, prouvé par test dédié).
- **Frontière transactionnelle** : `RepeatPaymentService.payAgain` est `@Transactional`
  (propagation REQUIRED, la valeur par défaut) — les appels internes à `QuoteService`/
  `OrderService` (chacun déjà `@Transactional` indépendamment, inchangé) rejoignent cette même
  transaction physique : si la création de l'ordre échoue, le nouveau devis est annulé avec le
  reste, jamais orphelin. Aucune frontière transactionnelle *existante* n'est modifiée — les
  endpoints normaux (créer un devis, l'accepter, créer un ordre séparément) restent chacun dans
  leur propre transaction HTTP, exactement comme avant.
- **DECISION REQUIRED** : `RateLimitFilter` ne couvre aujourd'hui que `/api/v1/quotes`,
  `/api/v1/orders` et `/api/v1/orders/*/payments` — `/api/v1/suppliers/*/pay-again` (qui peut
  pourtant créer un `Order`) n'est **pas** couvert par la limite de volume existante. Gap
  identifié, non corrigé unilatéralement dans cette mission (voir le rapport de clôture de
  Phase 4).

### `GET /api/v1/rates/history` (Phase 5)

**Frontière de confidentialité** — jamais négociable :

```
RAW / INTERNAL COST
        ↓
BREAK-EVEN                 (confidentiel, daily_cost_rate_configurations — jamais exposé)
        ↓
COMMERCIAL PRICING          (RateEngine.applyMargin — SEULE formule, jamais dupliquée)
        ↓
PUBLIC CUSTOMER RATE        (public_rate_snapshots — la seule chose que ce endpoint renvoie)
```

- **Table** `public_rate_snapshots` (`V24`) : `id, currency_pair, customer_rate, recorded_at`.
  Append-only (`@Immutable`) — pas d'UPDATE/DELETE métier, chaque publication de configuration de
  coût crée une nouvelle ligne, les précédentes ne sont **jamais** recalculées.
- **Intégration** : `CostRateAdminService.publish()` appelle additivement
  `PublicRateSnapshotService.record(breakEvenRate, pair, now)`, dans la **même transaction**
  (propagation REQUIRED) — atomique avec la persistance de `DailyCostRateConfiguration`, sans
  changer sa validation, son calcul de break-even, son audit ni sa frontière transactionnelle.
  `PublicRateSnapshotService` ne dépend **jamais** de `DailyCostRateConfiguration`/son
  repository — garantie structurelle qu'aucune donnée interne ne peut fuiter par ce chemin.
- **Calcul** : `RateEngine.applyMargin(breakEvenRate, marginPercentage)` — la marge
  effectivement active **au moment de la publication** (`SettingsService`), jamais recalculée
  après coup. Aucune formule de pricing dupliquée.
- **Convention de taux** : identique aux devis — `1 CNY = customerRate XOF`.
- **DTO public** (`PublicRateHistoryEntry`) : `currencyPair, customerRate, recordedAt`
  uniquement — jamais `breakEvenRate`, marge, frais, `costConfigurationId`, ni aucune donnée de
  `rate_sources`/`daily_cost_rate_configurations`.
- **Sécurité** : authentifié (tout utilisateur, pas admin-only) — `/api/v1/**` comme le reste
  des endpoints client ; "public" signifie ici *jamais réservé à l'administration*, pas
  *anonyme* (seul `/api/settings/public` l'est réellement dans ce backend). Anonyme → `401`.
- **Pagination/tri** : `?pair=XOF/CNY&from=...&to=...&page=&size=`. Tri **fixe**
  `recordedAt DESC, id DESC` (le plus récent en premier, tie-breaker déterministe) — non
  négociable par le client, tout paramètre de tri qu'il fournirait est ignoré. Pair non
  supportée → `400 VALIDATION_ERROR` (une seule paire supportée aujourd'hui, `XOF/CNY` — schéma
  et requêtes déjà prêts pour d'autres paires sans réécriture).
- **Compatibilité `Quote`** : la publication d'un nouveau taux ne modifie jamais le
  `customerRate` déjà figé d'un `Quote` existant — deux séries de données distinctes (série
  temporelle publique vs. snapshot par transaction), vérifié par test dédié.
- Les endpoints admin existants (`GET /api/admin/rates`, `GET /api/admin/cost-rates`)
  restent inchangés — ce endpoint est une projection client distincte, pas un remplacement.

### `/api/v1/rate-alerts` (Phase 6)

**Principe** : une intention utilisateur persistante ("préviens-moi quand le taux client public
XOF/CNY atteint mon objectif"), jamais une transaction financière.

```
USER TARGET → PUBLIC CUSTOMER RATE (public_rate_snapshots) → COMPARISON → TRIGGER → notification
```

**Distinct de `preferred_rate_requests`** (module déjà présent avant cette mission) : une
`PreferredRateRequest` immobilise un montant sur le `Wallet` dès sa création et déclenche un
**échange réel** (`Exchange`) quand la cible est atteinte. Une `RateAlert` ne porte **aucun
montant** et ne déclenche jamais rien de financier — uniquement une notification `IN_APP`. Les
deux coexistent délibérément, aucune des deux n'est une duplication de l'autre.

- **Table** `rate_alerts` (`V25`) : `id, user_id, currency_pair, direction, target_rate,
  comparison, status, created_at, expires_at, triggered_at, cancelled_at, expired_at, version`.
  Contrairement à `public_rate_snapshots` (append-only), cette table a un cycle de vie :
  `ACTIVE → TRIGGERED | CANCELLED | EXPIRED`, chaque transition finale, jamais l'inverse.
- **`direction`** réutilise `PreferredRateDirection` (`XOF_TO_CNY`, déjà existant), pas
  `QuoteDirection` : ce dernier décrit quel montant le client fournit à un devis (`SEND_XOF`/
  `RECEIVE_CNY`), non pertinent ici puisqu'une alerte ne porte aucun montant.
  `PreferredRateDirection` décrit déjà exactement le même concept — un sens de conversion,
  extensible plus tard, sans montant associé — sans dupliquer un troisième enum. Une seule
  valeur existe aujourd'hui, cohérente avec `currency_pair = 'XOF/CNY'` : les deux ne peuvent
  jamais se contredire.
- **`comparison`** (`RateComparison` : `LESS_THAN_OR_EQUAL`, `GREATER_THAN_OR_EQUAL`) encapsule
  la règle de déclenchement — `RateAlert.isSatisfiedBy(currentRate)` délègue à
  `comparison.isSatisfied(...)`, jamais un `if` dispersé dans le contrôleur ou le scheduler.
  Comparaison exclusivement via `BigDecimal.compareTo` (jamais `==`/`double`), cas d'égalité
  explicitement couvert (`83.50 <= 83.50 = true`). Seul `LESS_THAN_OR_EQUAL` est utilisé
  aujourd'hui (cas standard XOF/CNY, `1 CNY = X XOF`) ; `GREATER_THAN_OR_EQUAL` existe pour un
  sens futur sans changer le modèle.
- **Source du taux courant** : `PublicRateSnapshotService.latestCustomerRate(currencyPair)`
  (nouvelle méthode additive, Phase 6 — n'existait pas en Phase 5), qui délègue à
  `PublicRateSnapshotRepository.findFirstByCurrencyPairOrderByRecordedAtDescIdDesc` (même
  ordre déterministe que `search`, Phase 5). Ni `breakEvenRate`, ni marge interne, ni
  `daily_cost_rate_configurations`/`rate_sources` ne sont jamais consultés par `RateAlertService`
  — garantie structurelle, comme pour `PublicRateSnapshotService` lui-même. Absence de taux
  publié pour la paire → évaluation ignorée silencieusement (ni déclenchée, ni marquée en échec),
  réévaluée au passage suivant du scheduler.
- **Expiration** : `expires_at` optionnel (`null` = jamais). Si fournie à la création, doit être
  strictement future (`400 VALIDATION_ERROR` sinon). L'expiration l'emporte toujours sur
  l'évaluation du taux — `RateAlertService.processOne` teste `isPastDeadline` avant même de lire
  le dernier taux public : une alerte expirée n'est jamais déclenchée, même si sa cible serait
  par ailleurs satisfaite.
- **Scheduler** (`RateAlertScheduler`) : même patron que `PreferredRateScheduler`/
  `OrderExpirationScheduler`/`IdempotencyPendingCleanupScheduler` — `fixedDelay` (jamais
  `fixedRate`, aucun chevauchement intra-instance), une transaction par alerte
  (`RateAlertService#processOne`), `catch RuntimeException` par élément pour qu'un échec ponctuel
  n'affecte jamais les autres candidats. Fréquence par défaut 5 minutes
  (`rate-alert.scheduler.fixed-delay-ms`), configuration d'infrastructure (`application.yml`),
  jamais `SettingsService` — cohérent avec les trois autres schedulers du projet.
- **Concurrence/atomicité** : `RateAlertRepository.findByIdForUpdate` (verrou pessimiste
  `PESSIMISTIC_WRITE`, même patron que `PreferredRateRequestRepository`) chargé au tout début de
  `processOne`, avec un double-check du statut immédiatement après (`!= ACTIVE → no-op`). Deux
  passages concurrents (deux instances du scheduler, ou un scheduler et une annulation
  utilisateur simultanés) se sérialisent proprement sur la même ligne : un seul déclenchement,
  une seule notification — prouvé par test dédié (deux threads, `ExecutorService`, assertion sur
  le statut final et le nombre exact de notifications).
- **Notification** : `NotificationType.RATE_ALERT_TRIGGERED` (nouvelle valeur, catalogue
  `notifications.type` étendu en `V25` selon le même patron additif que `V16`/`ORDER_EXPIRED` —
  `DROP`/`ADD CONSTRAINT`, seule façon d'étendre un `CHECK` PostgreSQL). Réutilise
  `NotificationService.create(...)`, déjà `REQUIRES_NEW` et déjà tolérant à l'échec (absorbe
  toute exception, renvoie `null`) — aucune infrastructure de notification nouvelle.
- **Ordre transactionnel du déclenchement** : `alert.trigger(now)` (mutation en mémoire, flush à
  la fin de la transaction) précède l'audit (`AuditService.recordSystem`, `REQUIRES_NEW`) puis la
  notification (`NotificationService.create`, `REQUIRES_NEW`) — jamais l'inverse. Un échec de
  notification ne fait donc jamais revenir l'alerte à `ACTIVE` ; c'est l'ordre inverse
  (notifier puis marquer) qui exposerait à une double notification si une exception survenait
  entre les deux. Même principe que `PreferredRateService#trigger`. **Limite connue, documentée
  plutôt que corrigée par une nouvelle infrastructure (pas d'outbox/event bus introduit pour
  cette feature)** : `REQUIRES_NEW` peut valider l'audit/la notification avant que la transaction
  englobante (qui porte la transition `ACTIVE → TRIGGERED`) ne commit elle-même ; un crash exactement
  dans cette fenêtre laisserait une alerte encore `ACTIVE` en base après une notification déjà
  envoyée, exposée à une seconde notification au passage suivant. Risque déjà accepté par le
  code existant (`PreferredRateService` a la même fenêtre), non nouveau à cette phase.
- **Ownership** : `OwnershipService.assertOwnedBy`, convention `404` partout (jamais `403`) —
  alerte d'autrui ou inexistante → `404 RATE_ALERT_NOT_FOUND`, y compris pour l'annulation.
- **Doublons** : plusieurs alertes identiques (même utilisateur, même paire, même cible) sont
  autorisées — aucune contrainte `UNIQUE` arbitraire. Le scheduler garantit qu'une alerte
  individuelle n'est jamais déclenchée deux fois, indépendamment du nombre d'alertes similaires.
- **Rate limiting — DECISION REQUIRED** : `RateLimitFilter` ne couvre aujourd'hui que
  `/api/v1/quotes`, `/api/v1/orders`, `/api/v1/orders/*/payments` et (Phase 4)
  `/api/v1/suppliers/*/pay-again` — `/api/v1/rate-alerts` (`POST`) et
  `/api/v1/rate-alerts/*/cancel` (`POST`) n'y figurent pas. Gap identifié, non corrigé
  unilatéralement dans cette mission (zone sensible, `RateLimitFilter` non modifié) : ces
  endpoints ne créent ni ne déplacent aucune valeur financière (contrairement à `pay-again`), le
  risque d'abus est donc jugé plus faible ; à réévaluer si le volume d'utilisateurs augmente.
- **Audit** : `RATE_ALERT_CREATED`, `RATE_ALERT_CANCELLED` (acteur = l'utilisateur, `AuditService.record`),
  `RATE_ALERT_TRIGGERED` (`AuditService.recordSystem`, déclenché par le scheduler, sans acteur
  humain) — uniquement ces trois mutations significatives, jamais la lecture de la liste ni
  l'expiration (transition silencieuse, cohérente avec le fait qu'elle ne résulte d'aucune action).
- **DTO** (`RateAlertResponse`) : volontairement sans `currentRate`/`gap` (contrairement à
  `PreferredRateRequestResponse`) — évite de recalculer une cotation à chaque lecture, ce qui
  garde `list`/`get` strictement `readOnly = true`, sans le contournement de verrouillage que
  `PreferredRateService` doit faire pour la même raison.
- **Persistance des transitions** : `TRIGGERED`/`CANCELLED`/`EXPIRED` sont des états relus tels
  quels depuis PostgreSQL, jamais déduits d'un état en mémoire du scheduler — un redémarrage de
  l'application ne change rien à l'état d'une alerte déjà finalisée, et
  `RateAlertService#activeAlertIds` n'y ré-inclut jamais une alerte non `ACTIVE`.

### `GET /api/v1/orders/{id}/receipt` (Phase 7)

**Principe** : une photographie documentaire de la transaction historique, jamais une nouvelle
source de vérité.

```
Persisted financial data (Order/Payment/Settlement/Refund/Beneficiary)
        ↓
TransferReceiptModel
        ↓
ReceiptPdfGenerator (PDFBox)
```

et jamais `Order → recalcul du pricing courant → PDF`.

- **Package** `order.receipt` (`model`/`pdf`/`service`), sibling des autres sous-packages
  d'`order` — le justificatif est directement lié à l'`Order`, pas un module racine séparé.
  Endpoint ajouté additivement sur le `OrderController` existant, même patron que
  `/{id}/tracking` (Phase 3) : aucune mutation, aucun changement aux endpoints déjà en place.
- **Librairie PDF** : aucune dépendance PDF préexistante (audit `pom.xml`) — Apache PDFBox
  ajouté (licence Apache 2.0, projet Apache Software Foundation ; jamais iText 7/AGPL). Rendu
  texte simple (pas de HTML/CSS), suffisant pour un document à sections fixes.
- **Modèle** (`TransferReceiptModel`) : uniquement des champs réellement portés par les entités
  — `orderId`/`transactionReference` (`Order.reference`)/dates/statut/montants/`customerRate`
  viennent exclusivement des colonnes déjà figées d'`Order` (copie du `Quote` au moment de sa
  création). `paymentReference` = `Payment.transactionReference`, `settlementReference` =
  `Settlement.settlementReference` — jamais une référence documentaire inventée. `currencyPair`
  n'est pas une colonne d'`Order` : réutilise la constante déjà existante
  `RateProvider.DEFAULT_CURRENCY_PAIR` (même choix que les Phases 5/6), pas un nouveau champ.
- **`OrderReceiptService`** : aucune dépendance vers `RateEngine`/`SettingsService`/tout pricing
  courant — garantie structurelle identique à `PublicRateSnapshotService` (Phase 5) et
  `RateAlertService` (Phase 6). Chargement ciblé par identifiant exact (ordre, bénéficiaire,
  client, paiement, règlement, remboursement), jamais de `findAll` — même patron que
  `OrderTrackingService`.
- **Bénéficiaire** : exclusivement le snapshot `Beneficiary` de l'ordre, jamais le `Supplier`
  courant — une modification ultérieure du fournisseur (banque/compte) n'a donc structurellement
  aucun effet sur un justificatif déjà émis, prouvé par test dédié (banque A/compte 1111 à la
  création, fournisseur modifié en banque B/2222 ensuite, justificatif toujours banque A/1111
  masqué).
- **Minimisation des données sensibles** : l'identifiant du bénéficiaire (compte bancaire/Alipay/
  WeChat) est **masqué** (`******1234`, mêmes 4 derniers caractères que `SupplierService#mask`)
  — décision volontairement plus stricte que la vue détail d'un `Supplier` (qui renvoie le
  compte en clair à son propriétaire) : un PDF est un document exportable/imprimable/partageable,
  un risque de fuite plus élevé qu'une réponse JSON éphémère.
- **Disponibilité** : uniquement pour un `Order` `COMPLETED` — `409 INVALID_ORDER_STATE` sinon
  (code déjà existant, réutilisé, aucun nouveau code d'erreur). Aucun justificatif provisoire
  inventé pour un ordre encore en cours.
- **Remboursement (`Refund`)** : section optionnelle, affichée uniquement si un `Refund` existe
  pour le paiement, **quel que soit son statut** (y compris `REJECTED`, contrairement à la
  timeline de suivi de la Phase 3 qui masque un `REJECTED` historique) — le justificatif doit
  représenter fidèlement ce qui existe réellement. `Order.status` reste `COMPLETED` dans tous les
  cas : un remboursement n'annule jamais rétroactivement le transfert initial (même invariant que
  `Refund` lui-même).
- **Storage** : **option A retenue — génération à la demande, aucune persistance.** Le module
  `FileStorageService` existant ne permet pas de clé déterministe (`store(...)` génère toujours
  un chemin `{répertoire}/{année}/{mois}/{uuid}.{ext}`, jamais dérivé de l'appelant) ; répliquer
  ce pattern pour le justificatif aurait exigé une nouvelle colonne (clé de stockage) sur
  `Order`, donc une migration — explicitement déconseillée par la mission en l'absence de besoin
  réel. Le document est petit (quelques Ko de texte), reproductible à l'identique à chaque appel
  (section "déterminisme" ci-dessous) : régénérer à la demande est strictement équivalent à le
  relire, sans le coût d'une migration ni d'un fichier orphelin non suivi en base.
- **Déterminisme** : le même `Order` produit toujours le même contenu financier/bénéficiaire —
  aucun horodatage de génération (`generatedAt`) inséré dans le document, pour rester simple à
  tester et rigoureusement déterministe.
- **Sécurité / ownership** : authentifié (`401` sinon), `OwnershipService.assertOwnedBy` — ordre
  d'un autre utilisateur ou inexistant → `404 ORDER_NOT_FOUND` dans les deux cas, jamais `403`
  (aucune fuite d'existence). `Content-Type: application/pdf`,
  `Content-Disposition: attachment; filename="transaction-{reference}.pdf"` (nom dérivé de la
  référence générée côté serveur, jamais d'une entrée utilisateur), `X-Content-Type-Options:
  nosniff`. Aucune URL publique/pré-signée — le PDF transite uniquement en streaming contrôlé par
  le backend, jamais un lien permanent.
- **Lecture seule** : `@Transactional(readOnly = true)`, aucun verrou pessimiste (aucune mutation
  à protéger — contrairement au reste du pipeline financier). Aucune notification, aucun audit
  dédié (une simple consultation de document ne l'exige pas ; le projet n'a pas de politique
  d'audit de téléchargement à réutiliser).
- **Rate limiting** : `RateLimitFilter` ne limite aujourd'hui que des endpoints `POST` d'écriture
  — `GET /api/v1/orders/{id}/receipt` n'y est pas soumis, comme toute lecture du backend. Signalé
  sans être corrigé : la génération (rendu texte, quelques Ko) reste légère, mais deviendrait un
  point à surveiller si le document se complexifiait (mise en page riche, images) ou si le volume
  d'utilisateurs augmentait significativement.

### Phase 8 — profil professionnel, historique enrichi, reporting Business

**Principe** : « ONE financial core, MULTIPLE customer experiences ». Rien ici ne crée, ne
modifie ni ne déclenche une transaction — `business` est un module de **lecture et de profil**
posé à côté du pipeline, jamais dedans.

#### `business_profiles` (`V26`) et la distinction PERSONAL/BUSINESS

- **Table** : `id, user_id (UNIQUE), business_name, business_type, registration_number?,
  country, city?, address?, created_at, updated_at, version`. `business_type` ∈
  `IMPORTER | MERCHANT | SERVICES | OTHER` (`CHECK`, jamais un type ENUM PostgreSQL — même
  convention que tout le schéma). Aucun index ajouté : l'index `btree` créé par
  `UNIQUE(user_id)` couvre déjà la seule requête de lecture (`findByUserId`/`existsByUserId`).
- **Aucune colonne `accountType` sur `users`** (décision d'architecture) : un utilisateur est
  `BUSINESS` **si et seulement si** une ligne `business_profiles` existe pour lui, `PERSONAL`
  sinon. Cette règle est encapsulée dans `BusinessProfileService.isBusinessUser(userId)` —
  jamais dispersée en `if (profile != null)` dans un contrôleur.
- **Hors périmètre volontaire** : aucun statut KYC (`PENDING_KYC`/`VERIFIED`/`REJECTED`/
  `SUSPENDED`), aucune vérification RCCM/fiscale, aucun document légal. `registration_number`
  reste optionnel. Ce n'est pas un moteur de conformité entreprise.

#### `GET` / `PUT /api/v1/business-profile`

- Ressource **singleton par utilisateur**, jamais adressée par identifiant dans l'URL.
- `PUT` **idempotent** (`UpsertBusinessProfileRequest`) : crée si absent (`201`), met à jour
  sinon (`200`). Le propriétaire vient **exclusivement** de l'utilisateur authentifié —
  `userId` n'est jamais un champ du corps.
- `GET` sans profil → `404 BUSINESS_PROFILE_NOT_FOUND` (jamais `403`) : l'utilisateur est alors
  simplement `PERSONAL`.
- **Concurrence** : deux `PUT` concurrents pour le même utilisateur ne peuvent pas créer deux
  lignes — `uq_business_profiles_user` le garantit côté PostgreSQL ; la violation est convertie
  en `409 DUPLICATE_RESOURCE` par `GlobalExceptionHandler` (aucun code de conversion ajouté).
  Prouvé par test (`twoConcurrentCreations_forSameUser_resultInExactlyOneProfile`).
- **Audit** : `BUSINESS_PROFILE_CREATED` / `BUSINESS_PROFILE_UPDATED` (nouvelles valeurs
  `AuditAction`). Aucun audit sur les `GET` (profil, historique, résumé) — pas de politique
  d'audit de lecture dans le projet.

#### `GET /api/v1/orders/history` — historique enrichi

- **Ouvert à tout utilisateur authentifié** (Personal comme Business) : même pipeline financier
  pour les deux, l'historique n'est pas une fonction « premium ».
- `OrderHistoryService` : simple projection en lecture seule au-dessus d'`Order`, aucune
  mutation, aucune seconde source de vérité — même séparation que `OrderTrackingService`.
- **Filtres tous optionnels** : `status`, `purpose`, `supplierId`, `from`, `to`
  (`from <= createdAt < to`). Implémentés en JPQL avec le patron **paire
  `hasXxx` (boolean) / `xxx` (valeur)** — jamais `:x IS NULL` seul (voir la note sur le bug de
  type PostgreSQL plus bas). `userId` n'est **jamais** optionnel : un `supplierId` appartenant
  à un autre utilisateur ne renvoie jamais ses données, il ne produit simplement aucun
  résultat.
- **Tri fixe** `createdAt DESC, id DESC`, non négociable par l'appelant : seuls le numéro et la
  taille de page d'un `Pageable` fourni sont retenus (même convention que
  `PublicRateSnapshotService`/`RateAlertService`).
- **Vérité financière** : `amountXof`/`amountCny`/`feeXof`/`customerRate` proviennent
  exclusivement des colonnes déjà figées d'`Order` (copie du `Quote`) — jamais un recalcul avec
  le pricing courant.
- **Pagination PostgreSQL** : `Page<Order>` via `OrderRepository.searchHistory(...)`, jamais un
  `findAll()` global suivi d'un filtrage Java.

#### `GET /api/v1/business/payments/summary` — reporting consolidé

- **Réservé aux profils Business** : `isBusinessUser(userId) == false` → `404
  BUSINESS_PROFILE_NOT_FOUND` (jamais `403` — convention du backend).
- **Agrégation SQL** : `OrderRepository.aggregateByStatus(...)` (`SELECT new
  OrderStatusAggregate(o.status, COUNT(o), COALESCE(SUM(...)))  ... GROUP BY o.status`) —
  jamais un chargement des ordres suivi d'une somme Java. Une ligne par statut, consommée par
  `BusinessPaymentReportService`.
- **Convention de comptage** (la spec ne la fixait pas, documentée ici et dans le rapport de
  clôture) : `transferCount`/`completedCount`/`cancelledCount`/`rejectedCount` portent sur
  **tous les ordres** du périmètre ; `totalAmountXof`/`totalAmountCny`/`totalFeesXof` ne
  somment que les ordres `COMPLETED` — un ordre annulé/rejeté n'a jamais déplacé d'argent,
  l'inclure gonflerait un « volume traité » fictif.
- **Séparation `Refund` / `Order`** : un `Refund` n'est **jamais** un transfert. Un
  remboursement sur un ordre `COMPLETED` ne modifie ni `completedCount` ni aucun total (prouvé
  par test `summary_refundOnCompletedOrder_neverAltersTheTotals`). `Order.status` ne connaît
  aucune valeur `REFUNDED` — invariant déjà acté (voir Phase 3, `RefundService`).
- **Vérité financière** : mêmes colonnes figées d'`Order`, aucune dépendance vers
  `RateEngine`/`SettingsService`/tout pricing courant.

#### Note — `AuditLogRepository.search` et l'inférence de type PostgreSQL

Les vérifications d'audit de bout en bout de la Phase 8 ont révélé une **limitation
préexistante** (hors périmètre de correction de cette phase) : `AuditLogRepository.search`
utilise le patron `(:p IS NULL OR col = :p)` pour chaque filtre optionnel. Sous le protocole
étendu de PostgreSQL, un paramètre dont l'unique occurrence syntaxique exploitable est
`$n IS NULL` (ici la borne temporelle `from`) n'a **pas de type inférable au moment du
Parse** → `ERROR: could not determine data type of parameter $n`, y compris quand une valeur
concrète est passée (l'échec est au *parse*, avant le *bind*). Ce chemin n'avait jamais été
exercé par un test avant la Phase 8. **Traitement retenu** : un finder dérivé additif
`AuditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(...)` (aucun paramètre
optionnel, aucune modification de `search`), utilisé par les tests d'audit de la Phase 8.
`search` — et l'endpoint `GET /api/admin/audit-logs` qui s'appuie dessus — reste à corriger
(migration vers le patron `hasXxx`/`xxx` déjà employé par les Phases 5/8, ou `COALESCE`) :
**RISQUE documenté**, à traiter dans le « Final Backend Audit / Pre-production Hardening ».

---

# Partie I — Révision architecturale (Phase 2.5)

## A. Executive Summary

Ce qui change par rapport à l'architecture de la Phase 1, et pourquoi.

| # | Changement | Portée |
|---|---|---|
| 1 | **Séparation taux marché / taux client.** Le taux n'est plus une donnée unique administrée à la main et consommée telle quelle par l'ordre. On introduit une chaîne `RateProvider → RateEngine → Quote`, avec marge de change et frais de service explicitement distincts. | Conceptuel + modèle de données |
| 2 | **Introduction du `Quote`** comme objet métier de premier plan, snapshot immuable créé à la confirmation (pas à la simulation). L'ordre référence un devis, jamais un taux « courant ». | Nouvelle entité |
| 3 | **Introduction du `Settlement`**, distinct du `Payment`. `Payment` = le client a payé en FCFA. `Settlement` = l'entreprise a exécuté le décaissement en Chine. | Nouvelle entité |
| 4 | **Vocabulaire trésorerie clarifié** : `Available` / `Reserved` / `Consumed` / `Released`. Le schéma `treasury_accounts` / `treasury_transactions` de la Phase 1 reste structurellement valide — c'est la sémantique qui est précisée, pas la table. | Documentation, pas de migration |
| 5 | **Segmentation client** (`STUDENT`, `MERCHANT`, `IMPORTER`, `BUSINESS`) introduite comme attribut optionnel du compte, orthogonal au rôle d'accès (`RoleCode`). Aucune règle métier n'en dépend encore. | Extension future, non bloquante |
| 6 | **Capacité Risk/Compliance** introduite (`RiskFlag`, revue manuelle) sans construire un moteur AML. L'objectif est de ne pas rendre son ajout impossible plus tard, pas de le livrer maintenant. | Nouvelle capacité, portée volontairement réduite |
| 7 | **Drapeau `REGULATORY_VALIDATION_REQUIRED`** explicite : aucune source de liquidité P2P/crypto, aucun nouveau canal de paiement, ne sont présumés autorisés par défaut. | Gouvernance / documentation |
| 8 | **Aucune intégration Binance/OKX/Wave/virement bancaire** dans cette révision ni dans la Phase 3 qui suivra. Ce sont des sources et canaux **futurs**, déclarés dans l'abstraction mais non implémentés. | Portée explicitement exclue |
| 9 | **Le modèle de données prévu en Phase 1 pour `exchange_rates`/`orders` est ajusté**, pas jeté : voir le détail entité par entité en §K. | Modèle de données |

**Ce qui NE change PAS** : le choix monolithe modulaire Spring Boot/Angular/PostgreSQL, l'usage exclusif de `BigDecimal`, l'absence de décaissement automatisé, le stateless JWT, la sécurité (RBAC, 404 sur accès horizontal, anti brute-force), et tout le code déjà livré en Phase 2. Cette révision est un **raffinement du domaine métier**, pas une refonte technique.

---

## B. Vision produit révisée

> Construire l'infrastructure digitale de référence pour les paiements Burkina Faso ↔ Chine, permettant aux étudiants, commerçants et importateurs de connaître à l'avance leur taux, payer simplement en FCFA, suivre leur opération et bénéficier d'une exécution rapide et traçable de leur paiement en Chine.

Cette vision recadre le produit. Il ne s'agit pas d'un **calculateur de change + formulaire de commande**, mais d'un système complet :

```
QUOTATION
    ↓
ORDER
    ↓
PAYMENT
    ↓
VERIFICATION
    ↓
LIQUIDITY / SETTLEMENT
    ↓
CHINA PAYOUT
    ↓
TRACKING
```

Chaque étape est un concept métier à part entière, avec son propre état, sa propre traçabilité, et — pour `QUOTATION` et `LIQUIDITY/SETTLEMENT` — sa propre entité (`Quote`, `Settlement`), là où la Phase 1 les avait implicitement fusionnées dans `Order` et `TreasuryTransaction`.

---

## C. Positionnement

### C.1 Corridor et segments

```
BURKINA FASO
     ↓
    XOF
     ↓
    CHINE
     ↓
    RMB / CNY
```

Trois segments identifiés par l'étude de marché :

1. **Étudiants** burkinabè en Chine — besoin récurrent, montants modérés, sensibles au délai.
2. **Commerçants / importateurs** — besoin répété, montants moyens à élevés, sensibles au taux et à la fiabilité.
3. **PME / cargo / entreprises** — montants élevés, besoin de traçabilité documentaire (facture, preuve, référence).

### C.2 Douleurs adressées

- Taux de change élevés et peu transparents.
- Lenteur des transactions.
- Risque de fraude perçu.
- Manque de transparence sur le taux réellement appliqué.
- Lourdeur du parcours bancaire classique.

### C.3 Différenciateurs retenus pour la conception

- **Rapidité** perçue (verrouillage de taux, suivi en temps réel du statut).
- **Transparence** du taux (le client connaît son taux et ses frais **avant** de payer — d'où le `Quote`).
- **Compétitivité** du taux, rendue possible par une architecture qui sépare le coût réel (`marketRate`) de la marge appliquée, permettant de piloter cette marge finement au lieu de l'enfouir dans un taux unique.
- **Mobile Money** comme canal d'entrée principal (déjà le seul moyen de paiement actif en Phase 2/3, `PaymentMethod.MOBILE_MONEY`).

### C.4 Avertissement sur les données de marché

> **Aucun chiffre TAM/SAM/SOM de l'étude de marché n'est transformé en règle, contrainte ou constante technique.** Les volumes, montants moyens ou objectifs de croissance mentionnés dans l'étude sont des **hypothèses commerciales à valider par des données réelles**, une fois le produit en exploitation. Ils ne figurent dans aucun `system_settings`, aucune borne de code, aucun test. Les seules bornes techniques (`MIN_ORDER_AMOUNT_CFA`, `MAX_ORDER_AMOUNT_CFA`, etc.) restent des paramètres administrables sans lien avec ces hypothèses.

---

## D. Segmentation client

### D.1 Principe

Le domaine utilisateur ne doit plus être pensé comme un unique type `USER` indifférencié. On introduit un attribut **`CustomerSegment`**, orthogonal au contrôle d'accès :

```
RoleCode        → QUI a le droit de faire quoi dans le système (USER, ADMIN)
CustomerSegment → QUEL type de client est-ce, pour adapter l'offre (STUDENT, MERCHANT, IMPORTER, BUSINESS)
```

Ces deux axes ne se substituent jamais l'un à l'autre : un `ADMIN` n'a pas de segment (`NULL`) ; un `USER` a un segment optionnel, potentiellement `UNSPECIFIED` tant qu'il n'a pas été qualifié.

| Valeur | Usage typique visé (non implémenté) |
|---|---|
| `STUDENT` | Montants modérés et récurrents, tarification potentiellement dédiée, pièce justificative différente (carte étudiante) |
| `MERCHANT` | Volume répété, plafonds plus élevés à terme |
| `IMPORTER` | Montants élevés ponctuels, besoin de traçabilité documentaire renforcée |
| `BUSINESS` | Entreprise/cargo, KYC renforcé à terme, potentiellement facturation |
| `UNSPECIFIED` | Valeur par défaut, avant qualification |

### D.2 Ce que cette segmentation permettra plus tard (hors périmètre immédiat)

- **Limites** : un `MAX_ORDER_AMOUNT_CFA` par segment plutôt qu'un plafond global unique (via une future table `segment_settings` ou `fee_schedules` clé par segment, superposée aux `system_settings` globaux qui restent la valeur par défaut).
- **KYC** : profondeur de vérification différente selon le segment (un étudiant vs une entreprise).
- **Tarification** : barème de frais/marge différent par segment.
- **Risque** : profil de risque et seuils de `RiskFlag` différents par segment.
- **Fonctionnalités** : accès à des fonctionnalités spécifiques (ex. facturation pour `BUSINESS`).

### D.3 Ce qui est fait maintenant

Rien n'est codé. Cette section documente uniquement la **place réservée** dans le modèle (`users.customer_segment`, colonne nullable, ajoutée par une migration future de la Phase 3/4 — voir §K.13). Aucune règle métier actuelle (bornes, frais, risque) ne dépend du segment ; toutes restent globales via `system_settings`, exactement comme en Phase 2.

---

## E. Architecture fonctionnelle

```
┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────────┐   ┌───────────────┐   ┌─────────────┐   ┌──────────┐
│QUOTATION │──►│  ORDER   │──►│ PAYMENT  │──►│ VERIFICATION │──►│ LIQUIDITY /   │──►│ CHINA PAYOUT │──►│ TRACKING │
│          │   │          │   │          │   │              │   │ SETTLEMENT    │   │              │   │          │
└──────────┘   └──────────┘   └──────────┘   └──────────────┘   └───────────────┘   └──────────────┘   └──────────┘
     │              │              │                │                   │                   │                │
  module         module         module           module              module              module           module
  rate/quote      order         payment          payment            treasury +          settlement        audit +
                                                 (verify)            settlement                            dashboard
```

| Étape | Module(s) porteur(s) | Entité(s) |
|---|---|---|
| `QUOTATION` | `rate` (providers, engine), `quote` | `RateSource`, `Quote` |
| `ORDER` | `order` | `Order`, `Beneficiary` |
| `PAYMENT` | `payment` | `Payment`, `PaymentProof` |
| `VERIFICATION` | `payment` (revue admin) | `Payment.status` |
| `LIQUIDITY / SETTLEMENT` | `treasury`, `settlement` | `TreasuryAccount`, `TreasuryTransaction`, `Settlement` |
| `CHINA PAYOUT` | `settlement` | `Settlement` (exécution manuelle) |
| `TRACKING` | `order`, `audit`, `dashboard` | `OrderStatusHistory`, `AuditLog` |

---

## F. Architecture technique

```
                        ┌─────────────────────┐
                        │      Angular         │
                        │ Customer / Admin      │
                        └──────────┬───────────┘
                                   │
                              REST API
                                   │
                  ┌────────────────▼────────────────┐
                  │          Spring Boot             │
                  │                                  │
                  │ Auth / User                      │
                  │ Quote / Rate Engine               │
                  │ Order                              │
                  │ Payment                             │
                  │ Settlement                           │
                  │ Treasury                              │
                  │ Risk / Compliance                      │
                  │ Audit                                   │
                  └───────────────┬──────────────────────┘
                                  │
                       ┌──────────▼──────────┐
                       │     PostgreSQL       │
                       └──────────────────────┘

  En dehors du cœur métier, derrière des interfaces (ports) :

  ┌────────────────┐  ┌────────────────┐  ┌───────────────────────┐  ┌────────────────────────┐
  │ Rate Providers  │  │ File Storage   │  │ Future Payment         │  │ Future Liquidity        │
  │ (Manual / futur │  │ (Local / futur │  │ Providers               │  │ Providers                │
  │  Market / P2P)  │  │  S3)           │  │ (Wave, Bank — futur)    │  │ (Market API, P2P — futur)│
  └────────────────┘  └────────────────┘  └───────────────────────┘  └────────────────────────┘
```

**Confirmations explicites** (aucune de ces décisions de la Phase 1 n'est remise en cause) :

- Toujours **un seul backend monolithique modulaire** — pas de microservices. La séparation `rate`/`quote`/`order`/`payment`/`settlement`/`treasury` est une séparation de **modules**, pas de **services déployés** : ils partagent la même base PostgreSQL et les mêmes transactions ACID.
- Toujours **Spring Boot 3.5 / Java 21** côté backend, **Angular** côté frontend. Aucun remplacement de stack.
- Toujours **PostgreSQL comme source de vérité unique**, y compris pour la trésorerie.
- Les « Rate Providers », « Future Payment Providers » et « Future Liquidity Providers » sont des **adaptateurs derrière une interface**, exactement comme `FileStorageService` en Phase 2 (`LocalFileStorageService` aujourd'hui, `S3FileStorageService` demain, sans toucher au domaine). Le domaine ne dépend jamais d'un fournisseur concret.

---

## G. Nouveau modèle Rate / Quote

### G.1 Pourquoi séparer taux marché et taux client

Le modèle de la Phase 1 (`ExchangeRate → Order`) traitait le taux comme une **donnée unique** : un administrateur saisissait « le » taux, frais compris, et l'ordre le consommait tel quel. Cela ne permet pas de répondre séparément à :

- Quel était le taux de marché à cet instant ?
- Quelle marge avons-nous appliquée ?
- Quel taux avons-nous réellement proposé au client ?

Or ce sont exactement les deux sources de revenu identifiées par l'étude de marché (frais de service **et** marge sur le taux de change), qui doivent rester distinctes pour être pilotées, reportées et auditées séparément.

### G.2 Chaîne conceptuelle

```
Source A              84.80 XOF/CNY
Source B              84.95 XOF/CNY
Source C              85.00 XOF/CNY
                       ↓
                Market Rate
                       ↓
                Business Margin
                       ↓
              Customer Rate
                       ↓
                 Customer Quote
```

```
Market Rate
     ↓
Rate Sources
     ↓
Rate Engine
     ↓
Customer Rate
     ↓
Quote
     ↓
Order
```

Le client ne dépend **jamais directement** d'un fournisseur de liquidité : il ne voit et ne consomme que le `customerRate` d'un `Quote`.

### G.3 Abstraction `RateProvider`

```
RateProvider                    (interface — port)
    ├── ManualRateProvider       ← MVP, source opérationnelle principale
    ├── MarketRateProvider       ← futur (agrégation d'API de marché)
    └── P2PRateProvider          ← futur (Binance/OKX ou équivalent)
```

Contrat conceptuel (pas de code à ce stade) :

- Un `RateProvider` sait produire une cotation brute pour une paire de devises : valeur, instant de capture, référence de source.
- Il expose son type (`MANUAL`, `MARKET`, `P2P`) et sa disponibilité.
- Le `RateEngine` interroge un ou plusieurs `RateProvider` actifs et produit le **`marketRate`** — pour le MVP, avec un seul `ManualRateProvider` actif, le `marketRate` est simplement la dernière cotation manuelle publiée ; la logique d'agrégation multi-sources (médiane, moyenne pondérée, etc.) n'a de sens que lorsqu'une deuxième source existera, et sera ajoutée **sans modifier le domaine `Order`**, précisément parce que `RateProvider` est déjà une abstraction.

**Binance/OKX/API P2P** : cités par l'étude de marché comme avantage potentiel. Traités ici comme une **source de liquidité/données future**, jamais comme une dépendance du MVP. `P2PRateProvider` est un nom réservé dans l'abstraction, **aucune intégration n'est développée** dans cette phase ni dans la Phase 3 qui suit. Voir §J.3 pour la réserve réglementaire explicite qui s'applique à toute activation future de ce mécanisme.

### G.4 `RateEngine` — du taux marché au taux client

```
customerRate = marketRate × (1 + marginPercentage / 100)
```

`marginPercentage` (et, si besoin plus tard, une marge fixe) est un paramètre métier administrable, **distinct** de `feePercentage`/`fixedFeeCfa` (les frais de service). Les deux sont capturés séparément dans le `Quote`.

### G.5 Entité `Quote`

Une **simulation** reste stateless (comme en Phase 1) : elle interroge le `RateEngine`, calcule un aperçu, ne persiste rien.

Lorsque le client **confirme**, le système crée un `Quote` — snapshot immuable :

> `marketRate`/`rateSourceId` ci-dessous `[SUPERSEDED → voir §G.7]` : depuis la Phase 3.1, ces colonnes s'appellent `breakEvenRate`/`costConfigurationId` et proviennent de `DailyCostRateConfiguration`, plus de `RateSource`. Le reste de ce tableau (marge, frais de service, montants) reste exact.

| Champ | Rôle |
|---|---|
| `quoteId` | Identifiant public |
| `userId` | Client propriétaire |
| `amountCfa` | Montant brut saisi par le client |
| `marketRate` | Taux de marché au moment du devis (traçabilité) |
| `marginPercentage` | Marge appliquée (traçabilité) |
| `customerRate` | Taux réellement proposé au client — `marketRate × (1 + marginPercentage/100)` |
| `feePercentage`, `fixedFeeCfa` | Frais de service, distincts de la marge |
| `feeCfa` | Frais calculés |
| `netAmountCfa` | `amountCfa − feeCfa` |
| `amountCny` | `netAmountCfa / customerRate` |
| `rateSourceId` | Renvoi vers la cotation source (`rate_sources`) ayant produit `marketRate` |
| `rateVersion` / `rateSnapshot` | Empreinte de la cotation utilisée (permet de rejouer un litige) |
| `status` | `ACTIVE` (devis en attente de confirmation) → `CONFIRMED` (consommé par un `Order`) ou `EXPIRED` |
| `createdAt` | Horodatage de création |
| `expiresAt` | `createdAt + RATE_LOCK_DURATION_MINUTES` (30 min) |

Un `Order` **référence** un `Quote` (`order.quote_id`) au lieu de référencer un taux brut. Principe non négociable, repris du cahier des charges : **un ordre historique ne dépend jamais du taux actuel.**

### G.6 Verrouillage de 30 minutes — précision du déclencheur

```
Quote créé (confirmé)
      ↓
30 minutes
      ↓
Quote expire
```

Point important, différent d'une lecture naïve de la Phase 1 : le verrou démarre **à la création du devis confirmé**, pas à la simple simulation. Une simulation peut être répétée indéfiniment sans jamais démarrer de compte à rebours.

Si le taux de marché change **entre la simulation et la confirmation** (le client a mis du temps à valider), le système ne fige pas silencieusement un taux obsolète : la confirmation renvoie `409 RATE_CHANGED` avec les nouvelles conditions, et le client doit obtenir un nouveau devis avant de continuer. Une fois le `Quote` créé et confirmé, en revanche, il est **immuable** jusqu'à son expiration ou sa consommation par un `Order` — exactement le comportement de verrouillage déjà spécifié en Phase 1, seul le point de départ change.

### G.7 Phase 3.1 — le `Quote` se price depuis le coût de revient (`breakEvenRate`), plus depuis `RateSource`

**Ce qui a changé** : G.4/G.5 ci-dessus décrivaient un `marketRate` saisi manuellement (`RateSource` → `ManualRateProvider`) comme base du `customerRate`. Depuis la Phase 3.1, cette base est le **coût de revient réel** de la chaîne d'approvisionnement XOF → USD → CNY, calculé par `CostRateCalculator` à partir de la dernière `DailyCostRateConfiguration` publiée par un administrateur. `market_rate`/`rate_source_id` sur `quotes` sont renommés `break_even_rate`/`cost_configuration_id` (migration `V18`).

```
DailyCostRateConfiguration (rateXofUsd, rateUsdCny, feeXofUsdPercent, feeUsdCnyFixedUsd, referenceAmountXof)
        │
        ▼
CostRateCalculator.calculateBreakEven(...)          ← XOF -> USD -> CNY, AUCUNE marge ici
        │
        ▼
breakEvenRate   (coût interne, XOF par CNY — JAMAIS exposé au client)
        │
        ▼
RateEngine.price(basis, amount, breakEvenRate, marginPercentage, feePercentage, fixedFeeXof)
        │                              │                    │
        │                       + marge commerciale   + frais de service (axe séparé,
        │                         (décision business)   inchangé depuis la Phase 3 :
        ▼                                                SettingsService)
customerRate = breakEvenRate × (1 + marginPercentage/100)   ← seule valeur exposée au client
        │
        ▼
Quote (snapshot immuable : breakEvenRate, marginPercentage, customerRate, feeXof, netAmountXof, amountCny)
```

**Exemple numérique** (paramètres du jour, montant de référence 1 000 000 XOF) :

| Entrée | Valeur |
|---|---|
| `rateXofUsd` | 583 |
| `rateUsdCny` | 6.70 |
| `feeXofUsdPercent` | 0.01 (fraction, = 1 %) |
| `feeUsdCnyFixedUsd` | 1.50 USD |

`netXof = 990 000` → `usdReceived ≈ 1698.1132` → `usdNet ≈ 1696.6132` → `cnyReceived ≈ 11 367.3085` → **`breakEvenRate ≈ 87.971572` XOF/CNY**. Avec une marge commerciale de 2 % : `customerRate = 87.971572 × 1.02 ≈ 89.730 XOF/CNY` — c'est cette seule valeur, plus les frais de service (`feePercentage`/`fixedFeeXof`, inchangés), que voit le client dans son `Quote`.

**Ce qui n'a pas changé** :
- `RateEngine` reste inchangé dans sa logique (arrondis, frais de service, sens SEND_XOF/RECEIVE_CNY) — seul son paramètre d'entrée passe de `MarketRate` (type spécifique à `rate.domain`) à un simple `BigDecimal baseRate`, qu'il ne cherche jamais à interpréter. Le moteur n'a donc jamais eu besoin de savoir d'où vient ce taux.
- `RateSource`/`RateProvider`/`ManualRateProvider`/`AdminRateController` (`/api/admin/rates`) restent pleinement fonctionnels et **ne sont pas dépréciés** : `preferredrate` (`PreferredRateService`/`PreferredRateScheduler`) continue de s'appuyer dessus pour évaluer si le taux cible d'un client est atteint. Seule la création de `Quote` change de source.
- `DailyCostRateConfiguration` n'a pas de notion de ligne « courante » à clôturer (contrairement à `rate_sources`) : chaque publication insère simplement une nouvelle ligne complète et immuable. La lecture de la configuration la plus récente (`ORDER BY created_at DESC LIMIT 1`) n'a donc besoin d'aucun verrou pessimiste — la garantie MVCC de PostgreSQL suffit à exclure qu'une transaction concurrente lise un mélange de deux publications.
- Un `Quote` déjà créé reste un snapshot immuable : republier une configuration de coût ou changer la marge le lendemain ne le modifie jamais (mêmes garanties qu'en Phase 3).
- `breakEvenRate` et `marginPercentage` ne sont, comme `marketRate` avant eux, **jamais exposés** dans `QuoteResponse` — seuls `customerRate`, les montants et les frais de service le sont.

**Trois notions à ne jamais fusionner**, chacune indépendamment auditable :
1. **Coût** (`feeXofUsdPercent`/`feeUsdCnyFixedUsd`) — le prix payé dans la chaîne d'approvisionnement, entre dans `breakEvenRate`.
2. **Marge** (`marginPercentage`) — la décision commerciale appliquée par-dessus `breakEvenRate` par `RateEngine.applyMargin`.
3. **Frais de service** (`feePercentage`/`fixedFeeXof`) — facturés explicitement au client, calculés par `RateEngine` sur le montant XOF, totalement indépendants de 1 et 2.

---

## H. Nouveau modèle Order / Payment / Settlement

### H.1 Order référence un Quote, pas un taux

```
Order
  ├── quote_id  (FK, immuable après création)
  ├── Payment   (0..n — le client a payé)
  └── (via Settlement, hors Order) 
```

`Order` conserve, dénormalisés depuis le `Quote` au moment de la création, les champs nécessaires à un affichage/filtrage rapide sans jointure systématique (`amountCfa`, `amountCny`, `customerRate`, `feeCfa`, etc.) — exactement comme en Phase 1, sauf que la source de vérité de ces valeurs est désormais le `Quote`, pas un `ExchangeRate` brut.

### H.2 Payment — inchangé conceptuellement

Un `Payment` continue de signifier : *le client a payé en FCFA*. Le cycle `SUBMITTED → VERIFIED | REJECTED` de la Phase 1 reste valide tel quel.

### H.3 Settlement — nouvelle entité

Un `Settlement` signifie : *l'entreprise a exécuté/organisé le décaissement en Chine*. C'était auparavant fondu dans `Order` (`payout_reference`, `admin_note`, `completed_at`). On l'extrait en entité propre :

```
Order
  ├── Payment    (le client a payé)
  └── Settlement (l'entreprise a réglé en Chine)
```

| Champ | Rôle |
|---|---|
| `settlementId` | Identifiant public |
| `orderId` | Ordre réglé (1–1 pour le MVP) |
| `settlementStatus` | `PENDING → EXECUTED` (voir §N.3) |
| `settlementReference` | Référence du décaissement (remplace `orders.payout_reference`) |
| `settledAt` | Horodatage d'exécution |
| `executedBy` | Administrateur ayant exécuté/déclaré le règlement |
| `notes` | Remplace `orders.admin_note` pour la partie règlement |
| `amountCny` | Montant effectivement décaissé (traçabilité, permet un écart avec `order.amountCny` si jamais constaté) |

Le décaissement Yuan **reste manuel** dans le MVP, sans changement : `Settlement` structure le *suivi* de cette opération manuelle, il ne l'automatise pas.

### H.4 Pourquoi extraire Settlement plutôt que garder les champs sur Order

- Un `Order` qui porte à la fois les champs de paiement client et les champs de règlement fournisseur mélange deux responsabilités et deux acteurs (client / trésorerie interne).
- Séparer permet de répondre à la question d'audit « qui a exécuté le règlement, quand, avec quelle référence » sans avoir à distinguer ce champ des autres colonnes d'`Order`.
- Cela ouvre la voie, plus tard, à un règlement en plusieurs étapes ou par lot (plusieurs ordres réglés par un seul virement groupé) sans redesign — hors périmètre MVP, mais non bloqué.

---

## I. Treasury revisité

### I.1 Vocabulaire aligné sur le modèle demandé

```
CNY Available
      ↓
Order created
      ↓
CNY Reserved
      ↓
Settlement
      ↓
CNY Consumed
```

```
CNY Reserved
      ↓
Order expired/cancelled
      ↓
CNY Released
```

### I.2 Ce vocabulaire correspond déjà au schéma de la Phase 1 — pas de refonte

| Terme demandé | Représentation existante (`treasury_accounts` / `treasury_transactions`) |
|---|---|
| `Available` | `balance − reserved_balance` (déjà documenté ainsi en Phase 1, §C.8) |
| `Reserved` | `treasury_accounts.reserved_balance`, mouvement `RESERVATION` |
| `Released` | Mouvement `RELEASE` (annulation, expiration, rejet) |
| `Consumed` | Mouvement `WITHDRAWAL` **lié à un `order_id`** (décaissement effectif après `Settlement`) |

Le seul ajustement est **sémantique**, pas structurel : `WITHDRAWAL` recouvrait déjà « décaissement CNY exécuté », mais sans distinguer explicitement un décaissement lié à un ordre (= *Consumed*, la réservation devient réelle) d'un mouvement de trésorerie manuel sans ordre (alimentation/retrait administratif, déjà couvert par `DEPOSIT`/`ADJUSTMENT`). Cette distinction se lit déjà dans le schéma existant via la présence ou l'absence de `treasury_transactions.order_id` — elle est désormais **documentée explicitement** plutôt qu'implicite.

**Décision** : la table `treasury_accounts`/`treasury_transactions` telle que migrée en Phase 2 (`V1`, `V2`) **n'est pas modifiée**. Voir §K.10–K.11.

### I.3 Garanties de concurrence — inchangées

Les réservations restent transactionnelles, protégées par verrou pessimiste sur `treasury_accounts` et par la contrainte `CHECK (reserved_balance <= balance)`, plus l'index unique partiel `uq_treasury_tx_order_type` empêchant une double réservation/libération/consommation pour un même ordre. Rien de tout cela ne change (Phase 1, §J.1).

---

## J. Risk / Compliance

### J.1 Portée volontairement réduite

L'objectif de cette phase n'est **pas** de construire un moteur AML/KYC complet. C'est de ne pas rendre son ajout **impossible** plus tard. Deux capacités identifiables sont introduites conceptuellement : `risk/` et `compliance/`.

### J.2 `RiskFlag`

Un signalement simple, non bloquant par défaut, posé sur une entité (ordre, paiement, utilisateur) :

| Type de drapeau | Déclencheur envisagé (futur, pas codé) |
|---|---|
| `LARGE_AMOUNT` | Montant au-delà d'un seuil administrable |
| `SUSPICIOUS_ACTIVITY` | Motif libre, posé manuellement par un administrateur |
| `REPEATED_REJECTED_PAYMENT` | Plusieurs paiements rejetés consécutifs pour un même client |
| `VELOCITY_LIMIT` | Trop d'ordres sur une fenêtre de temps courte |
| `MANUAL_REVIEW` | Revue manuelle demandée explicitement |

Cycle de vie envisagé : `OPEN → REVIEWED → (CONFIRMED | DISMISSED)`. Un `RiskFlag` **n'empêche rien automatiquement** dans le MVP : il qualifie une entité pour la file de revue admin. Le blocage effectif reste, comme aujourd'hui, une décision explicite d'un administrateur (`UserService.block`, rejet de paiement) — le `RiskFlag` l'éclaire, ne la remplace pas.

### J.3 Cadre réglementaire — `REGULATORY_VALIDATION_REQUIRED`

L'étude de marché signale un enjeu réglementaire important autour de l'activité de change. Cette architecture **ne présume pas** que la combinaison

```
API crypto (P2P)
+
Mobile Money
+
change XOF/CNY
```

est automatiquement autorisée. C'est une hypothèse à valider, pas un fait acquis.

**Décision de conception** : tout mécanisme de sourcing ou de règlement dont la légalité n'est pas établie (notamment `P2PRateProvider`, tout futur canal de paiement au-delà de `MOBILE_MONEY`) est conçu comme **désactivé par défaut** et gouverné par un paramètre explicite portant la mention `REGULATORY_VALIDATION_REQUIRED` dans sa description (`system_settings`, à l'image de `ENABLED_PAYMENT_METHODS` déjà existant, qui liste aujourd'hui uniquement `MOBILE_MONEY`). Activer une nouvelle source ou un nouveau canal doit être un acte administratif conscient, jamais un comportement par défaut du code.

Cette règle s'applique en particulier à `P2PRateProvider` et à tout mécanisme de règlement basé sur des actifs numériques : **leur activation opérationnelle devra être validée au regard du cadre légal et réglementaire applicable avant toute mise en production**, indépendamment de leur faisabilité technique.

### J.4 Traçabilité — voir §Q

Les capacités `customer identity`, `beneficiary`, `transaction amount`, `payment proof`, `transaction reference`, `order history`, `audit trail` sont **déjà couvertes** par le modèle de données de la Phase 1 (`users`, `beneficiaries`, `orders`, `payment_proofs`, `payments`, `audit_logs`). `risk_flags` est la seule pièce manquante ; elle est décrite en §K.12.

---

## K. Modèle de données révisé

Revue entité par entité, au format demandé : **EXISTANT / PROBLÈME / PROPOSITION / JUSTIFICATION / IMPACT**. Aucune migration n'est exécutée dans cette phase — l'impact décrit ce qui sera fait en Phase 3 et suivantes.

### K.1 `users`

- **EXISTANT** : table complète (Phase 2, `V1`), avec `status` (`ACTIVE`/`BLOCKED`) et rôles via `user_roles`. Aucune notion de segment client.
- **PROBLÈME** : impossible de différencier étudiant / commerçant / importateur / entreprise pour une future tarification ou des limites dédiées.
- **PROPOSITION** : ajouter une colonne nullable `customer_segment VARCHAR(20)` (`STUDENT`, `MERCHANT`, `IMPORTER`, `BUSINESS`, `UNSPECIFIED` par défaut), sans contrainte `NOT NULL` pour ne pas casser les comptes déjà créés en Phase 2.
- **JUSTIFICATION** : additive, non destructrice ; aucune règle métier actuelle n'en dépend, donc aucun risque de régression.
- **IMPACT** : migration additive en Phase 3/4 (`ALTER TABLE users ADD COLUMN ...`), aucune donnée existante affectée, aucun code Phase 2 à modifier (`User`, `UserService`, `AuthService` continuent de fonctionner sans connaître ce champ tant qu'il n'est pas exploité).

### K.2 `roles` / `user_roles`

- **EXISTANT** : `USER`/`ADMIN`, référentiel fermé.
- **PROBLÈME** : aucun — ce n'est pas le même axe que la segmentation (voir §D.1).
- **PROPOSITION** : inchangé.
- **JUSTIFICATION** : le contrôle d'accès (qui a le droit de faire quoi) n'a pas à évoluer parce que le profil commercial du client évolue.
- **IMPACT** : aucun.

### K.3 `exchange_rates` → remplacée conceptuellement par `rate_sources`

- **EXISTANT** : table unique mêlant taux ET frais de service (`cfa_per_cny`, `fee_percentage`, `fixed_fee_cfa`), avec un seul taux « courant » à la fois (`effective_to IS NULL`).
- **PROBLÈME** : ne distingue pas taux de marché et taux client ; ne permet pas de savoir, après coup, quelle marge a été appliquée séparément des frais ; ne modélise pas la possibilité de plusieurs sources.
- **PROPOSITION** : repenser comme `rate_sources` — chaque ligne est une **cotation brute** d'un `RateProvider` (`provider_type` : `MANUAL`/`MARKET`/`P2P`, `cfa_per_cny`, `captured_at`, `created_by`, `note`). Les frais de service migrent vers `system_settings` (ou une table `fee_schedules` dédiée si une segmentation tarifaire est requise plus tard) ; la marge (`marginPercentage`) devient un paramètre du `RateEngine`, historisé **dans chaque `Quote`** plutôt que sur la source elle-même.
- **JUSTIFICATION** : sépare la donnée d'entrée (cotation brute, ce que dit le marché) de la décision commerciale (marge, frais), qui peut changer sans changer la cotation, et inversement.
- **IMPACT** : cette table est déjà migrée (`V1`) mais **aucun code applicatif n'en dépend** (le module `exchange` n'a jamais été construit en Phase 2). Le coût de la révision est donc nul aujourd'hui : une nouvelle migration (`V7+`) en Phase 3 remplacera son usage, sans avoir à défaire du code existant.

### K.4 `quotes` — nouvelle table

- **EXISTANT** : n'existe pas. La Phase 1 avait explicitement écarté une table `quotes` persistée (décision V2 de la Phase 1, « stateless suffit ») au profit d'un `Order` directement lié au taux courant.
- **PROBLÈME** : sans devis persisté, impossible de répondre après coup à « quel taux de marché, quelle marge, quels frais avons-nous montrés au client avant qu'il ne confirme ? » — seul le résultat final (dans `Order`) était conservé, pas la décomposition.
- **PROPOSITION** : table `quotes` avec les champs détaillés en §G.5 (`amountCfa`, `marketRate`, `marginPercentage`, `customerRate`, `feePercentage`, `fixedFeeCfa`, `feeCfa`, `netAmountCfa`, `amountCny`, `rateSourceId`, `rateVersion`/`rateSnapshot`, `status`, `createdAt`, `expiresAt`).
- **JUSTIFICATION** : c'est le changement demandé le plus structurant de cette révision — un `Order` ne doit plus jamais dépendre du taux actuel, et la seule façon de garantir cela avec une décomposition marge/frais est de figer cette décomposition à un instant donné, dans son propre snapshot.
- **IMPACT** : nouvelle table (Phase 3), nouveau module `quote`. `orders.exchange_rate_id` est remplacé par `orders.quote_id`. Aucun impact sur le code Phase 2 existant.

### K.5 `orders`

- **EXISTANT** : référence directement `exchange_rate_id` et duplique taux/frais en colonnes propres (`exchange_rate`, `fee_percentage`, `fixed_fee_cfa`, `rate_locked_at`, `rate_expires_at`). Porte aussi `payout_reference`, `admin_note`, `completed_at` (règlement).
- **PROBLÈME** : (a) référence un taux plutôt qu'un devis, incompatible avec le nouveau modèle §G ; (b) mélange les responsabilités « ordre » et « règlement fournisseur ».
- **PROPOSITION** : remplacer `exchange_rate_id` par `quote_id` (FK unique, un devis n'est consommé que par un seul ordre) ; conserver les colonnes dénormalisées `amount_cfa`/`amount_cny`/`customer_rate`/`fee_cfa` pour l'affichage rapide (copiées depuis le `Quote` à la création, immuables) ; retirer `payout_reference`/`admin_note`/`completed_at` au profit de `Settlement` (§K.7). `market_rate` et `margin_percentage` ne sont **pas** dupliqués sur `orders` : ils restent uniquement dans `quotes`, consultables par jointure — l'ordre n'a besoin que du taux qui lui a été appliqué (`customer_rate`), pas de sa décomposition.
- **JUSTIFICATION** : la machine d'état et les garanties de concurrence de la Phase 1 (verrou pessimiste, `uq_orders_reference`, index d'expiration) restent **entièrement valables** — seule la source de la donnée monétaire change (devis plutôt que taux). Extraire le règlement clarifie qui fait quoi.
- **IMPACT** : cette table est migrée (`V1`) mais **inutilisée par le code** (module `order` non construit). Migration de remplacement en Phase 4, sans coût de régression.

### K.6 `beneficiaries`

- **EXISTANT** : snapshot immuable 1–1 avec l'ordre, typé `ALIPAY`/`WECHAT_PAY`/`CHINESE_BANK_ACCOUNT`.
- **PROBLÈME** : aucun identifié par cette révision.
- **PROPOSITION** : inchangée.
- **JUSTIFICATION** : conception déjà correcte au regard des nouveaux besoins (traçabilité du bénéficiaire, §Q).
- **IMPACT** : aucun.

### K.7 `payments`

- **EXISTANT** : cycle `SUBMITTED → VERIFIED | REJECTED`, un seul paiement vivant par ordre.
- **PROBLÈME** : aucun — `Payment` continue de signifier exactement « le client a payé », ce qui reste correct dans le nouveau modèle.
- **PROPOSITION** : inchangée structurellement.
- **JUSTIFICATION** : le nouveau concept qui manquait n'était pas côté paiement client, mais côté règlement fournisseur (`Settlement`).
- **IMPACT** : aucun.

### K.8 `payment_proofs`

- **EXISTANT** : métadonnées uniquement, hors base pour le contenu binaire.
- **PROBLÈME** : aucun.
- **PROPOSITION** : inchangée.
- **JUSTIFICATION** : conception déjà alignée avec les exigences de traçabilité (§Q).
- **IMPACT** : aucun.

### K.9 `settlements` — nouvelle table

- **EXISTANT** : n'existe pas ; l'information vivait partiellement dans `orders.payout_reference`/`admin_note`/`completed_at`.
- **PROBLÈME** : confond le cycle de vie du règlement fournisseur avec celui de l'ordre ; ne permet pas de distinguer proprement « qui a exécuté le règlement » de « qui a créé/annulé l'ordre ».
- **PROPOSITION** : table `settlements` détaillée en §H.3 (`orderId`, `settlementStatus`, `settlementReference`, `settledAt`, `executedBy`, `notes`, `amountCny`).
- **JUSTIFICATION** : correspond exactement à la distinction demandée `Order / Payment / Settlement`, et améliore la traçabilité d'audit (« qui a exécuté le règlement, avec quelle référence »).
- **IMPACT** : nouvelle table (Phase 5/6), nouveau module `settlement`. Les colonnes `orders.payout_reference`/`admin_note`/`completed_at` de `V1` ne seront simplement pas exploitées par le code — elles pourront être retirées lors de la migration de remplacement d'`orders` en Phase 4, sans avoir jamais été utilisées.

### K.10 `treasury_accounts`

- **EXISTANT** : `balance`, `reserved_balance`, `low_threshold` par devise.
- **PROBLÈME** : aucun — voir §I.2, le vocabulaire demandé s'y superpose sans changement structurel.
- **PROPOSITION** : inchangée.
- **JUSTIFICATION** : `Available = balance − reserved_balance` était déjà le modèle documenté en Phase 1.
- **IMPACT** : aucun.

### K.11 `treasury_transactions`

- **EXISTANT** : ledger append-only, types `DEPOSIT`/`WITHDRAWAL`/`RESERVATION`/`RELEASE`/`ADJUSTMENT`, index unique empêchant une double réservation/libération/décaissement par ordre.
- **PROBLÈME** : aucun structurel — voir §I.2, seule la lecture sémantique de `WITHDRAWAL` (lié ou non à un `order_id`) était implicite.
- **PROPOSITION** : inchangée structurellement ; documentation clarifiée (fait, §I.2). Option non retenue pour l'instant : ajouter un type `CONSUMPTION` distinct de `WITHDRAWAL` — écartée pour ne pas dupliquer une distinction déjà lisible via `order_id IS NOT NULL`.
- **JUSTIFICATION** : ne pas ajouter de complexité (nouveau type d'enum, nouvelle contrainte) pour une information déjà déductible du schéma existant.
- **IMPACT** : aucun changement de schéma.

### K.12 `risk_flags` — nouvelle table

- **EXISTANT** : n'existe pas.
- **PROBLÈME** : aucune capacité de signalement ou de revue de risque, même minimale.
- **PROPOSITION** : table `risk_flags` (`id`, `flagType` — voir §J.2, `entityType`/`entityId` polymorphe à l'image d'`audit_logs`, `orderId` nullable pour les jointures fréquentes, `severity`, `status` (`OPEN`/`REVIEWED`/`CONFIRMED`/`DISMISSED`), `raisedBy` (nullable = système), `reviewedBy`, `reviewedAt`, `resolutionNote`, `createdAt`).
- **JUSTIFICATION** : capacité minimale demandée (§11 du cadrage), sans construire de moteur de règles.
- **IMPACT** : nouvelle table, Phase 6.5 (voir roadmap §T). N'affecte aucune table existante.

### K.13 `audit_logs`

- **EXISTANT** : générique (`actorId`, `action` typé par enum fermé, `entityType`/`entityId`, `metadata JSONB`), écriture isolée en transaction séparée.
- **PROBLÈME** : aucun structurel — le schéma est déjà assez générique pour couvrir `Quote`, `Settlement`, `RiskFlag` sans modification. Seul le catalogue `AuditAction` doit s'enrichir de nouvelles valeurs (`QUOTE_CREATED`, `QUOTE_EXPIRED`, `SETTLEMENT_EXECUTED`, `RISK_FLAG_RAISED`, `RISK_FLAG_REVIEWED`, etc.).
- **PROPOSITION** : table inchangée ; extension de l'enum applicatif `AuditAction` au moment où chaque module correspondant sera construit.
- **JUSTIFICATION** : c'est exactement la conception « catalogue fermé mais extensible sans migration de schéma » recherchée en Phase 1 (§D) — elle absorbe la révision sans effort.
- **IMPACT** : aucun changement de schéma ; ajout de constantes d'enum au fil des phases suivantes.

### K.14 `system_settings`

- **EXISTANT** : paramètres typés, administrables, incluant déjà `ENABLED_PAYMENT_METHODS` et `TREASURY_RESERVE_ON_ORDER`.
- **PROBLÈME** : aucun structurel ; de nouvelles clés seront nécessaires (`DEFAULT_MARGIN_PERCENTAGE`, `RATE_SOURCE_TYPE_ACTIVE`, un ou plusieurs drapeaux `..._REGULATORY_VALIDATION_REQUIRED`).
- **PROPOSITION** : table inchangée ; nouvelles lignes amorcées par une future migration de seed, à l'image de `V4__seed_settings.sql`.
- **JUSTIFICATION** : conception déjà pensée pour être étendue sans redéploiement de code (§Settings, Phase 2).
- **IMPACT** : nouvelle migration de seed en Phase 3, aucun changement de schéma.

### K.15 Vue relationnelle révisée

```
users 1──n quotes
  │            │
  │            0..1 (consommé par)
  │            ▼
  │          orders 1──1 beneficiaries
  │            │
  │            1──n payments 1──n payment_proofs
  │            │
  │            0..1 settlements
  │            │
  │            1──n order_status_history
  │            │
  │            0..n treasury_transactions ──n──1 treasury_accounts
  │            │
  │            0..n risk_flags
  │
  n──n roles (user_roles)
  │
  1──n audit_logs (actor_id)
  1..n risk_flags (raised_by / reviewed_by)

rate_sources 1──n quotes (rate_source_id — traçabilité de la cotation utilisée)
```

---

## L. Diagramme des dépendances entre modules

```
                        ┌───────────────────┐
                        │      common       │
                        └─────────┬─────────┘
   ┌──────────────┬───────────────┼───────────────┬──────────────┬─────────────┐
   │              │               │               │              │             │
┌──▼──────┐  ┌────▼─────┐   ┌─────▼──────┐  ┌─────▼─────┐  ┌─────▼──────┐ ┌────▼─────┐
│ config  │  │ security │   │  settings  │  │   audit   │  │  storage   │ │ compliance│
└─────────┘  └────┬─────┘   └─────┬──────┘  └─────┬─────┘  └─────┬──────┘ │ (drapeaux │
                  │               │               │              │        │ REGULATORY│
             ┌────▼─────┐         │               │              │        │ _VALIDATION│
             │   auth   │         │               │              │        │ _REQUIRED)│
             └────┬─────┘         │               │              │        └─────┬────┘
                  │               │               │              │              │
             ┌────▼─────┐         │               │              │              │
             │   user   │◄────────┴───────────────┤              │              │
             │ +segment │                         │              │              │
             └────┬─────┘                         │              │              │
                  │                               │              │              │
           ┌──────▼────────┐                      │              │              │
           │  rate         │  RateProvider (Manual/Market/P2P)   │              │
           │  RateEngine   │  → marketRate + marge → customerRate│              │
           └──────┬────────┘                      │              │              │
                  │                               │              │              │
           ┌──────▼────────┐                      │              │              │
           │    quote      │  snapshot immuable, verrou 30 min   │              │
           └──────┬────────┘                      │              │              │
                  │ quote_id                       │              │              │
           ┌──────▼────────┐   réservation  ┌─────▼──────┐       │              │
           │     order     │───────────────►│  treasury  │       │              │
           │ StateMachine  │◄───────────────│ Available/ │       │              │
           └──────┬────────┘   libération   │ Reserved/  │       │              │
                  │            /consommation│ Consumed   │       │              │
           ┌──────▼────────┐                └─────▲──────┘       │              │
           │    payment    │──────────── preuves ──┼──────────────┘              │
           └──────┬────────┘                       │                            │
                  │                                │                            │
           ┌──────▼────────┐                       │                            │
           │  settlement   │───────── consommation ─┘                           │
           │ (China payout)│                                                    │
           └──────┬────────┘                                                    │
                  │                                                             │
           ┌──────▼────────┐                                                    │
           │     risk      │◄───────────────────────────────────────────────────┘
           │  RiskFlag     │  observe order/payment/quote, ne bloque rien seul
           └──────┬────────┘
                  │
           ┌──────▼────────────────────────────────────────┐
           │                    admin                       │
           │  Orchestration transverse — aucune règle propre │
           └──────────────────────────────────────────────┘
```

**Règle de dépendance inchangée** : les flèches ne remontent jamais. `treasury` ne connaît toujours pas `order` en détail (référence opaque `order_id`). `risk` **observe** (lecture seule sur les entités des autres modules pour évaluer des règles) mais n'est jamais appelé de façon bloquante par `order`/`payment` dans le MVP — il consomme des événements/état, il n'en émet pas vers le domaine transactionnel.

---

## M. Flux métier de bout en bout

```
CLIENT                          BACKEND                              ADMIN
──────                          ───────                              ─────
Inscription (+ segment optionnel) ──► users
Connexion ──────────────────────────► JWT
Consulte le taux ───────────────────► GET /rates/current (customerRate uniquement)
Simule 100 000 CFA ─────────────────► POST /quotes/simulate   (stateless)
                                       marketRate, marge, frais, customerRate, amountCny
Confirme ───────────────────────────► POST /quotes             (crée le Quote, verrou 30 min)
                                       └─ si le marché a bougé entre-temps → 409 RATE_CHANGED
Confirme l'ordre ───────────────────► POST /orders  { quoteId }  (Idempotency-Key)
                                       ├─ bornes vérifiées
                                       ├─ liquidité CNY vérifiée
                                       ├─ RESERVATION CNY
                                       └─ status AWAITING_PAYMENT
Paie en Mobile Money (hors plateforme)
Déclare la référence ───────────────► POST /orders/{id}/payments
                                       status PAYMENT_SUBMITTED
Téléverse la preuve ────────────────► POST /orders/{id}/payment-proof
                                                                 ◄─── file d'attente admin
                                                                 Contrôle la preuve
                                       ◄──────────────────────── POST /payments/{id}/verify
                                       DEPOSIT XOF · status PAYMENT_VERIFIED
                                                                 Décaisse en Chine (manuel)
                                       ◄──────────────────────── POST /orders/{id}/start-processing
                                       status PROCESSING
                                                                 Déclare le règlement
                                       ◄──────────────────────── POST /settlements  { orderId, reference }
                                       WITHDRAWAL CNY (consommation) · Settlement.EXECUTED
                                       status COMPLETED
Suit son ordre ─────────────────────► GET /orders/{id}   (inclut le devis, le paiement, le règlement)
```

---

## N. États et transitions

### N.1 `Quote`

```
(simulation — stateless, aucun état persisté)
        │
        ▼ confirmation
     ACTIVE ──────────────► CONFIRMED   (consommé par un Order — terminal)
        │
        └──────────────────► EXPIRED    (30 min écoulées sans confirmation — terminal)
```

Un `Quote` `ACTIVE` non confirmé dans les 30 minutes expire et ne peut plus produire d'`Order` ; le client doit en obtenir un nouveau (même logique que l'expiration d'`Order` en Phase 1, appliquée un cran plus tôt dans le parcours).

### N.2 `Order`

Machine d'état **inchangée** par rapport à la Phase 1 (8 statuts, mêmes transitions, mêmes garanties de verrou pessimiste et d'idempotence — voir Partie II, §E). Seule différence : la transition `PROCESSING → COMPLETED` n'écrit plus directement `payout_reference`/`completed_at` sur `Order`, elle est désormais **déclenchée par** la création d'un `Settlement` en statut `EXECUTED` (voir N.3).

### N.3 `Settlement`

```
(création, à start-processing ou juste après) ──► PENDING
                                                      │
                                     admin déclare l'exécution
                                                      ▼
                                                  EXECUTED  (terminal — déclenche Order → COMPLETED)
```

Pas de retour en arrière : un `Settlement` mal renseigné se corrige par une note/audit, pas par une réouverture (cohérent avec le principe « pas de décaissement automatisé, mais traçabilité complète de l'exécution manuelle »).

### N.4 `RiskFlag`

```
OPEN ──► REVIEWED ──┬──► CONFIRMED   (le signalement était fondé — alimente une décision admin séparée : blocage, rejet…)
                     └──► DISMISSED  (faux positif)
```

---

## O. API prévues (Phase 3 et suivantes — non implémentées à ce stade)

Cette section **remplace** F.1–F.3 de la Partie II pour tout ce qui touche taux/ordre/paiement/règlement. Les endpoints `auth`/`user`/`settings`/`admin` déjà livrés en Phase 2 (Partie II, §F) restent inchangés et ne sont pas repris ici.

| Méthode | Chemin | Accès | Remplace / précise |
|---|---|---|---|
| `GET` | `/api/rates/current` | Public | `GET /exchange-rate/current` — n'expose que `customerRate`, jamais `marketRate` ni la marge (§P) |
| `POST` | `/api/quotes/simulate` | Public ou authentifié | `POST /exchange/simulate` — stateless |
| `POST` | `/api/quotes` | Authentifié | **Nouveau** — crée le `Quote` confirmé, démarre le verrou 30 min |
| `GET` | `/api/quotes/{id}` | Authentifié, propriétaire | **Nouveau** |
| `POST` | `/api/orders` | Authentifié | Body `{ quoteId, beneficiary, note }` au lieu de `{ amountCfa, beneficiary }` |
| `GET` | `/api/orders`, `/api/orders/{id}` | Authentifié, propriétaire | Inchangé dans l'esprit (Phase 1, §F.2) |
| `POST` | `/api/orders/{id}/cancel` | Authentifié, propriétaire | Inchangé |
| `POST` | `/api/orders/{id}/payments` | Authentifié, propriétaire | Inchangé |
| `POST` | `/api/orders/{id}/payment-proof` | Authentifié, propriétaire | Inchangé |
| `GET` | `/api/admin/rate-sources` | `ADMIN` | Remplace `GET /admin/exchange-rates` |
| `POST` | `/api/admin/rate-sources` | `ADMIN` | Remplace `POST /admin/exchange-rates` — publie une cotation `ManualRateProvider` |
| `GET`/`PUT` | `/api/admin/pricing-settings` | `ADMIN` | **Nouveau** — marge par défaut, frais |
| `POST` | `/api/admin/payments/{id}/verify`, `/reject` | `ADMIN` | Inchangé |
| `POST` | `/api/admin/orders/{id}/start-processing` | `ADMIN` | Inchangé |
| `POST` | `/api/admin/settlements` | `ADMIN` | **Nouveau** — remplace `POST /admin/orders/{id}/complete` |
| `GET` | `/api/admin/settlements/{id}` | `ADMIN` | **Nouveau** |
| `GET`/`POST` | `/api/admin/treasury/**` | `ADMIN` | Inchangé (Phase 1, §F.3) |
| `GET` | `/api/admin/risk-flags` | `ADMIN` | **Nouveau** |
| `POST` | `/api/admin/risk-flags/{id}/review` | `ADMIN` | **Nouveau** |
| `GET` | `/api/admin/dashboard` | `ADMIN` | Enrichi des KPI business (§R) |

---

## P. Sécurité

Aucun changement de posture par rapport à la Phase 2 (RBAC, JWT, 404 sur accès horizontal, CORS strict, anti brute-force, revalidation du statut du compte à chaque requête) — voir Partie II, §J.2. Précisions propres à cette révision :

- **Le `marketRate` et la `marginPercentage` d'un `Quote` ne sont jamais exposés au client** dans les réponses API publiques/authentifiées côté `USER` — seuls `customerRate`, `feeCfa`, `amountCny` le sont. Ce sont des données commercialement sensibles, réservées aux endpoints `ADMIN`.
- La règle « 404, jamais 403, sur une ressource d'autrui » s'applique identiquement à `Quote`, `Order`, `Settlement` (côté client — `Settlement` n'est de toute façon jamais exposé côté client, uniquement côté admin).
- Les futurs endpoints `rate-sources`, `pricing-settings`, `settlements`, `risk-flags` sont tous `ADMIN`-only, protégés par la double barrière déjà en place (`SecurityFilterChain` + `@PreAuthorize`).

---

## Q. Audit / traçabilité

Chaîne de bout en bout demandée et sa correspondance avec le modèle :

```
USER               → users.id (acteur ou propriétaire)
QUOTE              → quotes (marketRate, margin, customerRate, fee, rateSourceId)
ORDER              → orders.quote_id
PAYMENT            → payments (method, transactionReference, amountCfa)
PAYMENT PROOF      → payment_proofs (checksum, storageKey)
PAYMENT VERIFICATION → payments.reviewedBy / verifiedAt
LIQUIDITY RESERVATION → treasury_transactions (RESERVATION, order_id)
SETTLEMENT         → settlements (executedBy, settlementReference, settledAt)
CHINA PAYOUT       → settlements (même entité — l'exécution EST le payout, manuel)
COMPLETION         → orders.status = COMPLETED + order_status_history
```

Chaque étape est déjà, ou sera (Quote/Settlement/RiskFlag), un enregistrement persistant et daté, permettant de répondre à toutes les questions listées dans le cadrage :

| Question | Réponse dans le modèle |
|---|---|
| Qui ? | `audit_logs.actor_id` / `payments.reviewed_by` / `settlements.executed_by` |
| Quoi ? | `audit_logs.action` (enum fermé, extensible) |
| Quand ? | `created_at`/`updated_at` sur chaque entité, UTC |
| Combien ? | `quotes.amount_cfa` / `amount_cny` |
| À quel taux ? | `quotes.market_rate` et `quotes.customer_rate` |
| Avec quels frais ? | `quotes.fee_cfa` (+ `margin_percentage` séparément) |
| Quelle source de taux ? | `quotes.rate_source_id` → `rate_sources.provider_type` |
| Quelle preuve ? | `payment_proofs.storage_key` / `checksum_sha256` |
| Qui a validé ? | `payments.reviewed_by` |
| Qui a exécuté le règlement ? | `settlements.executed_by` |
| Quelle référence de transaction ? | `payments.transaction_reference` / `settlements.settlement_reference` |

Le schéma générique d'`audit_logs` (Phase 1, §C.12) n'a besoin d'**aucune** modification structurelle pour couvrir cette chaîne — seul son catalogue `AuditAction` s'enrichit au fil des phases (§K.13).

---

## R. KPI business

Le dashboard admin (Phase 8) affichera, sans que le modèle de données ne l'empêche :

| KPI | Source |
|---|---|
| Clients actifs | `COUNT(users) WHERE status = ACTIVE`, filtrable par `customer_segment` |
| Ordres du jour | `COUNT(orders) WHERE created_at::date = today` |
| Volume XOF du jour / mensuel | `SUM(orders.amount_cfa)` sur la période |
| Volume CNY du jour / mensuel | `SUM(orders.amount_cny)` sur la période |
| Ticket moyen | `AVG(orders.amount_cfa)` |
| Taux de conversion Quote → Order | `COUNT(orders) / COUNT(quotes WHERE status IN (CONFIRMED, EXPIRED))` |
| Taux de paiement réussi | `COUNT(payments WHERE status = VERIFIED) / COUNT(payments)` |
| Taux d'annulation | `COUNT(orders WHERE status = CANCELLED) / COUNT(orders)` |
| Taux de rejet | `COUNT(payments WHERE status = REJECTED) / COUNT(payments)` |
| Temps moyen de traitement | `AVG(settlements.settled_at − orders.created_at)` |
| Marge moyenne | `AVG(quotes.margin_percentage)`, ou `SUM((customer_rate − market_rate) × amount_cny)` |
| Frais générés | `SUM(quotes.fee_cfa)` |
| Liquidité disponible / réservée | `treasury_accounts.balance − reserved_balance` / `reserved_balance` |

Tous ces indicateurs se calculent par agrégation directe sur le modèle révisé — **aucun KPI de cette liste n'exige une table ou une colonne supplémentaire au-delà de ce qui est déjà proposé en §K.** Si le volume de données rend ces agrégations coûteuses en lecture directe, une vue matérialisée ou un modèle de lecture dédié pourra être introduit plus tard sans changer le modèle transactionnel — non nécessaire au MVP.

---

## S. Risques techniques et business

Complète la liste de la Partie II, §J (risques 1 à 27, tous toujours valables). Nouveaux risques introduits par cette révision :

| # | Risque | Gravité | Parade |
|---|---|---|---|
| 28 | **Marge et frais confondus par erreur applicative** (ex. un futur développeur ajoute la marge dans `feeCfa`) | 🔴 Critique | Les deux champs restent des colonnes distinctes dans `quotes`, jamais fusionnées en amont du calcul ; test dédié vérifiant que `customerRate` et `feeCfa` varient indépendamment |
| 29 | **Désynchronisation Quote/Order** (un ordre créé après l'expiration technique du devis, race condition) | 🟠 Élevé | Vérification de `quote.status = ACTIVE AND now() < expiresAt` **dans la même transaction verrouillée** que la création de l'ordre, comme pour l'expiration des ordres en Phase 1 |
| 30 | **Settlement enregistré sans consommation de trésorerie correspondante** (double comptabilité) | 🔴 Critique | La transition `Settlement → EXECUTED` et l'écriture `treasury_transactions (WITHDRAWAL)` doivent être **atomiques** (même transaction applicative), avec le même index unique `(order_id, WITHDRAWAL)` déjà en place |
| 31 | **Faux négatif de `RiskFlag`** (un cas à risque n'est jamais signalé, silence rassurant à tort) | 🟡 Moyen | Le MVP ne prétend pas détecter automatiquement grand-chose : la revue manuelle et le contrôle de preuve restent la première ligne de défense, `RiskFlag` est un complément, pas un filet de sécurité unique |
| 32 | **Dépendance réglementaire non validée activée par erreur** (un P2P/crypto provider ou un canal de paiement non validé légalement passé en production) | 🔴 Critique (business/légal) | Paramètre `..._REGULATORY_VALIDATION_REQUIRED` explicite (§J.3), désactivé par défaut ; toute activation est un acte administratif documenté, jamais un défaut de configuration |
| 33 | **Contournement des limites par segmentation** (une fois les limites par segment introduites, un client mal classé accède à un plafond inadapté) | 🟡 Moyen | `customer_segment` reste `UNSPECIFIED` par défaut avec les bornes globales actuelles tant qu'aucune règle par segment n'est codée ; l'introduction de limites par segment devra être testée en confirmant qu'aucune combinaison ne relève le plafond global sans action administrative explicite |
| 34 | **Sur-dépendance précoce à une source de liquidité P2P** (le business plan s'appuie sur Binance/OKX avant validation réglementaire ou avant qu'une deuxième source ne soit réellement nécessaire) | 🟠 Élevé (business) | `ManualRateProvider` reste la seule source opérationnelle du MVP ; `P2PRateProvider` demeure un nom réservé dans l'interface, non implémenté, non planifié avant feu vert réglementaire explicite |

---

## T. Roadmap révisée

> **`[SUPERSEDED → voir Partie 0]`.** Le tableau ci-dessous était le **plan de construction**.
> Il ne reflète plus l'état réel : les phases 3 à 6.5 sont livrées (sauf `risk`), et trois
> modules non planifiés (`wallet`, `preferredrate`, `notification`) ont été ajoutés. Pour le
> statut effectif, se référer à la **Partie 0** et à **[BACKEND.md](BACKEND.md)**. Conservé
> ci-dessous pour mémoire de l'intention initiale.

| Phase | Contenu | Statut |
|---|---|---|
| **2 — Fondations** | Squelette Maven, Docker Compose, Flyway `V1`–`V6`, `common`, sécurité JWT, `auth`, `user`, `settings`, seed admin, OpenAPI | ✅ **Livré** (39/39 tests) |
| **2.5 — Révision architecturale** | Ce document. Aucun code. | ✅ **Ce livrable** — en attente de validation |
| **3 — Rate Engine + Quote** | `RateProvider` (interface + `ManualRateProvider`), `RateEngine`, `rate_sources`, `quotes`, simulateur, verrouillage 30 min, `409 RATE_CHANGED` | ⏳ Bloqué par la validation de cette révision |
| **4 — Order** | `Order` référence `Quote`, `OrderStateMachine` (inchangée dans sa forme), bénéficiaires, annulation, expiration | ⏳ |
| **5 — Payment** | `Payment`, `FileStorageService` + implémentation locale, upload/validation de preuve, vérification/rejet admin | ⏳ |
| **5.5 — Settlement** | `Settlement`, endpoints admin, déclenchement `Order → COMPLETED` | ⏳ |
| **6 — Treasury** | Comptes, ledger, réservation/libération/consommation (vocabulaire clarifié, schéma déjà prêt) | ⏳ |
| **6.5 — Risk / Compliance (léger)** | `risk_flags`, revue manuelle, drapeaux `REGULATORY_VALIDATION_REQUIRED` amorcés dans `system_settings` | ⏳ |
| **7 — Angular client** | Auth, dashboard, simulateur/devis, création d'ordre, minuteur 30 min, upload | ⏳ |
| **8 — Angular admin** | Dashboard KPI (§R), file de paiements, ordres, règlements, utilisateurs, sources de taux, trésorerie, risque | ⏳ |
| **9 — Tests** | Unitaires, intégration Testcontainers, sécurité, concurrence — étendus aux nouveaux modules | ⏳ |
| **10 — Livraison** | Docker, README, guide de déploiement, variables d'environnement | ⏳ |

**Points de contrôle réglementaire explicites**, à ne franchir qu'après feu vert business/légal, jamais par défaut technique :

- Activation de `WavePaymentMethod` ou `BankTransferPaymentMethod` (au-delà de `MOBILE_MONEY`).
- Implémentation ou activation de `MarketRateProvider` (dépendance à une API de marché externe).
- Implémentation ou activation de `P2PRateProvider` (Binance/OKX ou équivalent) comme source de liquidité ou de données.

Aucune de ces trois activations n'est planifiée dans les phases 3 à 10 ci-dessus.

---

# Partie II — Architecture initiale (Phase 1 — référence historique)

> Conservée telle qu'approuvée avant la Phase 2, à des fins de traçabilité de la conception. Les sous-sections **`[SUPERSEDED]`** ci-dessous sont remplacées par la Partie I ; le reste (sécurité applicative, arborescence backend/frontend, garanties de concurrence, décisions Java/Maven) demeure la référence en vigueur.

## A. Architecture globale

### A.1 Vue macro

```
┌────────────────────────────────────────────────────────────────────────┐
│                             NAVIGATEUR                                 │
│   Angular 20 (standalone + signals) · Angular Material · RxJS          │
│   Espace CLIENT            |            Espace ADMIN                   │
└───────────────────────────┬────────────────────────────────────────────┘
                            │ HTTPS / JSON · Bearer JWT
                            │ (HttpInterceptor : auth, erreurs, loader)
┌───────────────────────────▼────────────────────────────────────────────┐
│                    BACKEND — Spring Boot 3.5 / Java 21                 │
│                                                                        │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ COUCHE WEB      Controllers REST · DTO · Bean Validation          │  │
│  │                 GlobalExceptionHandler · ApiResponse<T>           │  │
│  ├──────────────────────────────────────────────────────────────────┤  │
│  │ COUCHE SÉCURITÉ SecurityFilterChain · JwtAuthenticationFilter     │  │
│  │                 RBAC (@PreAuthorize) · contrôle de propriété      │  │
│  ├──────────────────────────────────────────────────────────────────┤  │
│  │ COUCHE MÉTIER   Services transactionnels (@Transactional)         │  │
│  │                 OrderStateMachine · MoneyCalculator               │  │
│  │                 TreasuryService · AuditService · SettingsService  │  │
│  ├──────────────────────────────────────────────────────────────────┤  │
│  │ COUCHE ACCÈS    Spring Data JPA Repositories · MapStruct Mappers  │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                                        │
│  Port sortant : FileStorageService (interface)                         │
└──────────┬──────────────────────────────────┬──────────────────────────┘
           │ JDBC                             │ SPI
┌──────────▼───────────────┐      ┌───────────▼──────────────────────────┐
│  PostgreSQL 16           │      │  STOCKAGE FICHIERS                   │
│  Flyway (versionné)      │      │  LocalFileStorageService (MVP)       │
│  Données + ledger        │      │  S3FileStorageService (extension)    │
│  Métadonnées fichiers    │      │  → preuves de paiement (hors BDD)    │
└──────────────────────────┘      └──────────────────────────────────────┘
```

### A.2 Décisions structurantes

| # | Décision | Justification |
|---|----------|---------------|
| 1 | **Monolithe modulaire** (package-by-feature), pas de microservices | Domaine transactionnel fortement cohérent : ordre + paiement + trésorerie doivent partager la **même transaction ACID**. Découper obligerait à inventer une saga distribuée sans bénéfice. |
| 2 | **Aucune logique métier hors du backend** | Le frontend est un client passif : il affiche un montant calculé par l'API, jamais un montant qu'il calcule lui-même. |
| 3 | **`BigDecimal` partout**, `NUMERIC` en base | Interdiction absolue de `double`/`float`/`REAL` sur des montants. |
| 4 | **PostgreSQL source de vérité unique**, y compris pour la trésorerie | Ledger append-only + soldes matérialisés verrouillés en pessimiste. |
| 5 | **Aucun décaissement Yuan automatisé** | Le système orchestre un *workflow administratif*. Le virement réel reste humain, tracé par `payoutReference`. |
| 6 | **Stateless côté serveur** (JWT, pas de session HTTP) | Scalabilité horizontale, pas de session collante. |

---

## B. Diagramme logique des modules `[SUPERSEDED → voir Partie I, §L]`

```
                        ┌───────────────────┐
                        │      common       │  ApiResponse, ErrorResponse,
                        │  (socle partagé)  │  BusinessException, Money,
                        └─────────┬─────────┘  PageResponse
                                  │ (dépendance de tous)
   ┌──────────────┬───────────────┼───────────────┬──────────────┐
   │              │               │               │              │
┌──▼──────┐  ┌────▼─────┐   ┌─────▼──────┐  ┌─────▼─────┐  ┌─────▼──────┐
│ config  │  │ security │   │  settings  │  │   audit   │  │  storage   │
│ OpenAPI │  │ JWT, RBAC│   │ SystemSet. │  │ AuditLog  │  │ FileStorage│
│ Jackson │  │ Filtres  │   │ (bornes,   │  │ Service   │  │ Local / S3 │
│ CORS    │  │          │   │  durées)   │  │           │  │            │
└─────────┘  └────┬─────┘   └─────┬──────┘  └─────┬─────┘  └─────┬──────┘
                  │               │               │              │
             ┌────▼─────┐         │               │              │
             │   auth   │         │               │              │
             │ register │         │               │              │
             │ login/me │         │               │              │
             └────┬─────┘         │               │              │
                  │               │               │              │
             ┌────▼─────┐         │               │              │
             │   user   │◄────────┴───────────────┤              │
             │ User,Role│                         │              │
             └────┬─────┘                         │              │
                  │                               │              │
           ┌──────▼────────┐                      │              │
           │   exchange    │  taux, historique,   │              │
           │ Rate, Simulate│  simulateur, calcul  │              │
           └──────┬────────┘                      │              │
                  │                               │              │
           ┌──────▼────────┐   réservation  ┌─────▼──────┐       │
           │     order     │───────────────►│  treasury  │       │
           │ Order, Benef. │   libération   │ Account    │       │
           │ StateMachine  │◄───────────────│ Transaction│       │
           └──────┬────────┘   contrôle     └────────────┘       │
                  │            liquidité                         │
           ┌──────▼────────┐                                     │
           │    payment    │──────────── preuves ────────────────┘
           │ Payment,Proof │
           └──────┬────────┘
                  │
           ┌──────▼────────────────────────────────────────┐
           │                    admin                      │
           │  Orchestration transverse : dashboard,        │
           │  vérification paiements, exécution ordres,    │
           │  gestion utilisateurs, taux, trésorerie       │
           └───────────────────────────────────────────────┘
```

**Règle de dépendance** : les flèches ne remontent jamais. `treasury` ne connaît pas `order` (il reçoit un `orderId` opaque comme référence de corrélation). `admin` orchestre en appelant les services des autres modules — il **ne contient aucune règle métier propre**, uniquement agrégation et délégation.

---

## C. Modèle de données complet

Conventions globales :

- **PK** : `UUID` (`gen_random_uuid()`, natif PG 13+) — identifiant public non énumérable.
- **Horodatage** : `TIMESTAMPTZ`, stocké en **UTC**. Côté Java : `Instant`.
- **Montants** : `NUMERIC`. XOF = devise **sans sous-unité** (scale métier 0) ; CNY = 2 décimales. Stockage `(19,2)` pour homogénéité, contrainte de scale appliquée au niveau service.
- **Audit technique** : `created_at`, `updated_at` sur toute entité mutable.
- **Verrou optimiste** : colonne `version BIGINT` sur les entités concurrentes.

### C.1 `users`

| Colonne | Type | Contraintes |
|---|---|---|
| `id` | UUID | PK, default `gen_random_uuid()` |
| `phone` | VARCHAR(20) | **NOT NULL, UNIQUE**, format E.164 (`+225...`) |
| `password_hash` | VARCHAR(100) | NOT NULL (BCrypt cost 12) |
| `first_name` | VARCHAR(80) | NOT NULL |
| `last_name` | VARCHAR(80) | NOT NULL |
| `email` | VARCHAR(160) | NULL, UNIQUE |
| `status` | VARCHAR(20) | NOT NULL, DEFAULT `ACTIVE`, CHECK ∈ (`ACTIVE`, `BLOCKED`) |
| `blocked_at` | TIMESTAMPTZ | NULL |
| `blocked_reason` | VARCHAR(500) | NULL |
| `blocked_by` | UUID | NULL, FK → `users(id)` |
| `last_login_at` | TIMESTAMPTZ | NULL |
| `created_at` / `updated_at` | TIMESTAMPTZ | NOT NULL |
| `version` | BIGINT | NOT NULL DEFAULT 0 |

Index : `uq_users_phone (phone)`, `uq_users_email (email) WHERE email IS NOT NULL`, `idx_users_status_created (status, created_at DESC)`.

> `password_hash` n'est **jamais** exposé : aucun DTO de sortie ne le porte, et une projection JPA dédiée est utilisée pour les listes admin.
>
> **`[Étendue par Partie I, §K.1]`** : une colonne `customer_segment` nullable sera ajoutée par une migration future ; le reste de cette table n'est pas affecté.

### C.2 `roles` / `user_roles`

`roles` : `id SMALLINT PK`, `code VARCHAR(20) UNIQUE NOT NULL` (`USER`, `ADMIN`), `label VARCHAR(60)`.

`user_roles` : `user_id UUID FK ON DELETE CASCADE`, `role_id SMALLINT FK`, **PK composite** `(user_id, role_id)`, index `idx_user_roles_role (role_id)`.

### C.3 `exchange_rates` `[SUPERSEDED → voir Partie I, §K.3 (rate_sources) et §K.4 (quotes)]`

| Colonne | Type | Contraintes |
|---|---|---|
| `id` | UUID | PK |
| `cfa_per_cny` | NUMERIC(18,6) | NOT NULL, CHECK `> 0` — *« 1 CNY = X CFA »* |
| `fee_percentage` | NUMERIC(6,4) | NOT NULL DEFAULT 0, CHECK `>= 0 AND < 100` |
| `fixed_fee_cfa` | NUMERIC(19,2) | NOT NULL DEFAULT 0, CHECK `>= 0` |
| `effective_from` | TIMESTAMPTZ | NOT NULL |
| `effective_to` | TIMESTAMPTZ | **NULL = taux courant** |
| `note` | VARCHAR(500) | NULL |
| `created_by` | UUID | NOT NULL, FK → `users(id)` |
| `created_at` | TIMESTAMPTZ | NOT NULL |

Index :

- `CREATE UNIQUE INDEX uq_exchange_rate_current ON exchange_rates ((effective_to IS NULL)) WHERE effective_to IS NULL;`
  → **garantit au niveau SQL qu'il n'existe jamais deux taux courants simultanés**, même en cas de double POST concurrent.
- `idx_exchange_rates_effective_from (effective_from DESC)`.

Publier un nouveau taux = `UPDATE ... SET effective_to = now() WHERE effective_to IS NULL` puis `INSERT`, dans **une seule transaction**. Table **append-only** : aucun `UPDATE` du montant, l'historique est intégral.

> Cette table est migrée (`V1`) mais **aucun code applicatif n'en dépend** à ce jour (module `exchange` non construit) — son remplacement par `rate_sources` (Partie I, §K.3) n'a aucun coût de régression.

### C.4 `orders` `[SUPERSEDED partiellement → voir Partie I, §K.5]`

| Colonne | Type | Contraintes |
|---|---|---|
| `id` | UUID | PK |
| `reference` | VARCHAR(24) | NOT NULL UNIQUE — `ORD-202608-000123` (séquence PG) |
| `user_id` | UUID | NOT NULL, FK → `users(id)` |
| `status` | VARCHAR(24) | NOT NULL, CHECK ∈ *OrderStatus* |
| `amount_cfa` | NUMERIC(19,2) | NOT NULL, CHECK `> 0` — **brut payé par le client** |
| `fee_cfa` | NUMERIC(19,2) | NOT NULL, CHECK `>= 0` |
| `net_amount_cfa` | NUMERIC(19,2) | NOT NULL, CHECK `> 0` |
| `amount_cny` | NUMERIC(19,2) | NOT NULL, CHECK `> 0` — **à décaisser** |
| `exchange_rate` | NUMERIC(18,6) | NOT NULL — **snapshot figé** |
| `exchange_rate_id` | UUID | NOT NULL, FK → `exchange_rates(id)` |
| `fee_percentage` | NUMERIC(6,4) | NOT NULL — snapshot |
| `fixed_fee_cfa` | NUMERIC(19,2) | NOT NULL — snapshot |
| `rate_locked_at` | TIMESTAMPTZ | NOT NULL |
| `rate_expires_at` | TIMESTAMPTZ | NOT NULL |
| `note` | VARCHAR(500) | NULL (client) |
| `admin_note` | VARCHAR(1000) | NULL |
| `payout_reference` | VARCHAR(100) | NULL |
| `cancellation_reason` | VARCHAR(500) | NULL |
| `rejection_reason` | VARCHAR(500) | NULL |
| `treasury_reserved` | BOOLEAN | NOT NULL DEFAULT FALSE |
| `completed_at` / `cancelled_at` | TIMESTAMPTZ | NULL |
| `created_at` / `updated_at` | TIMESTAMPTZ | NOT NULL |
| `version` | BIGINT | NOT NULL DEFAULT 0 |

Index :

- `idx_orders_user_created (user_id, created_at DESC)` — historique client.
- `idx_orders_status_created (status, created_at DESC)` — file admin.
- `idx_orders_expiry (rate_expires_at) WHERE status = 'AWAITING_PAYMENT'` — **index partiel** pour le job d'expiration (balayage minimal).
- `uq_orders_reference (reference)`.

**Aucun `UPDATE` du couple `(exchange_rate, amount_cny)` après création.** C'est une invariante testée explicitement — **ce principe est conservé à l'identique dans le modèle révisé**, appliqué désormais à `quote_id` plutôt qu'à `exchange_rate_id`.

> `exchange_rate_id`/`payout_reference`/`admin_note`/`completed_at` sont remplacés conceptuellement (Partie I, §K.5, §K.9). La machine d'état (§E ci-dessous), les index et les garanties de concurrence restent valables sans changement.

### C.5 `beneficiaries`

Snapshot **immuable** du bénéficiaire, en relation 1–1 avec l'ordre.

| Colonne | Type | Contraintes |
|---|---|---|
| `id` | UUID | PK |
| `order_id` | UUID | **NOT NULL UNIQUE**, FK → `orders(id)` ON DELETE CASCADE |
| `type` | VARCHAR(24) | NOT NULL, CHECK ∈ *BeneficiaryType* |
| `full_name` | VARCHAR(120) | NOT NULL |
| `identifier` | VARCHAR(120) | NOT NULL (compte Alipay / WeChat / n° bancaire) |
| `bank_name` | VARCHAR(120) | NULL |
| `bank_branch` | VARCHAR(120) | NULL |
| `created_at` | TIMESTAMPTZ | NOT NULL |

CHECK métier : `type <> 'CHINESE_BANK_ACCOUNT' OR bank_name IS NOT NULL`.
Index : `idx_beneficiaries_identifier (identifier)` — détection de bénéficiaires récurrents / anti-fraude.

> *Choix* : table séparée plutôt que colonnes inline dans `orders` → garde `orders` étroit, isole les données personnelles (chiffrement au repos possible plus tard sans toucher aux ordres), et ouvre la voie à un carnet d'adresses réutilisable (`saved_beneficiaries`) sans migration destructive.

### C.6 `payments`

Un ordre peut porter **plusieurs** paiements (un rejet suivi d'une resoumission).

| Colonne | Type | Contraintes |
|---|---|---|
| `id` | UUID | PK |
| `order_id` | UUID | NOT NULL, FK → `orders(id)` |
| `method` | VARCHAR(24) | NOT NULL, CHECK ∈ *PaymentMethod* |
| `status` | VARCHAR(16) | NOT NULL, CHECK ∈ (`SUBMITTED`, `VERIFIED`, `REJECTED`) |
| `amount_cfa` | NUMERIC(19,2) | NOT NULL, CHECK `> 0` |
| `transaction_reference` | VARCHAR(100) | NOT NULL |
| `payer_phone` | VARCHAR(20) | NULL |
| `submitted_at` | TIMESTAMPTZ | NOT NULL |
| `verified_at` / `rejected_at` | TIMESTAMPTZ | NULL |
| `reviewed_by` | UUID | NULL, FK → `users(id)` |
| `rejection_reason` | VARCHAR(500) | NULL |
| `created_at` / `updated_at` | TIMESTAMPTZ | NOT NULL |
| `version` | BIGINT | NOT NULL DEFAULT 0 |

Index :

- `idx_payments_pending (status, submitted_at) WHERE status = 'SUBMITTED'` — file d'attente admin.
- `uq_payments_active_per_order (order_id) WHERE status <> 'REJECTED'` → **un seul paiement vivant par ordre**.
- `uq_payments_txref (method, transaction_reference) WHERE status <> 'REJECTED'` → **empêche de réutiliser la même référence Mobile Money sur deux ordres**.
- `idx_payments_order (order_id)`.

### C.7 `payment_proofs`

**Aucun contenu binaire en base.** Métadonnées uniquement.

| Colonne | Type | Contraintes |
|---|---|---|
| `id` | UUID | PK |
| `payment_id` | UUID | NOT NULL, FK → `payments(id)` ON DELETE CASCADE |
| `file_name` | VARCHAR(255) | NOT NULL — nom **assaini**, jamais le nom brut du client |
| `content_type` | VARCHAR(100) | NOT NULL, CHECK ∈ allowlist |
| `storage_key` | VARCHAR(500) | NOT NULL UNIQUE — chemin logique généré serveur |
| `storage_provider` | VARCHAR(16) | NOT NULL DEFAULT `LOCAL` |
| `size_bytes` | BIGINT | NOT NULL, CHECK `> 0` |
| `checksum_sha256` | CHAR(64) | NOT NULL — déduplication + intégrité |
| `uploaded_by` | UUID | NOT NULL, FK → `users(id)` |
| `uploaded_at` | TIMESTAMPTZ | NOT NULL |

Index : `idx_payment_proofs_payment (payment_id)`.

### C.8 `treasury_accounts`

| Colonne | Type | Contraintes |
|---|---|---|
| `id` | UUID | PK |
| `currency` | VARCHAR(3) | NOT NULL UNIQUE, CHECK ∈ (`XOF`, `CNY`) |
| `balance` | NUMERIC(21,2) | NOT NULL DEFAULT 0 — solde total |
| `reserved_balance` | NUMERIC(21,2) | NOT NULL DEFAULT 0, CHECK `>= 0` |
| `low_threshold` | NUMERIC(21,2) | NOT NULL DEFAULT 0 — seuil d'alerte dashboard |
| `updated_at` | TIMESTAMPTZ | NOT NULL |
| `version` | BIGINT | NOT NULL DEFAULT 0 |

CHECK : `reserved_balance <= balance`.
**Solde disponible = `balance - reserved_balance`.**

> Vocabulaire `Available`/`Reserved`/`Consumed`/`Released` précisé en Partie I, §I — **aucun changement de schéma**.

### C.9 `treasury_transactions` — ledger append-only

| Colonne | Type | Contraintes |
|---|---|---|
| `id` | UUID | PK |
| `account_id` | UUID | NOT NULL, FK → `treasury_accounts(id)` |
| `type` | VARCHAR(16) | NOT NULL, CHECK ∈ *TreasuryTransactionType* |
| `amount` | NUMERIC(21,2) | NOT NULL, CHECK `> 0` (le signe est porté par `type`) |
| `balance_after` | NUMERIC(21,2) | NOT NULL |
| `reserved_after` | NUMERIC(21,2) | NOT NULL |
| `order_id` | UUID | NULL, FK → `orders(id)` — corrélation |
| `performed_by` | UUID | NULL, FK → `users(id)` — NULL = système |
| `reason` | VARCHAR(500) | NULL |
| `created_at` | TIMESTAMPTZ | NOT NULL |

Index :

- `idx_treasury_tx_account_created (account_id, created_at DESC)`.
- `idx_treasury_tx_order (order_id)`.
- `uq_treasury_tx_order_type (order_id, type) WHERE type IN ('RESERVATION','RELEASE','WITHDRAWAL')`
  → **garantit au niveau SQL qu'un ordre ne peut jamais être réservé deux fois, libéré deux fois, ni décaissé deux fois.** Filet de sécurité ultime contre le double décaissement.

Aucun `UPDATE`/`DELETE` sur cette table : une correction se fait par écriture `ADJUSTMENT` compensatoire.

### C.10 `order_status_history`

| Colonne | Type | Contraintes |
|---|---|---|
| `id` | UUID | PK |
| `order_id` | UUID | NOT NULL, FK → `orders(id)` ON DELETE CASCADE |
| `from_status` | VARCHAR(24) | NULL (NULL = création) |
| `to_status` | VARCHAR(24) | NOT NULL |
| `changed_by` | UUID | NULL, FK → `users(id)` — NULL = système (expiration) |
| `reason` | VARCHAR(500) | NULL |
| `created_at` | TIMESTAMPTZ | NOT NULL |

Index : `idx_osh_order_created (order_id, created_at)`.

### C.11 `system_settings`

| Colonne | Type | Contraintes |
|---|---|---|
| `key` | VARCHAR(64) | **PK** |
| `value` | VARCHAR(255) | NOT NULL |
| `value_type` | VARCHAR(16) | NOT NULL, CHECK ∈ (`STRING`,`INTEGER`,`DECIMAL`,`BOOLEAN`) |
| `description` | VARCHAR(255) | NULL |
| `is_public` | BOOLEAN | NOT NULL DEFAULT FALSE — exposable au frontend client |
| `updated_by` | UUID | NULL, FK → `users(id)` |
| `updated_at` | TIMESTAMPTZ | NOT NULL |

Clés amorcées (migration V4) :

| Clé | Type | Valeur initiale | Public |
|---|---|---|---|
| `MIN_ORDER_AMOUNT_CFA` | DECIMAL | `10000` | ✅ |
| `MAX_ORDER_AMOUNT_CFA` | DECIMAL | `2000000` | ✅ |
| `RATE_LOCK_DURATION_MINUTES` | INTEGER | `30` | ✅ |
| `ORDER_AUTO_EXPIRE_ENABLED` | BOOLEAN | `true` | ❌ |
| `REQUIRE_PAYMENT_PROOF` | BOOLEAN | `true` | ✅ |
| `TREASURY_RESERVE_ON_ORDER` | BOOLEAN | `true` | ❌ |
| `MAX_PROOF_FILE_SIZE_BYTES` | INTEGER | `5242880` (5 Mo) | ✅ |
| `MAX_PROOFS_PER_PAYMENT` | INTEGER | `3` | ✅ |
| `ENABLED_PAYMENT_METHODS` | STRING | `MOBILE_MONEY` | ✅ |
| `MAX_OPEN_ORDERS_PER_USER` | INTEGER | `3` | ❌ |

### C.12 `audit_logs`

| Colonne | Type | Contraintes |
|---|---|---|
| `id` | UUID | PK |
| `actor_id` | UUID | NULL, FK → `users(id)` |
| `actor_phone` | VARCHAR(20) | NULL — dénormalisé (survit à toute modification du compte) |
| `action` | VARCHAR(48) | NOT NULL — *AuditAction* |
| `entity_type` | VARCHAR(48) | NULL |
| `entity_id` | VARCHAR(64) | NULL |
| `metadata` | JSONB | NULL — avant/après, montants, raisons |
| `ip_address` | VARCHAR(45) | NULL (IPv6-safe) |
| `user_agent` | VARCHAR(255) | NULL |
| `created_at` | TIMESTAMPTZ | NOT NULL |

Index : `idx_audit_created (created_at DESC)`, `idx_audit_entity (entity_type, entity_id)`, `idx_audit_actor (actor_id, created_at DESC)`.

Écriture en `REQUIRES_NEW` : **un échec d'audit ne doit jamais faire échouer l'opération métier, et un rollback métier ne doit pas effacer la trace de la tentative.**

### C.13 `idempotency_keys`

| Colonne | Type | Contraintes |
|---|---|---|
| `id` | UUID | PK |
| `idem_key` | VARCHAR(80) | NOT NULL |
| `user_id` | UUID | NOT NULL, FK → `users(id)` |
| `endpoint` | VARCHAR(120) | NOT NULL |
| `request_hash` | CHAR(64) | NOT NULL — SHA-256 du body |
| `response_status` | INTEGER | NULL |
| `response_body` | JSONB | NULL |
| `created_at` / `expires_at` | TIMESTAMPTZ | NOT NULL |

Index : `uq_idem (user_id, endpoint, idem_key)`, `idx_idem_expiry (expires_at)`.

### C.14 Vue relationnelle `[SUPERSEDED → voir Partie I, §K.15]`

```
users 1──n orders 1──1 beneficiaries
  │            │
  │            1──n payments 1──n payment_proofs
  │            │
  │            1──n order_status_history
  │            │
  │            0──n treasury_transactions ──n──1 treasury_accounts
  │
  n──n roles (user_roles)
  │
  1──n exchange_rates (created_by)
  1──n audit_logs (actor_id)
  1──n idempotency_keys

exchange_rates 1──n orders (exchange_rate_id — traçabilité du snapshot)
```

---

## D. Liste complète des énumérations

Toutes persistées en **`VARCHAR` + `CHECK`** (`@Enumerated(EnumType.STRING)`) — jamais en `ORDINAL` : insérer une valeur au milieu d'un enum ordinal corromprait silencieusement toutes les lignes existantes.

| Enum | Valeurs | Localisation |
|---|---|---|
| `RoleCode` | `USER`, `ADMIN` | `user/domain` |
| `UserStatus` | `ACTIVE`, `BLOCKED` | `user/domain` |
| `Currency` | `XOF`, `CNY` | `common/money` |
| `BeneficiaryType` | `ALIPAY`, `WECHAT_PAY`, `CHINESE_BANK_ACCOUNT` | `order/domain` |
| `OrderStatus` | `AWAITING_PAYMENT`, `PAYMENT_SUBMITTED`, `PAYMENT_VERIFIED`, `PROCESSING`, `COMPLETED`, `CANCELLED`, `REJECTED`, `EXPIRED` | `order/domain` |
| `PaymentMethod` | `MOBILE_MONEY`, `WAVE`, `BANK_TRANSFER` | `payment/domain` |
| `PaymentStatus` | `SUBMITTED`, `VERIFIED`, `REJECTED` | `payment/domain` |
| `StorageProvider` | `LOCAL`, `S3` | `storage` |
| `TreasuryTransactionType` | `DEPOSIT`, `WITHDRAWAL`, `RESERVATION`, `RELEASE`, `ADJUSTMENT` | `treasury/domain` |
| `SettingType` | `STRING`, `INTEGER`, `DECIMAL`, `BOOLEAN` | `settings/domain` |
| `SettingKey` | cf. tableau C.11 (enum typé, pas de chaîne magique) | `settings/domain` |
| `AuditAction` | cf. ci-dessous | `audit/domain` |
| `ErrorCode` | cf. section F.5 | `common/exception` |

**`AuditAction`** :
`USER_REGISTERED`, `USER_LOGIN_SUCCESS`, `USER_LOGIN_FAILED`, `USER_BLOCKED`, `USER_UNBLOCKED`,
`EXCHANGE_RATE_CREATED`,
`ORDER_CREATED`, `ORDER_CANCELLED`, `ORDER_EXPIRED`, `ORDER_PROCESSING_STARTED`, `ORDER_COMPLETED`,
`PAYMENT_SUBMITTED`, `PAYMENT_PROOF_UPLOADED`, `PAYMENT_VERIFIED`, `PAYMENT_REJECTED`,
`TREASURY_DEPOSIT`, `TREASURY_WITHDRAWAL`, `TREASURY_ADJUSTMENT`, `TREASURY_RESERVED`, `TREASURY_RELEASED`,
`SETTING_UPDATED`.

> **`[Étendu par Partie I, §K.13]`** : de nouvelles valeurs (`QUOTE_CREATED`, `QUOTE_EXPIRED`, `SETTLEMENT_EXECUTED`, `RISK_FLAG_RAISED`, `RISK_FLAG_REVIEWED`, …) s'ajouteront au fil des phases, sans changement de schéma.

**Sémantique des effets trésorerie**, portée par `TreasuryTransactionType` (table de vérité unique) :

| Type | `balance` | `reserved_balance` | Déclencheur |
|---|---|---|---|
| `DEPOSIT` | `+ amount` | — | Alimentation manuelle admin, ou encaissement CFA vérifié |
| `WITHDRAWAL` | `- amount` | `- amount` si réservé | Décaissement CNY exécuté (`COMPLETED`) — *= « Consumed » quand `order_id` est renseigné, voir Partie I §I.2* |
| `RESERVATION` | — | `+ amount` | Création d'ordre (immobilise la liquidité CNY) |
| `RELEASE` | — | `- amount` | Annulation / expiration / rejet |
| `ADJUSTMENT` | `± amount` | — | Correction comptable admin (motif obligatoire) |

---

## E. Machine d'état `Order`

### E.1 Graphe des transitions

```
                        ┌──────────────────┐
          création ────►│ AWAITING_PAYMENT │
                        └───┬────┬─────┬───┘
      client déclare        │    │     │
      son paiement          │    │     │  timeout > rate_expires_at (job système)
                            │    │     └──────────────────────────────► EXPIRED ◄─┐
                            │    │                                                 │
                            │    │  annulation client                              │ RELEASE
                            │    └────────────────────────────────────► CANCELLED  │ trésorerie
                            ▼                                              ▲       │
                  ┌────────────────────┐                                   │       │
                  │  PAYMENT_SUBMITTED │───── rejet admin (motif) ─────► REJECTED ─┘
                  └─────────┬──────────┘
                            │  vérification admin
                            │  → DEPOSIT CFA
                            ▼
                  ┌────────────────────┐
                  │  PAYMENT_VERIFIED  │
                  └─────────┬──────────┘
                            │  admin : start-processing
                            ▼
                  ┌────────────────────┐
                  │     PROCESSING     │
                  └─────────┬──────────┘
                            │  admin : complete (payoutReference)
                            │  → WITHDRAWAL CNY
                            ▼
                  ┌────────────────────┐
                  │     COMPLETED      │   (terminal)
                  └────────────────────┘
```

> **`[Précisé par Partie I, §N.2]`** : la transition finale `PROCESSING → COMPLETED` est désormais déclenchée par un `Settlement.EXECUTED` plutôt que par l'écriture directe de `payoutReference` sur `Order`. Le graphe des 8 statuts et toutes les autres transitions restent inchangés.

### E.2 Table des transitions autorisées

| Depuis | Vers | Acteur | Effets latéraux |
|---|---|---|---|
| *(création)* | `AWAITING_PAYMENT` | CLIENT | Snapshot du taux, `RESERVATION` CNY, historique |
| `AWAITING_PAYMENT` | `PAYMENT_SUBMITTED` | CLIENT | Création du `Payment` |
| `AWAITING_PAYMENT` | `CANCELLED` | CLIENT / ADMIN | `RELEASE` CNY |
| `AWAITING_PAYMENT` | `EXPIRED` | **SYSTÈME** | `RELEASE` CNY |
| `PAYMENT_SUBMITTED` | `PAYMENT_VERIFIED` | ADMIN | `DEPOSIT` CFA, `Payment → VERIFIED` |
| `PAYMENT_SUBMITTED` | `REJECTED` | ADMIN | `RELEASE` CNY, `Payment → REJECTED` + motif |
| `PAYMENT_VERIFIED` | `PROCESSING` | ADMIN | — |
| `PROCESSING` | `COMPLETED` | ADMIN | `WITHDRAWAL` CNY, `payoutReference`, `completedAt` |

États terminaux : `COMPLETED`, `CANCELLED`, `REJECTED`, `EXPIRED`. Aucune sortie.

### E.3 Règles d'implémentation

1. **Aucun endpoint n'accepte un statut en entrée.** Le statut est *dérivé* de l'action métier invoquée. Le frontend appelle `POST /orders/{id}/cancel`, jamais `PATCH {status:"CANCELLED"}`.
2. La table de transitions vit dans un composant unique `OrderStateMachine` (`EnumMap<OrderStatus, Set<OrderStatus>>`). Toute transition passe par `transition(order, target, actor, reason)`, qui : vérifie la légalité → lève `InvalidOrderStateException` sinon → écrit `order_status_history` → publie l'audit.
3. **Chargement en verrou pessimiste** (`SELECT ... FOR UPDATE`) avant toute transition sensible : deux administrateurs cliquant simultanément « vérifier » sont sérialisés, le second constate le statut déjà changé et reçoit un `409 INVALID_ORDER_STATE`.
4. `PAYMENT_SUBMITTED` n'est atteignable **qu'avant** `rate_expires_at`. Passé ce délai, la déclaration de paiement est refusée (`ExchangeRateExpiredException`) — un ordre expiré ne peut pas être « rattrapé ».
5. Le job d'expiration ne touche **jamais** un ordre déjà sorti de `AWAITING_PAYMENT` : sa requête filtre sur le statut *et* la date, avec `FOR UPDATE SKIP LOCKED`.

### E.4 Cycle de vie du `Payment` (imbriqué)

```
(déclaration client) ──► SUBMITTED ──┬── verify ──► VERIFIED  (terminal)
                                     └── reject ──► REJECTED  (terminal)
```

Un `Payment` `REJECTED` **libère** l'index unique `uq_payments_active_per_order` : le client peut resoumettre un nouveau paiement — mais uniquement si l'ordre est repassé dans un état l'autorisant (décision de gestion, cf. point de validation §K).

---

## F. Endpoints REST `[SUPERSEDED → voir Partie I, §O pour les endpoints taux/ordre/paiement/règlement]`

Préfixe global `/api`. Toutes les réponses de succès sont enveloppées dans `{ "data": …, "message": … }`.

### F.1 Public — `/api/auth/**`, `permitAll`

| Méthode | Chemin | Description |
|---|---|---|
| `POST` | `/auth/register` | Inscription (`phone`, `password`, `firstName`, `lastName`) → 201 |
| `POST` | `/auth/login` | Authentification → JWT + profil |
| `GET` | `/auth/me` | Profil courant *(authentifié)* |
| `GET` | `/settings/public` | Bornes min/max, durée de verrouillage, méthodes de paiement actives |
| `GET` | `/exchange-rate/current` | Taux courant + frais applicables |

> Section toujours **en vigueur telle quelle** : ces endpoints `auth`/`settings` sont livrés en Phase 2 et inchangés par la révision. Seul `GET /exchange-rate/current` est renommé/précisé en Partie I, §O.

### F.2 Client authentifié — rôle `USER`

| Méthode | Chemin | Description |
|---|---|---|
| `POST` | `/exchange/simulate` | Simulation `amountCfa` → `amountCny`, frais, taux, expiration |
| `POST` | `/orders` | Création d'un ordre **(`Idempotency-Key` requis)** → verrouille le taux 30 min |
| `GET` | `/orders` | Historique paginé, filtres `status`, `from`, `to` |
| `GET` | `/orders/{id}` | Détail (ordre + bénéficiaire + paiement + historique de statuts) |
| `POST` | `/orders/{id}/cancel` | Annulation (uniquement depuis `AWAITING_PAYMENT`) |
| `POST` | `/orders/{id}/payments` | Déclaration du paiement CFA (`method`, `transactionReference`) → `PAYMENT_SUBMITTED` |
| `POST` | `/orders/{id}/payment-proof` | Upload d'une preuve (`multipart/form-data`) |
| `GET` | `/orders/{id}/proofs/{proofId}` | Téléchargement de la preuve (propriétaire ou ADMIN) |
| `GET` | `/dashboard/summary` | Agrégats client : ordres par statut, volumes, dernier ordre actif |

### F.3 Administration — `/api/admin/**`, rôle `ADMIN` exclusivement

| Méthode | Chemin | Description |
|---|---|---|
| `GET` | `/admin/dashboard` | KPI globaux + alertes de trésorerie |
| `GET` | `/admin/users` | Liste paginée + recherche (nom, téléphone, statut) |
| `GET` | `/admin/users/{id}` | Détail + statistiques (nb d'ordres, volume total) |
| `POST` | `/admin/users/{id}/block` | Blocage (motif obligatoire) |
| `POST` | `/admin/users/{id}/unblock` | Déblocage |
| `GET` | `/admin/orders` | Liste paginée, filtres `status`, `userId`, période |
| `GET` | `/admin/orders/{id}` | Détail complet (vue admin) |
| `POST` | `/admin/orders/{id}/start-processing` | `PAYMENT_VERIFIED → PROCESSING` |
| `POST` | `/admin/orders/{id}/complete` | `PROCESSING → COMPLETED` (`payoutReference`, `adminNote`) **(`Idempotency-Key`)** |
| `POST` | `/admin/orders/{id}/cancel` | Annulation administrative (motif obligatoire) |
| `GET` | `/admin/payments/pending` | File d'attente de vérification |
| `GET` | `/admin/payments/{id}` | Détail + preuves attachées |
| `POST` | `/admin/payments/{id}/verify` | Validation **(`Idempotency-Key`)** |
| `POST` | `/admin/payments/{id}/reject` | Rejet (motif obligatoire) |
| `GET` | `/admin/exchange-rates` | Historique paginé des taux |
| `POST` | `/admin/exchange-rates` | Publication d'un nouveau taux |
| `GET` | `/admin/treasury/accounts` | Soldes XOF / CNY (total, réservé, disponible) |
| `GET` | `/admin/treasury/transactions` | Ledger paginé, filtres devise / type / période |
| `POST` | `/admin/treasury/transactions` | `DEPOSIT` / `WITHDRAWAL` / `ADJUSTMENT` manuel |
| `GET` | `/admin/settings` | Liste des paramètres métier |
| `PUT` | `/admin/settings/{key}` | Modification d'un paramètre (auditée) |
| `GET` | `/admin/audit-logs` | Journal paginé, filtres acteur / action / entité / période |

> `GET`/`POST /admin/users/**`, `/admin/settings/**`, `/admin/audit-logs` : **livrés en Phase 2, inchangés.** Les autres lignes de ce tableau (`orders`, `payments`, `exchange-rates`, `treasury`) sont des endpoints **prévus, non implémentés** ; ils sont remplacés/précisés par Partie I, §O.

### F.4 Formats de réponse

Succès :

```json
{ "data": { }, "message": "Operation successful" }
```

Erreur :

```json
{
  "timestamp": "2026-08-20T12:30:00Z",
  "status": 400,
  "error": "BUSINESS_ERROR",
  "code": "ORDER_AMOUNT_BELOW_MINIMUM",
  "message": "Le montant minimum est de 10 000 CFA",
  "path": "/api/orders",
  "traceId": "3f1c…",
  "violations": [ { "field": "amountCfa", "message": "must be >= 10000" } ]
}
```

`code` est un identifiant **stable et machine-lisible** ; `message` est destiné à l'humain et peut être traduit. Le frontend ne parse jamais `message`.

Ce format (`ApiResponse<T>`/`ErrorResponse`) est **livré et testé en Phase 2**, inchangé par la révision.

### F.5 Exceptions métier → HTTP

| Exception | HTTP | `code` |
|---|---|---|
| `OrderNotFoundException` | 404 | `ORDER_NOT_FOUND` |
| `InvalidOrderStateException` | 409 | `INVALID_ORDER_STATE` |
| `ExchangeRateExpiredException` | 409 | `EXCHANGE_RATE_EXPIRED` |
| `ExchangeRateNotConfiguredException` | 503 | `EXCHANGE_RATE_UNAVAILABLE` |
| `InsufficientTreasuryException` | 409 | `INSUFFICIENT_TREASURY` |
| `UserBlockedException` | 403 | `USER_BLOCKED` |
| `InvalidPaymentProofException` | 400 | `INVALID_PAYMENT_PROOF` |
| `OrderAmountOutOfRangeException` | 400 | `ORDER_AMOUNT_OUT_OF_RANGE` |
| `DuplicateTransactionReferenceException` | 409 | `DUPLICATE_TRANSACTION_REFERENCE` |
| `PaymentAlreadyReviewedException` | 409 | `PAYMENT_ALREADY_REVIEWED` |
| `AccessDeniedException` (ownership) | **404** | `ORDER_NOT_FOUND` |
| `MethodArgumentNotValidException` | 400 | `VALIDATION_ERROR` |
| *non gérée* | 500 | `INTERNAL_ERROR` (message générique, détail seulement dans les logs) |

> **Point de sécurité** : accéder à l'ordre d'autrui renvoie `404`, jamais `403`. Un `403` confirmerait l'existence de la ressource et permettrait d'énumérer les ordres. `UserBlockedException`/`USER_BLOCKED` et le principe 404-jamais-403 sont **livrés et testés en Phase 2** (`AdminEndpointSecurityIT`, `BlockedUserIT`).

Le catalogue `ErrorCode` réellement livré en Phase 2 (`backend/.../common/exception/ErrorCode.java`) contient déjà, en réserve, `RATE_CHANGED`, `INVALID_ORDER_STATE`, `EXCHANGE_RATE_EXPIRED`, `INSUFFICIENT_TREASURY`, etc. : ce tableau de la Phase 1 a anticipé une partie des besoins de la révision actuelle sans qu'aucune modification ne soit nécessaire.

---

## G. Arborescence backend

> État à la fin de la Phase 2 (livré) : seuls `common`, `config`, `security`, `auth`, `user`, `settings`, `audit`, `admin` (partiel) existent réellement. `exchange`, `order`, `payment`, `storage`, `treasury` listés ci-dessous restent **prévisionnels** (Phase 1) et seront ajustés selon Partie I (renommage `exchange` → `rate`/`quote`, ajout de `settlement` et `risk`) au moment de leur construction.

```
backend/
├── pom.xml
├── mvnw / mvnw.cmd / .mvn/wrapper/
├── Dockerfile
└── src/
    ├── main/
    │   ├── java/com/converter/
    │   │   ├── ConverterApplication.java
    │   │   │
    │   │   ├── config/
    │   │   │   ├── OpenApiConfig.java
    │   │   │   ├── JacksonConfig.java          # Instant ISO-8601 UTC, BigDecimal en string
    │   │   │   ├── PersistenceConfig.java      # JPA auditing, TZ UTC
    │   │   │   ├── AsyncSchedulingConfig.java
    │   │   │   ├── WebMvcConfig.java
    │   │   │   └── props/
    │   │   │       ├── JwtProperties.java
    │   │   │       ├── StorageProperties.java
    │   │   │       └── CorsProperties.java
    │   │   │
    │   │   ├── common/
    │   │   │   ├── api/  ApiResponse, ErrorResponse, PageResponse, ApiPageable
    │   │   │   ├── domain/  BaseEntity, BaseAuditEntity
    │   │   │   ├── exception/
    │   │   │   │   ├── BusinessException.java, ErrorCode.java
    │   │   │   │   ├── ResourceNotFoundException.java
    │   │   │   │   └── GlobalExceptionHandler.java
    │   │   │   ├── money/  Money.java, Currency.java, MoneyCalculator.java, MoneyRounding.java
    │   │   │   ├── idempotency/  IdempotencyKey, Repository, IdempotencyService, @Idempotent, Aspect
    │   │   │   ├── validation/  @PhoneNumber, PhoneNumberValidator, @PositiveAmount
    │   │   │   └── util/  RequestContext.java (IP, user-agent), Sanitizer.java
    │   │   │
    │   │   ├── security/
    │   │   │   ├── SecurityConfig.java
    │   │   │   ├── jwt/  JwtService, JwtAuthenticationFilter, JwtAuthenticationEntryPoint,
    │   │   │   │         JwtAccessDeniedHandler
    │   │   │   ├── CurrentUser.java, @AuthenticatedUser, CurrentUserArgumentResolver
    │   │   │   ├── OwnershipService.java
    │   │   │   └── RateLimitFilter.java         # anti brute-force sur /auth/login
    │   │   │
    │   │   ├── auth/
    │   │   │   ├── AuthController.java
    │   │   │   ├── AuthService.java
    │   │   │   └── dto/  RegisterRequest, LoginRequest, AuthResponse, CurrentUserResponse
    │   │   │
    │   │   ├── user/
    │   │   │   ├── domain/  User, Role, UserStatus, RoleCode
    │   │   │   ├── repository/  UserRepository, RoleRepository
    │   │   │   ├── service/  UserService, UserQueryService
    │   │   │   ├── mapper/  UserMapper
    │   │   │   └── dto/  UserResponse, UserSummaryResponse, UserDetailResponse, BlockUserRequest
    │   │   │
    │   │   ├── settings/
    │   │   │   ├── domain/  SystemSetting, SettingKey, SettingType
    │   │   │   ├── repository/  SystemSettingRepository
    │   │   │   ├── service/  SettingsService.java          # cache + typage fort
    │   │   │   └── dto/  SettingResponse, UpdateSettingRequest, PublicSettingsResponse
    │   │   │
    │   │   ├── rate/                              # anciennement "exchange" — voir Partie I, §G
    │   │   │   ├── domain/  RateSource, RateProviderType
    │   │   │   ├── provider/  RateProvider (interface), ManualRateProvider
    │   │   │   ├── engine/  RateEngine
    │   │   │   ├── repository/  RateSourceRepository
    │   │   │   ├── web/  RateController (admin + public)
    │   │   │   └── dto/  CurrentRateResponse, PublishRateSourceRequest
    │   │   │
    │   │   ├── quote/                              # nouveau — voir Partie I, §G.5
    │   │   │   ├── domain/  Quote, QuoteStatus
    │   │   │   ├── repository/  QuoteRepository
    │   │   │   ├── service/  QuoteService, QuoteExpirationScheduler
    │   │   │   ├── web/  QuoteController
    │   │   │   └── dto/  SimulationRequest, SimulationResponse, QuoteResponse
    │   │   │
    │   │   ├── order/
    │   │   │   ├── domain/  Order, Beneficiary, OrderStatus, BeneficiaryType, OrderStatusHistory
    │   │   │   ├── repository/  OrderRepository, OrderStatusHistoryRepository
    │   │   │   ├── service/  OrderService, OrderQueryService, OrderStateMachine,
    │   │   │   │             OrderExpirationScheduler, OrderReferenceGenerator
    │   │   │   ├── mapper/  OrderMapper, BeneficiaryMapper
    │   │   │   ├── web/  OrderController
    │   │   │   └── dto/  CreateOrderRequest, BeneficiaryRequest, OrderResponse,
    │   │   │             OrderDetailResponse, OrderSummaryResponse, CancelOrderRequest
    │   │   │
    │   │   ├── payment/
    │   │   │   ├── domain/  Payment, PaymentProof, PaymentMethod, PaymentStatus
    │   │   │   ├── repository/  PaymentRepository, PaymentProofRepository
    │   │   │   ├── service/  PaymentService, PaymentProofService, PaymentReviewService
    │   │   │   ├── mapper/  PaymentMapper
    │   │   │   ├── web/  PaymentController
    │   │   │   └── dto/  SubmitPaymentRequest, PaymentResponse, PaymentProofResponse,
    │   │   │             VerifyPaymentRequest, RejectPaymentRequest, PendingPaymentResponse
    │   │   │
    │   │   ├── settlement/                          # nouveau — voir Partie I, §H.3
    │   │   │   ├── domain/  Settlement, SettlementStatus
    │   │   │   ├── repository/  SettlementRepository
    │   │   │   ├── service/  SettlementService
    │   │   │   ├── web/  AdminSettlementController
    │   │   │   └── dto/  ExecuteSettlementRequest, SettlementResponse
    │   │   │
    │   │   ├── storage/
    │   │   │   ├── FileStorageService.java      # interface (port)
    │   │   │   ├── LocalFileStorageService.java # @ConditionalOnProperty storage.provider=local
    │   │   │   ├── StoredFile.java, FileValidator.java, FileNameSanitizer.java
    │   │   │   └── exception/  StorageException, InvalidFileException
    │   │   │
    │   │   ├── treasury/
    │   │   │   ├── domain/  TreasuryAccount, TreasuryTransaction, TreasuryTransactionType
    │   │   │   ├── repository/  TreasuryAccountRepository, TreasuryTransactionRepository
    │   │   │   ├── service/  TreasuryService, TreasuryQueryService
    │   │   │   ├── mapper/  TreasuryMapper
    │   │   │   ├── web/  TreasuryController
    │   │   │   └── dto/  TreasuryAccountResponse, TreasuryTransactionResponse,
    │   │   │             CreateTreasuryTransactionRequest, TreasurySnapshotResponse
    │   │   │
    │   │   ├── risk/                                # nouveau — voir Partie I, §J
    │   │   │   ├── domain/  RiskFlag, RiskFlagType, RiskFlagStatus
    │   │   │   ├── repository/  RiskFlagRepository
    │   │   │   ├── service/  RiskFlagService
    │   │   │   ├── web/  AdminRiskFlagController
    │   │   │   └── dto/  RiskFlagResponse, ReviewRiskFlagRequest
    │   │   │
    │   │   ├── audit/
    │   │   │   ├── domain/  AuditLog, AuditAction
    │   │   │   ├── repository/  AuditLogRepository
    │   │   │   ├── service/  AuditService.java
    │   │   │   ├── web/  AuditLogController
    │   │   │   └── dto/  AuditLogResponse
    │   │   │
    │   │   ├── dashboard/
    │   │   │   ├── ClientDashboardController.java, ClientDashboardService.java
    │   │   │   └── dto/  ClientDashboardResponse
    │   │   │
    │   │   └── admin/
    │   │       ├── AdminDashboardController.java, AdminDashboardService.java  # KPI §R
    │   │       ├── AdminUserController.java
    │   │       ├── AdminOrderController.java, AdminOrderService.java
    │   │       ├── AdminPaymentController.java
    │   │       ├── AdminSettingsController.java
    │   │       └── dto/  AdminDashboardResponse, TreasuryAlertResponse
    │   │
    │   └── resources/
    │       ├── application.yml
    │       ├── application-dev.yml
    │       ├── application-prod.yml
    │       └── db/migration/
    │           ├── V1__initial_schema.sql              # livré
    │           ├── V2__indexes_and_constraints.sql      # livré
    │           ├── V3__seed_roles.sql                   # livré
    │           ├── V4__seed_settings.sql                # livré
    │           ├── V5__seed_treasury_accounts.sql       # livré
    │           ├── V6__order_reference_sequence.sql     # livré
    │           └── V7+__...                             # Phase 3+ : rate_sources, quotes,
    │                                                     # orders (quote_id), settlements, risk_flags
    │
    └── test/
        ├── java/com/converter/
        │   ├── unit/       MoneyCalculatorTest, RateEngineTest, QuoteServiceTest, OrderServiceTest,
        │   │               OrderStateMachineTest, PaymentServiceTest, TreasuryServiceTest,
        │   │               SettlementServiceTest, FileValidatorTest, JwtServiceTest
        │   ├── integration/ AbstractIntegrationTest (Testcontainers), AuthFlowIT, QuoteFlowIT,
        │   │               OrderFlowIT, PaymentFlowIT, SettlementFlowIT, TreasuryConcurrencyIT,
        │   │               RateLockIT, FlywayMigrationIT
        │   ├── security/   OwnershipSecurityIT, AdminEndpointSecurityIT, BlockedUserIT
        │   └── fixtures/   TestDataFactory
        └── resources/  application-test.yml
```

---

## H. Arborescence frontend

```
frontend/
├── package.json, angular.json, tsconfig.json
├── Dockerfile, nginx.conf
└── src/
    ├── main.ts, index.html, styles.scss
    ├── environments/  environment.ts, environment.prod.ts
    └── app/
        ├── app.config.ts, app.routes.ts, app.component.ts
        │
        ├── core/
        │   ├── auth/       auth.service.ts, token.storage.ts, auth.models.ts
        │   ├── guards/     auth.guard.ts, admin.guard.ts, guest.guard.ts
        │   ├── interceptors/ auth.interceptor.ts, error.interceptor.ts,
        │   │                 loading.interceptor.ts, idempotency.interceptor.ts
        │   ├── services/   api.service.ts, quote.service.ts, order.service.ts,
        │   │               rate.service.ts, payment.service.ts, settlement.service.ts,
        │   │               treasury.service.ts, admin.service.ts,
        │   │               settings.service.ts, notification.service.ts
        │   └── models/     order.model.ts, quote.model.ts, payment.model.ts, user.model.ts,
        │                   rate.model.ts, api-response.model.ts, enums.ts
        │
        ├── shared/
        │   ├── components/  page-header, empty-state, confirm-dialog,
        │   │                order-status-chip, rate-lock-timer, money-display,
        │   │                stat-card, file-upload, loading-spinner
        │   ├── pipes/       money.pipe.ts, order-status-label.pipe.ts, time-left.pipe.ts
        │   ├── directives/  has-role.directive.ts
        │   └── validators/  phone.validator.ts, amount-range.validator.ts
        │
        ├── layouts/  client-layout/, admin-layout/, auth-layout/
        │
        └── features/
            ├── auth/       login.page.ts, register.page.ts
            ├── dashboard/  client-dashboard.page.ts
            ├── quote/      quote.page.ts, rate-card.component.ts, simulator.component.ts
            ├── orders/     order-list.page.ts, order-create.page.ts, order-detail.page.ts,
            │               components/ beneficiary-form, order-summary, order-timeline
            ├── payments/   payment-submit.page.ts, proof-upload.component.ts
            └── admin/
                ├── admin-dashboard.page.ts
                ├── admin-orders.page.ts, admin-order-detail.page.ts
                ├── admin-payments.page.ts, payment-review-dialog.component.ts
                ├── admin-settlements.page.ts
                ├── admin-users.page.ts, admin-user-detail.page.ts
                ├── admin-rate-sources.page.ts
                ├── admin-treasury.page.ts
                ├── admin-risk-flags.page.ts
                └── admin-settings.page.ts
```

**Routage** (`app.routes.ts`) : `/login`, `/register` (`guestGuard`) · `/dashboard`, `/quote`, `/orders`, `/orders/new`, `/orders/:id` (`authGuard`) · `/admin/**` (`authGuard` + `adminGuard`). Chargement différé (`loadComponent`) sur chaque route — l'espace admin n'est jamais téléchargé par un simple client.

---

## I. Flux métier principal `[SUPERSEDED → voir Partie I, §M]`

Conservé comme référence de la logique de calcul (arrondis, formule) — voir I.2 ci-dessous, toujours valable pour le calcul des frais. Le parcours complet (I.1) est remplacé par Partie I §M, qui insère l'étape `Quote`.

### I.2 Formule monétaire — principe conservé, décomposé en Partie I §G.4

```
feeCfa       = (amountCfa × feePercentage / 100) + fixedFeeCfa   → arrondi UP,   scale 0 (XOF)
netAmountCfa = amountCfa − feeCfa                                → scale 0
amountCny    = netAmountCfa / cfaPerCny                          → arrondi DOWN, scale 2 (CNY)
```

**Choix d'arrondi** : les frais sont arrondis **au supérieur**, le décaissement CNY **à l'inférieur**. La plateforme ne peut donc jamais devoir plus que ce qu'elle a encaissé — le résidu d'arrondi (< 0,01 CNY) reste toujours du côté de la trésorerie. C'est la convention prudente standard en change. **Ce principe est conservé sans changement** ; seul `cfaPerCny` devient `customerRate` (lui-même dérivé de `marketRate` et de la marge, Partie I §G.4).

Vérification sur l'exemple de la spécification : `100 000 / 85 = 1 176,470588…` → **`1 176,47 CNY`**. ✅

### I.3 Chemins alternatifs

| Événement | Effet |
|---|---|
| 30 min écoulées sans paiement | Job (toutes les 60 s) → `EXPIRED` + `RELEASE` CNY + audit `ORDER_EXPIRED` |
| Annulation client | `CANCELLED` + `RELEASE` CNY |
| Preuve non conforme | Admin rejette → `REJECTED` + motif + `RELEASE` CNY |
| Liquidité CNY insuffisante | `409 INSUFFICIENT_TREASURY` — **l'ordre n'est pas créé** |
| Modification du taux à 10 h 10 | Sans effet sur l'ordre créé à 10 h 00 : le snapshot fait foi |
| Utilisateur bloqué | Rejet `403 USER_BLOCKED` dès le filtre JWT, sur toutes ses requêtes |

---

## J. Risques techniques et sécurité

> Liste complète toujours en vigueur. Complétée, pas remplacée, par Partie I §S (risques 28 à 34).

### J.1 Risques financiers

| # | Risque | Gravité | Parade |
|---|---|---|---|
| 1 | **Double décaissement CNY** (double-clic, double admin, retry réseau) | 🔴 Critique | Verrou pessimiste sur l'ordre + machine d'état + `Idempotency-Key` + **index unique `(order_id, WITHDRAWAL)`** en base |
| 2 | **Double validation d'un paiement** | 🔴 Critique | `SELECT FOR UPDATE` sur `payments` + vérification `status = SUBMITTED` dans la même transaction |
| 3 | **Erreur d'arrondi** cumulée | 🔴 Critique | `BigDecimal` exclusivement, `MoneyCalculator` unique, arrondis explicites, tests dédiés |
| 4 | **Recalcul d'un ordre historique** au taux courant | 🔴 Critique | Snapshot immuable + test d'invariance : modifier le taux ne doit modifier aucun ordre existant |
| 5 | **Survente de liquidité** (N ordres concurrents épuisent la réserve) | 🟠 Élevé | Réservation sous verrou pessimiste sur `treasury_accounts` + CHECK `reserved_balance <= balance` |
| 6 | Réutilisation d'une **référence Mobile Money** sur deux ordres | 🟠 Élevé | Index unique partiel `(method, transaction_reference)` |
| 7 | Divergence **solde matérialisé ↔ ledger** | 🟡 Moyen | `balance_after` inscrit dans chaque écriture + test de réconciliation |

### J.2 Risques de sécurité applicative

| # | Risque | Parade |
|---|---|---|
| 8 | **Accès horizontal** (lire l'ordre d'autrui) | Tout repository client filtre sur `userId` **dans la requête SQL**, jamais en post-filtrage Java. Réponse `404` (pas `403`). Test d'intégration dédié. |
| 9 | **Élévation de privilège** | `/api/admin/**` verrouillé au niveau `SecurityFilterChain` **et** par `@PreAuthorize("hasRole('ADMIN')")` — double barrière. Le rôle vient de la base, jamais d'un claim JWT modifiable seul. |
| 10 | **Upload malveillant** (webshell, polyglotte, zip-bomb) | Allowlist MIME stricte (`image/jpeg`, `image/png`, `image/webp`, `application/pdf`), **vérification des magic bytes** (pas seulement du header `Content-Type`), taille max 5 Mo, nom regénéré côté serveur (UUID), stockage **hors racine web**, servi uniquement via un endpoint authentifié avec `Content-Disposition: attachment` et `X-Content-Type-Options: nosniff`. |
| 11 | **Path traversal** via le nom de fichier | Le nom client n'est **jamais** utilisé comme chemin ; `storageKey` est généré (`proofs/2026/08/{uuid}.{ext}`). |
| 12 | **Brute-force** sur `/auth/login` | Rate limiting par IP (Caffeine en mémoire, MVP), réponse générique (« identifiants invalides ») pour ne pas révéler l'existence d'un compte. **Livré et testé en Phase 2.** |
| 13 | **Utilisateur bloqué conservant un JWT valide** | Le filtre JWT recharge le statut depuis la base à chaque requête. Blocage effectif **immédiatement**, sans attendre l'expiration du token. **Livré et testé en Phase 2** (`BlockedUserIT`). |
| 14 | **Fuite du hash de mot de passe** | Aucun DTO ne l'expose. **Livré en Phase 2.** |
| 15 | **Secrets en dur / dans Git** | Aucune valeur par défaut pour `JWT_SECRET`/`DB_PASSWORD` en profil `prod` : l'application **refuse de démarrer** si absent. `.env` dans `.gitignore`. **Livré et vérifié en Phase 2** (`docker compose up` avec configuration vierge). |
| 16 | **CORS trop permissif** | Origines explicitement listées par profil, jamais `*` avec `allowCredentials`. **Livré en Phase 2.** |
| 17 | **Injection SQL** | JPA/paramètres liés exclusivement ; aucune concaténation de chaîne SQL. |
| 18 | **Fuite d'information dans les erreurs** | Stack traces jamais renvoyées : message générique + `traceId`. **Livré en Phase 2.** |
| 19 | **Énumération d'identifiants** | UUID non séquentiels sur toutes les ressources publiques. |
| 20 | **Données personnelles du bénéficiaire** | Isolées dans une table dédiée, masquées partiellement dans les listes, journalisation des accès admin. |

### J.3 Risques techniques

| # | Risque | Parade |
|---|---|---|
| 21 | **Job d'expiration exécuté deux fois** (multi-instance) | `FOR UPDATE SKIP LOCKED` + revérification du statut dans la transaction. ShedLock si déploiement multi-instance confirmé. |
| 22 | **Décalage de fuseau horaire** | `TIMESTAMPTZ` + `Instant` + JVM et PostgreSQL en UTC. **Livré en Phase 2.** |
| 23 | **`ddl-auto` destructeur** | `spring.jpa.hibernate.ddl-auto=validate` en dev et prod. Flyway est seul maître du schéma. **Livré et testé en Phase 2** (`ApplicationStartupIT`). |
| 24 | **Divergence entités / migrations** | `validate` + test d'intégration Testcontainers. **Livré en Phase 2.** |
| 25 | **Migration Flyway modifiée après application** | Règle : jamais d'édition d'un `V*` déjà déployé ; validation de checksum activée. **Appliquée dans cette révision même** : `V1`–`V6` ne sont pas retouchées, une nouvelle série `V7+` portera les changements du modèle révisé. |
| 26 | **`OptimisticLockException` remontée brutalement** | Traduite en `409 CONCURRENT_MODIFICATION`. **Livré en Phase 2.** |
| 27 | **Perte des fichiers en stockage local** (conteneur éphémère) | Volume Docker persistant monté ; migration S3 prévue par l'interface `FileStorageService`. |

---

## K. Plan d'implémentation `[SUPERSEDED → voir Partie I, §T pour la roadmap révisée]`

| Phase | Contenu | Livrable vérifiable |
|---|---|---|
| **2 — Fondations** | Squelette Maven, Docker Compose (PostgreSQL), Flyway V1–V6, `common` (ApiResponse, exceptions, `GlobalExceptionHandler`, `Money`), sécurité JWT, `auth`, `user`, `settings`, seed admin, OpenAPI | ✅ `docker compose up` démarre ; `register` → `login` → `me` fonctionne ; Swagger accessible — **livré, 39/39 tests verts** |
| **3 — Exchange** | ~~`ExchangeRate`, publication + historisation~~ | Remplacé par « Phase 3 — Rate Engine + Quote », Partie I §T |
| **4 — Orders** | `Order`, `Beneficiary`, `OrderStateMachine`, verrouillage 30 min, historique de statuts, annulation, job d'expiration, idempotence | Ajusté : référence `quote_id`, voir Partie I §T |
| **5 — Payments** | `Payment`, `FileStorageService` + implémentation locale, validation des fichiers, upload de preuve, vérification / rejet admin | Inchangé dans le fond, voir Partie I §T |
| **6 — Treasury** | Comptes, ledger, réservation / libération, contrôles de liquidité, réconciliation | Vocabulaire clarifié uniquement, voir Partie I §I et §T |
| **7 — Angular client** | Projet Angular, auth, guards, interceptors, dashboard, simulateur, création d'ordre, minuteur 30 min, upload | Inchangé, étape de devis ajoutée |
| **8 — Angular admin** | Dashboard KPI, file de paiements, gestion des ordres, utilisateurs, taux, trésorerie, alertes | Enrichi des KPI business et de la revue de risque, voir Partie I §R |
| **9 — Tests** | Tests unitaires métier, intégration Testcontainers, tests de sécurité, tests de concurrence | Étendu aux nouveaux modules |
| **10 — Livraison** | Dockerfiles multi-stage, `docker-compose` complet avec health checks, README, guide de déploiement, variables d'environnement | Inchangé |

### Points nécessitant une validation explicite (Phase 1)

| # | Sujet | Recommandation | Alternative | Statut |
|---|---|---|---|---|
| **V1** | **Sens du taux** | `cfa_per_cny` : « 1 CNY = 85 CFA ». | `cny_per_cfa`. | ✅ Conservé — `customerRate`/`marketRate` gardent la même convention (Partie I, §G.4). |
| **V2** | **Simulation persistée ou non** | **Stateless**, table `quotes` jugée non requise. | Table `quotes` persistée. | ♻️ **Révisé** : la simulation reste stateless, mais la **confirmation** crée désormais un `Quote` persisté et immuable (Partie I, §G.5). L'étude de marché et l'exigence de traçabilité de la marge/des frais justifient ce changement par rapport à la décision initiale. |
| **V3** | **Réservation de trésorerie dès la création** | **Oui**, `TREASURY_RESERVE_ON_ORDER=true`. | Réserver à `PAYMENT_VERIFIED`. | ✅ Conservé (Partie I, §I). |
| **V4** | **Frais** | Portés par `exchange_rates`, historisés avec le taux. | Frais dans `system_settings`. | ♻️ **Révisé** : frais **et** marge sont désormais deux champs distincts, snapshotés dans `quotes` (Partie I, §K.3–K.4), pas dans `rate_sources`. |
| **V5** | **Refresh token** | **Hors MVP**, access token 2 h + revalidation en base. | Refresh rotatif. | ✅ Conservé, livré en Phase 2. |
| **V6** | **Resoumission après rejet** | `REJECTED` terminal pour l'ordre. | Retour à `AWAITING_PAYMENT`. | ✅ Conservé. |
| **V7** | **Version du JDK** | JDK 21 Temurin, Maven Wrapper. | JDK 26. | ✅ Conservé, livré en Phase 2. |
| **V8** | **Notifications** | Hors MVP. | SMS/e-mail. | ✅ Conservé. |

---

*Fin de la Partie II — Architecture Phase 1 (référence historique).*

---

*Fin du document — Partie I (Phase 2.5, en vigueur) suivie de la Partie II (Phase 1, archivée).*
