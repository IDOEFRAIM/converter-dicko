# FINANCIAL INVARIANTS — Converter (corridor XOF ↔ CNY)

> Invariants **définitifs** du cœur financier, tels que garantis par le code réel au 2026-09-02
> (après les passes de durcissement 1 et 2). Chaque invariant indique **où** il est appliqué :
> code applicatif (`APP`), contrainte SQL (`SQL`), verrou (`LOCK`), ou combinaison. La règle
> d'or : *aucun invariant important ne dépend d'un seul mécanisme implicite* — la défense est en
> profondeur.
>
> Documents liés : [ARCHITECTURE.md](ARCHITECTURE.md) · [BACKEND.md](BACKEND.md) ·
> [AUDIT_BUSINESS_LOGIC.md](AUDIT_BUSINESS_LOGIC.md) ·
> [AUDIT_BUSINESS_LOGIC_PASS2.md](AUDIT_BUSINESS_LOGIC_PASS2.md).

---

## 1. Représentation monétaire

**I-1.** Tout montant monétaire est un `BigDecimal` en mémoire et un `NUMERIC` en base. Aucun
`double`/`float`/`REAL` n'intervient à aucune étape d'un calcul financier.
*Où :* `APP` (types), `SQL` (`NUMERIC(19,2)` / `NUMERIC(21,2)` / `NUMERIC(18,6)`).

**I-2.** Les arrondis sont **centralisés** dans `rate/engine/MoneyRounding` et **déterministes** :
XOF engageant → arrondi **au supérieur**, échelle 0 ; CNY final → arrondi **à l'inférieur**,
échelle 2 ; taux → échelle 6, `HALF_UP` (une seule fois). Le résidu d'arrondi reste toujours du
côté de la trésorerie — la plateforme ne peut jamais devoir plus qu'elle n'a encaissé.
*Où :* `APP` (`MoneyRounding`, seul point d'arrondi de `RateEngine`), tests `MoneyRoundingTest`,
`RateEngineTest`.

---

## 2. Taux et pricing

**I-3.** Le **taux de marché**, la **marge** et le **taux client** sont trois grandeurs
distinctes, jamais fusionnées : `customerRate = normalizeRate(marketRate × (1 + margin/100))`.
Les **frais de service** (`feePercentage` + `fixedFeeXof`) sont une ligne de revenu **séparée**
de la marge, appliquée **une seule fois** sur le montant XOF brut, quel que soit le sens
(`SEND_XOF` / `RECEIVE_CNY` — recalcul intégral depuis le brut, pas de double frais).
*Où :* `APP` (`RateEngine`), `SQL` (colonnes distinctes dans `quotes`), tests.

**I-4.** Une cotation `rate_sources` avec `effective_to IS NULL` est **CURRENT** tant que son âge
(mesuré sur `effective_from`) ≤ `RATE_MAX_AGE_MINUTES`. Au-delà elle est **STALE** et traitée
comme **UNAVAILABLE** : `currentRate` lève `RATE_SOURCE_UNAVAILABLE` (+ log `WARN`),
`isAvailable` → `false`. `RATE_MAX_AGE_MINUTES = 0` désactive ce contrôle. Il n'existe **jamais
deux cotations courantes** simultanées pour une paire `(provider, currency_pair)`.
*Où :* `APP` (`ManualRateProvider`), `SQL` (`uq_rate_source_current`), politique **configurable**
(`system_settings`) / **auditée** (publication + rejet journalisés) / **testable**
(`ManualRateProviderTest`).

**I-5.** `rate_sources` est **append-only** : le montant d'une cotation publiée n'est jamais
modifié ; publier un nouveau taux clôt l'ancien (`effective_to`) puis insère le nouveau, dans la
même transaction, sous verrou de ligne.
*Où :* `APP` (`RateAdminService`, entité `@Immutable`), `LOCK` (`closeCurrent`), `SQL`.

---

## 3. Quote (devis)

**I-6.** Un `Quote` est un **snapshot financier immuable** : après création, aucune colonne
financière (`marketRate`, `marginPercentage`, `customerRate`, frais, `amountXof`, `amountCny`,
`netAmountXof`) n'est jamais modifiée — il n'existe aucun mutateur pour elles. Seuls `status` et
les horodatages de transition changent (`ACTIVE → ACCEPTED | EXPIRED | CANCELLED`).
*Où :* `APP` (`Quote` — pas de setter financier), `SQL` (`ck_quotes_net_coherent`), tests
`QuoteTest`.

**I-7.** Un `Quote` `ACTIVE` non accepté dans sa fenêtre (`expires_at = created_at +
RATE_LOCK_DURATION_MINUTES`, 30 min par défaut) **ne peut jamais produire d'`Order`**. L'expiration
est *paresseuse* (à la lecture et à chaque tentative de transition, avec correction du `status`
persisté) — cohérente et testée ; aucune ressource n'étant réservée par un `Quote`, aucun
scheduler n'est nécessaire.
*Où :* `APP` (`Quote.accept`/`cancel`/`effectiveStatus`), tests.

**I-8.** Un `Quote` accepté produit **au maximum un `Order`**.
*Où :* `SQL` (`uq_orders_quote`), `APP` (`OrderService.create` : `existsByQuoteId` + `catch
DataIntegrityViolationException → QUOTE_ALREADY_USED`), `LOCK` (implicite via la contrainte),
tests `OrderConcurrencyIT`.

---

## 4. Order

**I-9.** Un `Order` est créé **exclusivement** à partir d'un `Quote` déjà `ACCEPTED` et
appartenant au même utilisateur. Ses colonnes financières sont une **copie figée** du `Quote`,
**jamais recalculées** au taux courant. Le seul lien vivant est `quote_id` (traçabilité).
*Où :* `APP` (`OrderService.create`), tests.

**I-10.** Un `Order` **ne change de statut que via `OrderStateMachine`** (table de transitions
unique) orchestré par `OrderService`, sous **verrou pessimiste** (`findByIdForUpdate`). Aucun
autre service (`payment`, `settlement`, `expiration`) ne mute `Order.status` directement — ils
appellent `OrderService.transitionToXxx(...)` / `expireIfOverdue(...)`.
*Où :* `APP` (recherche `grep` confirmée : zéro mutation directe hors `OrderService`),
`LOCK`, tests `OrderStateMachineTest`, `OrderFlowIT`.

**I-11.** Transitions autorisées et leurs effets latéraux **atomiques** (même transaction) :

| Transition | Acteur | Effets latéraux |
|---|---|---|
| *(création)* → `AWAITING_PAYMENT` | client | `RESERVATION` CNY (si `TREASURY_RESERVE_ON_ORDER`), `payment_deadline_at` figée, historique |
| `AWAITING_PAYMENT` → `PAYMENT_SUBMITTED` | client | création du `Payment` (montant validé, cf. I-14) |
| `AWAITING_PAYMENT` → `CANCELLED` | client | `RELEASE` CNY si réservé |
| `AWAITING_PAYMENT` → `EXPIRED` | **système** (scheduler) | `RELEASE` CNY si réservé, audit `ORDER_EXPIRED`, notification |
| `PAYMENT_SUBMITTED` → `PAYMENT_VERIFIED` | admin | `Payment → CONFIRMED`, `DEPOSIT` XOF du montant reçu |
| `PAYMENT_SUBMITTED` → `REJECTED` | admin | `Payment → REJECTED` + motif, `RELEASE` CNY si réservé |
| `PAYMENT_VERIFIED` → `PROCESSING` | admin | création du `Settlement` (`PENDING`) |
| `PROCESSING` → `COMPLETED` | admin | `WITHDRAWAL` (consommation) CNY **si `order.isTreasuryReserved()`**, `Settlement → EXECUTED` |

États terminaux : `COMPLETED`, `CANCELLED`, `REJECTED`, `EXPIRED`.

**I-12.** **`COMPLETED` ne peut jamais être incohérent** : la transition `PROCESSING → COMPLETED`
n'est déclenchée que par `SettlementService.execute`, qui exige `Settlement` `PENDING` (garde
d'entité) + preuve de règlement si `REQUIRE_PAYMENT_PROOF` + (transitivement) un `Payment`
`CONFIRMED` (précondition pour atteindre `PROCESSING`). `settlement.execute()`, la consommation
de trésorerie et `orderService.transitionToCompleted()` sont dans **la même transaction** — tout
ou rien.
*Où :* `APP` (`SettlementService.execute`), `LOCK` (`findByIdForUpdate` sur `Settlement` et
`Order`), tests `SettlementFlowIT`.

**I-13.** Une `RESERVATION` CNY d'un ordre abandonné n'est **jamais bloquée indéfiniment** :
`payment_deadline_at` (= `created_at + ORDER_PAYMENT_WINDOW_MINUTES`, **stockée donc immuable par
ordre**) borne la fenêtre ; `OrderExpirationScheduler` → `OrderExpirationService.expireOverdue` →
`OrderService.expireIfOverdue` expire l'ordre et **libère** la réservation. **Idempotent et
rejouable** : re-vérification du statut ET de l'échéance après acquisition du verrou. Piloté par
`ORDER_AUTO_EXPIRE_ENABLED`.
*Où :* `APP` + `LOCK`, tests `OrderExpirationServiceIT`.

---

## 5. Payment

**I-14.** Le **montant XOF reçu déclaré** doit correspondre au **montant attendu** de l'ordre à
`PAYMENT_AMOUNT_TOLERANCE_XOF` près (défaut 0 = exact). Sous-paiement **et** sur-paiement
au-delà de la tolérance → `PAYMENT_AMOUNT_MISMATCH` (400), **aucune écriture** — un sous-paiement
accepté serait une perte de change directe (le décaissement CNY reste calculé sur le montant de
l'ordre).
*Où :* `APP` (`PaymentService.submit`), tests `PaymentFlowIT`.

**I-15.** Un `Order` porte **au plus un `Payment`** (rejet = état terminal, pas de
resoumission). Une **référence de transaction** ne sert **jamais deux paiements** (par méthode).
*Où :* `SQL` (`uq_payments_order`, `uq_payments_txref`), `APP` (`existsByOrderId`,
`existsByMethodAndTransactionReference` + `catch DataIntegrityViolationException` → codes métier
précis, jamais `DUPLICATE_RESOURCE` — passe 2 P2-6), tests.

**I-16.** Cycle `SUBMITTED → CONFIRMED | REJECTED`, transitions **gardées** par l'entité
(`requireSubmitted()`), sous verrou pessimiste (`findByIdForUpdate`). Double `confirm` /
`confirm` après `reject` / `reject` après `confirm` → `INVALID_PAYMENT_STATE` (409), aucune
double écriture trésorerie.
*Où :* `APP` (`Payment`, `PaymentService`), `LOCK`, tests.

**I-17.** Une **preuve de paiement** (`payment_proofs`) n'est jamais utilisée comme preuve sans
validation : type MIME sur **magic bytes** (pas seulement l'en-tête), taille ≤
`MAX_PROOF_FILE_SIZE_BYTES`, nom regénéré serveur (`storageKey` = `payment-proofs/{yyyy}/{MM}/
{uuid}.{ext}`), plafond `MAX_PROOFS_PER_PAYMENT`, checksum SHA-256 stocké. Aucun contenu binaire
en base. La confirmation admin exige ≥ 1 preuve si `REQUIRE_PAYMENT_PROOF`.
*Où :* `APP` (`FileValidator`, `LocalFileStorageService`, `PaymentService`), `SQL`
(`ck_payment_proofs_content_type`, `uq_payment_proofs_storage_key`), tests.

---

## 6. Settlement

**I-18.** Un `Settlement` (décaissement CNY) est **distinct** d'un `Payment` (encaissement XOF).
Créé **uniquement** depuis un ordre `PAYMENT_VERIFIED` ; **un seul règlement par ordre**.
*Où :* `SQL` (`uq_settlements_order`), `APP` (`existsByOrderId` + `catch → INVALID_SETTLEMENT_
STATE`), tests.

**I-19.** `Settlement EXECUTED` est **cohérent avec le payout** : `settlement_reference` **et**
`executed_at` obligatoires dès `EXECUTED` (garde d'entité + `ck_settlements_reference_on_
execution`). Le décaissement Yuan reste **manuel** — la plateforme ne prétend jamais l'avoir
effectué automatiquement.
*Où :* `APP` + `SQL`.

**I-20.** `execute` deux fois (séquentiel ou **concurrent**) → exactement un `EXECUTED`, les
autres `INVALID_SETTLEMENT_STATE` ; la trésorerie CNY n'est **consommée qu'une fois**.
*Où :* `LOCK` (`findByIdForUpdate`), `APP` (garde `PENDING`), `SQL` (`uq_treasury_tx_resolution_
per_order`), tests `SettlementFlowIT` (dont un test de concurrence réelle).

---

## 7. Trésorerie & Wallet (ledgers)

**I-21.** `TreasuryAccount` / `Wallet` sont des **projections matérialisées** du ledger
append-only (`treasury_transactions` / `wallet_transactions`), qui est la **source de vérité
d'audit**. Chaque mutation de solde et la ligne de ledger correspondante (portant
`balance_after` / `reserved_after`) committent dans **la même transaction**. Aucun `UPDATE` /
`DELETE` sur un ledger : une correction est une écriture compensatoire (`ADJUSTMENT`).
*Où :* `APP` (`TreasuryService`, `WalletService` seuls points d'écriture ; entités ledger
`@Immutable`), documenté en Javadoc.

**I-22.** Invariants de solde, garantis **à la fois** en `APP` et en `SQL` :
`balance ≥ 0` · `reserved_balance ≥ 0` · **`reserved_balance ≤ balance`** ·
`available = balance − reserved_balance` (jamais négatif).
*Où :* `SQL` (`ck_treasury_accounts_*`, `ck_wallets_*`), `APP` (`reserve` : `available ≥ amount`
sinon `INSUFFICIENT_TREASURY` / `INSUFFICIENT_WALLET_BALANCE` ; `adjust` : refus si `newBalance <
reservedBalance` — passe 2 P2-5).

**I-23.** **Une consommation ne peut jamais excéder la réservation réelle.** `consume`/`release`
lèvent `INSUFFICIENT_TREASURY` / `INSUFFICIENT_WALLET_BALANCE` **avant** toute mutation si
`amount > reservedBalance` (garde applicative en amont des `CHECK` SQL — passe 1). `Settlement
Service.execute` n'appelle `consume` **que si `order.isTreasuryReserved()`** — jamais pour un
ordre non réservé (sinon emprunt silencieux sur le pool partagé — passe 1).
*Où :* `APP` (`TreasuryAccount`/`Wallet` gardes, `SettlementService`), tests
`TreasuryServiceIT` / `WalletServiceIT`.

**I-24.** La **réservation d'un ordre est résolue exactement une fois** : au plus une
`RESERVATION`, au plus **une** résolution (`RELEASE` **xor** `WITHDRAWAL`) — jamais les deux,
jamais deux fois.
*Où :* `SQL` (`uq_treasury_tx_reservation_per_order`, `uq_treasury_tx_resolution_per_order` —
passe 2 P2-4), `APP` (machine d'état `Order`), `LOCK`, tests.

**I-25.** Réservations **concurrentes** : la somme des réservations réussies ne dépasse **jamais**
le disponible ; deux réservations / deux consommations / release+consume simultanés pour le même
ordre → au plus une réussit.
*Où :* `LOCK` (`SELECT ... FOR UPDATE` sur le compte), `SQL` (index uniques ci-dessus), tests de
concurrence réelle (`TreasuryServiceIT`, `WalletServiceIT`).

**I-26.** Un `Wallet` ne peut **jamais** être muté en contournant le ledger : `WalletService` est
le point d'écriture unique, aucun mutateur direct exposé, `deposit` non branché à un fournisseur
de paiement réel (brique de service). `PreferredRateService` (seul consommateur) passe toujours
par `reserve` / `debit` / `release`.
*Où :* `APP`, tests `WalletServiceIT`, `PreferredRateServiceIT`.

---

## 8. Historique & immuabilité

**I-27.** Un **historique financier ne dépend jamais du taux courant.** Modifier le taux (`rate_
sources`) n'altère **aucun** `Quote`, `Order`, `Payment`, `Settlement` existant.
*Où :* `APP` (snapshots figés, aucun re-calcul), test `changingRate_neverAffectsAlreadyCreated
Quote`.

**I-28.** Les **données financières historiques restent immuables** : `Quote` (colonnes
financières), `Beneficiary` (`@Immutable`), `OrderStatusHistory` (`@Immutable`), `PaymentProof` /
`SettlementProof` (`@Immutable`), tous les ledgers (`@Immutable`), `rate_sources` (`@Immutable`),
`audit_logs` (aucune écriture/suppression exposée, même à un `ADMIN`).
*Où :* `APP` (`@Immutable`), `SQL` (append-only par construction).

---

## 9. Idempotence & résilience

**I-29.** Toute opération financière critique est **idempotente ou protégée par contrainte** :

| Endpoint | Protection contre le double effet |
|---|---|
| `POST /api/v1/orders` | `Idempotency-Key` (opt.) + `uq_orders_quote` |
| `POST /api/v1/orders/{id}/payments` | `Idempotency-Key` (opt.) + `uq_payments_order` + `uq_payments_txref` + garde statut |
| `POST /api/admin/settlements/{id}/execute` | `Idempotency-Key` (opt.) + garde `PENDING` + `LOCK` + `uq_treasury_tx_resolution_per_order` |
| `POST /api/admin/treasury/deposit` \| `/adjust` | `Idempotency-Key` (opt.) — **seule** protection (aucun sens à une contrainte d'unicité sur un mouvement manuel libre) |
| `POST /api/admin/payments/{id}/confirm` \| `/reject` | garde statut (`requireSubmitted`) + `LOCK` |

**I-30.** Le framework d'idempotence est **transactionnellement sûr** : la **capture** de la clé
(`tryInsert`) est `REQUIRES_NEW` (durable avant l'action) ; l'**action métier** et
l'**enregistrement de la réponse** (`complete`) sont dans la **même transaction** (`guard` est
`@Transactional`, `complete` est `REQUIRED`) — l'effet métier et le cache de réponse committent
ensemble ou pas du tout (passe 2 P2-3). Un **échec métier n'est pas mis en cache** (`release
Pending`) → un rejeu ré-exécute et obtient la même erreur déterministe. Rejeu d'une clé
complétée → **réponse d'origine rejouée sans ré-exécution**. Clé réutilisée avec un corps
différent → `IDEMPOTENCY_KEY_REUSED`. Partitionnement `(userId, endpoint, idemKey)` — aucune
fuite inter-utilisateur.
*Où :* `APP` (`IdempotencyGuard`, `IdempotencyService`), `SQL` (`uq_idempotency_keys`), tests
`IdempotencyGuardIT`.

---

## 10. Effets de bord non financiers

**I-31.** Une **notification ne peut jamais** faire échouer ni annuler une transaction
financière : `NotificationService.create` est `REQUIRES_NEW` + exception absorbée (log `ERROR`
avec `type` + `userId`). Garantie : **best-effort `IN_APP`**, perte non silencieuse (traçable
dans les logs), **pas** un outbox durable.
*Où :* `APP`, tests `NotificationServiceIT`.

**I-32.** Toute opération financière importante est **tracée** dans `audit_logs` (`REQUIRES_NEW`,
échec d'audit absorbé) : publication de taux, création/acceptation/annulation/expiration de
devis, création/annulation/rejet/expiration/traitement/achèvement d'ordre,
soumission/confirmation/rejet de paiement, création/exécution de règlement,
réservation/libération/consommation de trésorerie, **modification de paramètre métier**
(`SETTING_UPDATED` avec ancienne + nouvelle valeur — passe 1). Répond à : qui / quoi / quand /
combien / quel taux / quelle source / quelle référence / état avant-après.
*Où :* `APP` (`AuditService`), tests.

---

## Propriétés globales garanties

```
NO DOUBLE EXECUTION        → I-8, I-16, I-20, I-29, I-30
NO DOUBLE RESERVATION      → I-8, I-24, I-25
NO OVER-CONSUMPTION        → I-2, I-23, I-24
NO INCONSISTENT COMPLETION → I-10, I-11, I-12
NO HISTORICAL RATE MUTATION→ I-5, I-27, I-28
NO FINANCIAL RACE CONDITION→ I-8(LOCK/SQL), I-16, I-20, I-25, I-30
NO SILENT LEDGER CORRUPTION→ I-21, I-22, I-23, I-24
```
