# Audit métier — refonte/durcissement du backend Converter

> Rapport interne, produit par inspection du **code réel** (entités, repositories, services,
> contrôleurs, enums, migrations Flyway `V1`→`V14`, tests). Le code fait foi ; la documentation
> de conception (`ARCHITECTURE.md`) n'est citée que comme référence d'intention. Format imposé :
> **EXISTANT / PROBLÈME / RISQUE / PROPOSITION / IMPACT**. Numérotation alignée sur les sections
> de la mission. Une ligne « ✅ Déjà conforme » ferme les points où le code satisfait déjà la
> cible sans changement nécessaire — listés pour preuve d'audit, pas laissés de côté.

---

## §4–6 · `RateProvider` / `RateSource`

| | |
|---|---|
| **EXISTANT** | `RateProvider` (port) expose `MarketRate currentRate(String pair)` + `RateProviderType type()`. `MarketRate` (record) = `currencyPair, cfaPerCny, source, effectiveAt, rateSourceId`. `RateSource` (`@Entity @Immutable`) : append-only, `effective_from`/`effective_to`, index unique partiel `uq_rate_source_current` sur `(provider_type, currency_pair, effective_to IS NULL)`. `ManualRateProvider` seule implémentation active. `RateAdminService.publishManualRate` clôture l'ancienne ligne (`UPDATE`) puis insère la nouvelle, même transaction. |
| **PROBLÈME** | Le contrat `RateProvider` n'expose pas la notion d'**« availability »** demandée par la mission (§4) sous une forme interrogeable sans exception : `currentRate(...)` lève `RATE_SOURCE_UNAVAILABLE` si aucune cotation n'existe. Un appelant qui veut *sonder* la disponibilité (ex. `PreferredRateService.processOne`) est aujourd'hui obligé d'attraper une `BusinessException` pour détecter ce cas — usage des exceptions comme flux de contrôle nominal. |
| **RISQUE** | Faible (fonctionnel, pas financier) — mais fragile : un futur appelant qui oublie le `catch` transforme une absence de cotation en 500 au lieu d'un comportement dégradé propre. |
| **PROPOSITION** | Ajouter `boolean isAvailable(String currencyPair)` à l'interface `RateProvider`, implémenté nativement par `ManualRateProvider` (lecture non verrouillée). Remplacer le `try/catch(BusinessException)` de `PreferredRateService.processOne` par un test explicite. |
| **IMPACT** | Un fichier d'interface + une méthode d'implémentation + un site d'appel. Pas de migration. Rétrocompatible (ajout pur). |

| | |
|---|---|
| **EXISTANT** | Historisation : jamais d'écrasement physique, `effective_to` ferme une ligne, la nouvelle est insérée. `findCurrentForPricing` prend un verrou `PESSIMISTIC_READ` (`FOR SHARE`) pendant tout calcul de pricing ; `closeCurrent` (`UPDATE`) prend le verrou exclusif de ligne, sérialisant naturellement une publication concurrente. Testé (`RateSourceUniquenessIT`, 2 tests : clôture correcte + violation de contrainte sur insertion directe d'un doublon). |
| **PROBLÈME** | Aucun. |
| **RISQUE** | — |
| **PROPOSITION** | ✅ **Déjà conforme** à la cible §5/§11 — répondre « quel était le taux à cet instant » est déjà garanti par l'historique append-only + `effective_from`/`effective_to`. |
| **IMPACT** | Aucun changement. |

---

## §6 · `RateEngine`

| | |
|---|---|
| **EXISTANT** | `rate/engine/*` n'importe ni Spring ni JPA (`AmountBasis`, `MoneyRounding`, `PricingResult`, `RateEngine` sont des types de valeur / composant pur). `customerRate = normalizeRate(marketRate × (1 + marginPercentage/100))`, jamais fusionné avec les frais (`feePercentage`/`fixedFeeXof`/`feeXof` = champs distincts de `PricingResult`). Testé (`RateEngineTest`, 9 cas ; `MoneyRoundingTest`, 3 cas). |
| **PROBLÈME** | Aucun. |
| **RISQUE** | — |
| **PROPOSITION** | ✅ **Déjà conforme**. |
| **IMPACT** | — |

---

## §7 · Support bidirectionnel XOF ↔ CNY

| | |
|---|---|
| **EXISTANT** | `AmountBasis{XOF, CNY}` + `RateEngine.price(basis, amount, ...)` avec deux chemins (`priceFromXof`, `priceFromTargetCny`), le second **recalculant tout depuis le montant XOF brut final** pour garantir que les deux sens appliquent exactement la même formule. `QuoteDirection{SEND_XOF, RECEIVE_CNY}` côté `quote`, traduit en `AmountBasis` par `QuoteService`. `RateEngine` ignore volontairement le vocabulaire `Quote`. |
| **PROBLÈME** | Le moteur reste **structurellement binaire XOF/CNY** (pas de `Currency source/target` générique) — mais **une seule paire de devises existe dans tout le système** (`RateProvider.DEFAULT_CURRENCY_PAIR = "XOF/CNY"`, en dur dans `RateSource.currency_pair` et partout ailleurs). Généraliser à un couple `(sourceCurrency, targetCurrency)` demanderait de traverser `quote`, `order`, `settlement`, `treasury`, `wallet`, `preferredrate` (tous typés XOF/CNY dans leurs colonnes et DTO) sans qu'aucun besoin fonctionnel actuel ne l'exige. |
| **RISQUE** | Sur-ingénierie : introduire une abstraction multi-devises non exercée par un seul appelant réel est le genre de changement que la mission demande explicitement d'éviter (§31 : « ne pas casser inutilement le schéma actuel », « privilégier la simplicité »). |
| **PROPOSITION** | **Ne pas généraliser le schéma de données maintenant.** Le point structurant demandé — « ne pas dupliquer la logique métier entre les deux sens » — est déjà acquis via `AmountBasis`/`priceFromXof`/`priceFromTargetCny`. Seule amélioration retenue : documenter explicitement (Javadoc déjà présente, complétée) que `AmountBasis` est le point d'extension si une deuxième paire apparaît un jour — aucune ligne de code changée. |
| **IMPACT** | Aucun changement de code. Décision documentée pour traçabilité de l'arbitrage. |

---

## §8 · Règles monétaires / rounding

| | |
|---|---|
| **EXISTANT** | `BigDecimal` exclusif dans tout `rate/`, `quote/`, `order/`, `payment/`, `settlement/`, `treasury/`, `wallet/`. Aucun `double`/`float` trouvé par recherche exhaustive dans `backend/src/main/java`. `MoneyRounding` centralise : `INTERMEDIATE = MathContext(20, HALF_UP)`, `roundXofUp` (scale 0, `UP`), `roundCnyDown` (scale 2, `DOWN`), `normalizeRate` (scale 6, `HALF_UP`). Dissymétrie documentée et testée (le résidu d'arrondi reste toujours côté trésorerie). |
| **PROBLÈME** | Aucun. |
| **RISQUE** | — |
| **PROPOSITION** | ✅ **Déjà conforme**. |
| **IMPACT** | — |

---

## §9–10 · `Quote` — modèle et cycle de vie

| | |
|---|---|
| **EXISTANT** | `Quote` porte tous les champs listés par la mission (§9) à l'identique. **Aucun mutateur** sur les colonnes financières — figées au constructeur depuis `PricingResult`, jamais recalculées. Trois transitions seulement (`accept`, `cancel`, + `expire` implicite). `expiresAt = createdAt + RATE_LOCK_DURATION_MINUTES` (30 min, paramètre `SettingsService`) — le compte à rebours démarre **à la création**, jamais à une simulation séparée (il n'y a d'ailleurs pas d'étape de simulation distincte : créer = déjà figer). `effectiveStatus(now)` calcule `EXPIRED` en lecture pure sans écriture ; `accept`/`cancel` re-vérifient l'expiration et **corrigent le statut persisté en `EXPIRED`** avant de lever `QUOTE_EXPIRED` — donc l'expiration paresseuse est cohérente y compris pour un lecteur qui interrogerait directement la table. |
| **PROBLÈME** | Aucun sur le modèle lui-même. Voir §11 pour la concurrence associée. |
| **RISQUE** | — |
| **PROPOSITION** | ✅ **Déjà conforme** — y compris l'exigence explicite « l'expiration paresseuse peut être conservée si elle est cohérente et testable » : elle l'est (`QuoteTest`, 8 cas domaine ; comportement testé aux deux niveaux lecture et transition). |
| **IMPACT** | — |

---

## §11 · Concurrence sur `Quote`

| | |
|---|---|
| **EXISTANT** | Création : `QuoteService.create` lit la cotation courante via `findCurrentForPricing` (**verrou partagé** `FOR SHARE`), dans la **même transaction** que l'écriture du `Quote` — une publication concurrente (`closeCurrent`, verrou exclusif) attend ou est attendue, jamais d'état intermédiaire. Acceptation/annulation : `findByIdForUpdate` (`PESSIMISTIC_WRITE`) + `Quote.accept/cancel` gardés par `requireActive()`. Testé en conditions réelles multi-threads : `QuoteConcurrencyIT` — 8 tentatives d'acceptation concurrente sur le même devis → exactement 1 succès, 7×409 ; 6 créations de devis en parallèle d'une republication de taux → chaque devis reste financièrement auto-cohérent, au plus 2 valeurs de sortie distinctes possibles. |
| **PROBLÈME** | Aucun. |
| **RISQUE** | — |
| **PROPOSITION** | ✅ **Déjà conforme**, avec couverture de test de concurrence réelle (pas seulement unitaire) déjà en place. |
| **IMPACT** | — |

---

## §12 · `Order`

| | |
|---|---|
| **EXISTANT** | `OrderService.create` : `Quote` doit être `ACCEPTED` (sinon `QUOTE_NOT_ACCEPTED`), `existsByQuoteId` (défense applicative) + `uq_orders_quote` (défense SQL). Colonnes financières = copie figée du `Quote`, jamais recalculées ; `Order` n'a aucun mutateur de statut public hors `applyStatus` appelé exclusivement par `OrderStateMachine`/`OrderService`. |
| **PROBLÈME** | Aucun. |
| **RISQUE** | — |
| **PROPOSITION** | ✅ **Déjà conforme**. |
| **IMPACT** | — |

---

## §13 · `Payment`

| | |
|---|---|
| **EXISTANT** | `uq_payments_order` (1 paiement/ordre, SQL) + `existsByOrderId` (Java) contre la double soumission. `uq_payments_txref` (SQL, non partiel) contre la réutilisation d'une référence de transaction. `FileValidator` vérifie les magic bytes (pas seulement le `Content-Type` déclaré). `payment.confirm/reject` gardés par `requireSubmitted()`. |
| **PROBLÈME** | Aucun structurel. |
| **RISQUE** | — |
| **PROPOSITION** | ✅ **Déjà conforme**. |
| **IMPACT** | — |

---

## §14 · `Settlement`

| | |
|---|---|
| **EXISTANT** | Entité distincte de `Payment`, `uq_settlements_order`, `execute()` gardé par le statut `PENDING`, `ck_settlements_reference_on_execution` (SQL) impose référence+date sur `EXECUTED`. |
| **PROBLÈME** | Voir §15–16 : le point de rupture n'est pas `Settlement` lui-même mais **son interaction avec `Treasury`**. |
| **RISQUE** | Voir ci-dessous. |
| **PROPOSITION** | Voir ci-dessous. |
| **IMPACT** | Voir ci-dessous. |

---

## §15–16 · `Treasury` et atomicité `Settlement` ↔ `Treasury` — **CONSTAT CRITIQUE**

| | |
|---|---|
| **EXISTANT** | `TreasuryAccount.consume(amount, now)` : `balance -= amount; reservedBalance -= amount` — **sans aucune vérification préalable** que `amount ≤ reservedBalance`. `TreasuryAccount.release(amount, now)` : `reservedBalance -= amount` — même absence de garde. `SettlementService.execute(...)` appelle **inconditionnellement** `treasuryService.consume(CNY, settlement.getAmountCny(), orderId, actor)`, **sans vérifier `order.isTreasuryReserved()`** (contrairement à `OrderService.releaseReservationIfNeeded`, qui *lui* vérifie ce drapeau avant de libérer). Seule protection existante : les contraintes SQL `ck_treasury_accounts_balance_positive` (`balance >= 0`), `ck_treasury_accounts_reserved_positive` (`reserved_balance >= 0`) et `uq_treasury_tx_order_type` (empêche un *second* `WITHDRAWAL` sur le *même* `order_id`, mais ne dit rien sur la cohérence du *premier*). |
| **PROBLÈME** | `reserved_balance` est un **solde agrégé par devise**, pas un solde par ordre. Si un `Settlement.execute()` est déclenché pour un ordre **jamais réservé** — ordre créé pendant une fenêtre où `TREASURY_RESERVE_ON_ORDER=false`, ou dont la réservation aurait déjà été relâchée par un chemin annexe — `consume()` décrémente quand même `reservedBalance` du montant de cet ordre. Deux issues possibles, toutes deux mauvaises : **(a)** si `reservedBalance` du pool est insuffisante pour absorber ce retrait imprévu, la contrainte SQL `CHECK (reserved_balance >= 0)` rejette l'écriture → la transaction *entière* (y compris le passage `Settlement → EXECUTED` et `Order → COMPLETED`) est annulée, mais l'erreur renvoyée au client est **`409 DUPLICATE_RESOURCE`** (`GlobalExceptionHandler.handleIntegrity`), un code qui ne dit rien sur la vraie cause ; **(b)** si `reservedBalance` du pool est *par coïncidence* suffisante — parce que **d'autres ordres, légitimement réservés, y contribuent** — `consume()` réussit silencieusement en empruntant sur la réservation d'un tiers : la trésorerie affiche alors un `reservedBalance` cohérent en apparence, alors que la réservation qui protégeait un *autre* ordre a été partiellement consommée à sa place. C'est une violation directe de l'invariant central demandé en §16 (« la mise à jour métier et le ledger doivent être cohérents ») et de la garantie anti-survente de CNY (§15). |
| **RISQUE** | 🔴 **Critique.** C'est exactement le scénario que le cahier des charges désigne comme « point critique » (§16) : possibilité de dériver `Treasury = CONSUMED` sans réservation correspondante réellement associée à *cet* ordre, avec un message d'erreur trompeur dans le meilleur cas et une corruption silencieuse du pool partagé dans le pire cas. Le chemin d'exécution normal (réservation systématique activée par défaut, `TREASURY_RESERVE_ON_ORDER=true` en seed `V4`) ne déclenche jamais ce bug aujourd'hui — d'où son absence de couverture de test — mais rien dans le code n'empêche de l'atteindre (paramètre désactivable à chaud via `PUT /api/admin/settings/TREASURY_RESERVE_ON_ORDER`, effet immédiat, cache invalidé). |
| **PROPOSITION** | **(1)** Ajouter une garde d'invariant **au niveau du domaine** (`TreasuryAccount.consume`/`release`) : lever une `BusinessException(ErrorCode.INSUFFICIENT_TREASURY)` explicite si `amount > reservedBalance`, **avant** toute mutation — défense en profondeur Java, en complément (pas en remplacement) des `CHECK` SQL déjà en place. **(2)** Corriger `SettlementService.execute` pour ne consommer la trésorerie **que si `order.isTreasuryReserved()`** — miroir exact du garde-fou déjà appliqué côté libération dans `OrderService`. Même correction appliquée par symétrie à `Wallet.consume`/`release` (même famille de bug, même modèle de solde agrégé). |
| **IMPACT** | 4 méthodes modifiées (`TreasuryAccount.consume/release`, `Wallet.consume/release`), 1 site d'appel corrigé (`SettlementService.execute`). Aucune migration (les contraintes SQL existantes restent, la garde applicative s'ajoute en amont). Aucun changement de comportement sur le chemin nominal (réservation systématique) — les tests existants ne sont pas affectés. Nouveaux tests ajoutés (voir §25 ci-dessous). |

---

## §17 · Idempotence HTTP — **CONSTAT CRITIQUE (déjà connu, confirmé)**

| | |
|---|---|
| **EXISTANT** | Table `idempotency_keys` créée en `V1__initial_schema.sql` (`idem_key, user_id, endpoint, request_hash, response_status, response_body, created_at, expires_at`, contrainte `uq_idempotency_keys(user_id, endpoint, idem_key)`). **Recherche exhaustive** (`grep -r "dempotency" backend/src/main/java`) : **aucune entité, aucun repository, aucun service, aucun contrôleur ne référence cette table.** `Idempotency-Key` figure uniquement dans la liste des en-têtes CORS autorisés (`SecurityConfig.corsConfigurationSource`). |
| **PROBLÈME** | `POST /api/v1/orders`, `POST /api/v1/orders/{id}/payments`, `POST /api/admin/settlements/{id}/execute`, `POST /api/admin/treasury/deposit`, `POST /api/admin/treasury/adjust` n'ont **aucune protection contre un rejeu HTTP** (timeout client, double clic, retry automatique d'un proxy) au-delà des contraintes SQL d'unicité déjà en place pour certains d'entre eux (`uq_orders_quote`, `uq_payments_order`). Pour les opérations de trésorerie (`deposit`/`adjust`), **aucune protection SQL n'existe non plus** : un rejeu double-crédite ou double-ajuste réellement le solde. |
| **RISQUE** | 🟠 **Élevé** pour `treasury/deposit` et `treasury/adjust` (aucun filet SQL, un rejeu produit un mouvement réel dupliqué) ; 🟡 **Moyen** pour `orders`/`payments`/`settlements/execute` (filet SQL existant, mais l'erreur renvoyée sur rejeu est une erreur métier `409` — pas la réponse originale attendue par un client qui rejoue une requête après timeout, ce qui casse le contrat d'idempotence même si l'état financier reste correct). |
| **PROPOSITION** | Construire un composant `common/idempotency/` exploitant la table existante (aucune migration nécessaire) : capture (« claim ») atomique de la clé dans une transaction dédiée `REQUIRES_NEW`, exécution de l'action métier, puis persistance du résultat (`response_status`/`response_body`) dans une seconde transaction `REQUIRES_NEW`. Sémantique : clé absente → exécute et mémorise ; clé présente avec même hash de requête et réponse déjà connue → **rejoue la réponse d'origine sans ré-exécuter l'action** ; clé présente avec un hash différent → `409` explicite (« clé réutilisée avec un corps différent ») ; clé présente sans réponse encore connue (concurrence ou crash en vol) → `409` explicite (« requête déjà en cours »), et la ligne « en vol » est **supprimée** si l'action métier échoue, pour ne pas bloquer indéfiniment une nouvelle tentative légitime après correction. Câblé sur les 5 endpoints listés par la mission, via un en-tête `Idempotency-Key` **optionnel** (rétrocompatible — son absence ne change rien au comportement actuel). |
| **IMPACT** | Nouveaux fichiers : `IdempotencyKey` (entité), `IdempotencyKeyRepository`, `IdempotencyService`, 2 nouveaux `ErrorCode`. 5 contrôleurs modifiés (paramètre d'en-tête optionnel + délégation à `IdempotencyService.guard(...)`). Aucune migration (schéma déjà présent). Tests ajoutés (voir §25). |

---

## §18 · `Wallet`

| | |
|---|---|
| **EXISTANT** | Modèle `Available = balance − reservedBalance`, ledger append-only (`CREDIT/DEBIT/RESERVE/RELEASE`), verrou pessimiste `findByUserIdForUpdate`. `reserve()` vérifie `available() ≥ amount` (sinon `INSUFFICIENT_WALLET_BALANCE`). |
| **PROBLÈME** | Même classe de bug que §15–16 : `consume()`/`release()` sans garde contre un montant supérieur au réservé. Dans le code actuel, tous les appelants (`PreferredRateService.trigger`, `.expire`, `.cancel`) passent systématiquement le **même montant** que celui réservé à la création de la demande, donc le chemin nominal ne l'atteint jamais — mais rien dans le type ne l'empêche structurellement. |
| **RISQUE** | 🟡 Moyen — pas d'appelant fautif identifié aujourd'hui, mais absence de défense en profondeur sur une opération irréversible (débit réel du solde). |
| **PROPOSITION** | Même garde que pour `TreasuryAccount` (§15–16), par symétrie et cohérence de la base de code. |
| **IMPACT** | 2 méthodes modifiées (`Wallet.consume`, `Wallet.release`). Aucune migration. |

---

## §19 · `PreferredRate`

| | |
|---|---|
| **EXISTANT** | Réservation immédiate à la création (`walletService.reserve`, avant même que le taux cible soit atteint). Scheduler `fixedDelay` (jamais `fixedRate` — pas de chevauchement intra-instance) : `processOne`/`progressOne` chacun sous verrou pessimiste ligne par ligne, avec **double vérification du statut après acquisition du verrou** (`if status != ACTIVE return`) — protège contre un second passage du scheduler ou une annulation utilisateur concurrente. `Exchange` porte trois marqueurs (`progress45SentAt`/`progress90SentAt`/`completedAt`) empêchant une notification dupliquée même si le scheduler tourne plus souvent que prévu. Testé (`PreferredRateServiceIT`, 11 cas ; `ExchangeTest`, 7 ; `PreferredRateRequestTest`, 9). |
| **PROBLÈME** | Aucun défaut de robustesse identifié dans la logique de déclenchement/expiration elle-même. |
| **RISQUE** | — |
| **PROPOSITION** | ✅ **Déjà conforme** à l'exigence « scheduler idempotent et résistant à plusieurs exécutions ». |
| **IMPACT** | — |

---

## §20 · Notifications

| | |
|---|---|
| **EXISTANT** | `NotificationService.create(...)` est appelé **après** la validation métier (jamais avant), dans la **même transaction** que l'opération financière (pas de `REQUIRES_NEW`, pas de try/catch autour) — contrairement à `AuditService` qui, lui, isole explicitement ses écritures. |
| **PROBLÈME** | Si l'écriture de la notification échouait (contrainte, connexion), elle ferait échouer **toute** la transaction métier (rollback de l'ordre/paiement/etc.) — l'inverse de la règle demandée (« une erreur de notification ne doit jamais annuler une transaction financière déjà validée »). |
| **RISQUE** | 🟡 Moyen en théorique (aucune contrainte SQL sur `notifications` n'est actuellement susceptible d'échouer sur des données valides — `type`/`channel` sont des enums Java déjà bornés, `title`/`message` ne dépassent jamais leurs longueurs déclarées), mais le **principe** demandé n'est pas respecté structurellement : rien n'empêche une régression future de rendre ce chemin fragile. |
| **PROPOSITION** | Envelopper `NotificationService.create` dans la même stratégie que `AuditService` : `@Transactional(propagation = REQUIRES_NEW)` + absorption de l'exception (log `ERROR`, jamais de propagation). Une notification est un **effet secondaire**, jamais une condition de succès de l'opération financière. |
| **IMPACT** | 1 méthode modifiée (`NotificationService.create`). Aucune migration. Risque de régression très faible (le comportement nominal — écriture réussie — est inchangé). |

---

## §21 · Audit

| | |
|---|---|
| **EXISTANT** | `AuditService.record`/`recordSystem` en `REQUIRES_NEW`, exception absorbée. Toutes les actions listées par la mission §21 sont déjà couvertes par `AuditAction`, à une exception près. |
| **PROBLÈME** | `SettingsService.update(...)` **ne écrit jamais d'entrée d'audit** — l'action `AuditAction.SETTING_UPDATED` existe dans l'enum (héritée de la Phase 2) mais **aucun appel à `auditService.record(...)` ne la déclenche** ; `SettingsService` ne dépend même pas d'`AuditService`. Or `DEFAULT_MARGIN_PERCENTAGE`/`DEFAULT_FEE_PERCENTAGE`/`ENABLED_PAYMENT_METHODS`/`TREASURY_RESERVE_ON_ORDER` sont des paramètres qui pèsent directement sur le pricing et les protections financières — leur modification est une opération sensible non tracée. |
| **RISQUE** | 🟡 Moyen — pas une faille financière directe, mais un trou de traçabilité sur des paramètres qui *déterminent* le calcul financier (répond mal à « qui a changé la marge, quand, avec quelle ancienne/nouvelle valeur »). |
| **PROPOSITION** | Injecter `AuditService` dans `SettingsService`, enregistrer `SETTING_UPDATED` avec `metadata = {key, oldValue, newValue}` dans `update(...)`. |
| **IMPACT** | 1 constructeur + 1 méthode modifiés. Aucune migration. |

---

## §22 · Sécurité

| | |
|---|---|
| **EXISTANT** | JWT stateless + revalidation du compte à chaque requête, RBAC double barrière (`SecurityFilterChain` + `@PreAuthorize`), `OwnershipService` → 404 systématique, CORS strict par liste explicite, `RateLimitFilter` par IP sur `/api/auth/login`. Tous les endpoints admin (`rate`, `order`, `payment`, `settlement`, `treasury`, `settings`, `users`, `audit-logs`) portent `@PreAuthorize("hasRole('ADMIN')")` **en plus** du matcher global `/api/admin/**`. Tous les endpoints client (`quote`, `order`, `payment`, `wallet`, `preferredrate`, `notification`) filtrent systématiquement par `currentUser.getId()` — soit en paramètre de requête (repositories `findByUserId*`), soit via `OwnershipService.assertOwnedBy` après chargement. |
| **PROBLÈME** | Aucune régression de sécurité identifiée. Les nouveaux endpoints/paramètres introduits par cette mission (en-tête `Idempotency-Key`) doivent être vérifiés pour ne fuiter aucune donnée d'un autre utilisateur — voir garantie ci-dessous. |
| **RISQUE** | — |
| **PROPOSITION** | La clé d'idempotence est scoping-partitionnée par `(userId, endpoint, idemKey)` : un utilisateur ne peut jamais lire la réponse mise en cache d'un autre (la clé de recherche inclut systématiquement `currentUser.getId()`, jamais un identifiant fourni par le client). Vérifié à la conception, testé (voir §25). |
| **IMPACT** | Aucune dégradation ; garantie explicitement testée pour la nouvelle fonctionnalité. |

---

## §23 · Machine d'état `Order`

| | |
|---|---|
| **EXISTANT** | Toute transition passe par `OrderService.transitionToXxx(...)` → `transition(...)` → `OrderStateMachine.assertTransition` → `order.applyStatus(...)`. `PaymentService`/`SettlementService` n'appellent **jamais** de mutateur direct sur `Order` — recherche exhaustive (`grep "order.applyStatus\|order.setStatus"` hors `order/`) confirme zéro occurrence en dehors de `OrderService`. |
| **PROBLÈME** | Aucun. |
| **RISQUE** | — |
| **PROPOSITION** | ✅ **Déjà conforme** — la règle fondamentale §29 (« ne jamais modifier directement `Order.status` ») est déjà respectée structurellement, sans exception trouvée. |
| **IMPACT** | — |

---

## §24 · Contraintes SQL (défense en profondeur)

| | |
|---|---|
| **EXISTANT** | Chaque invariant financier critique est doublé : `uq_orders_quote`, `uq_payments_order`, `uq_payments_txref`, `uq_settlements_order`, `uq_treasury_tx_order_type`, `uq_wallets_user`, `uq_rate_source_current`, plus les `CHECK` (`reserved_balance <= balance`, montants `> 0`, cohérence `net = brut − frais`). |
| **PROBLÈME** | Aucun trou de couverture SQL identifié — le point faible n'était pas l'absence de contrainte SQL (elle existe et fonctionne, cf. §15–16) mais l'absence de garde **applicative** en amont, qui transformait une violation SQL prévisible en `409 DUPLICATE_RESOURCE` peu explicite au lieu d'un rejet métier clair. |
| **RISQUE** | — (déjà traité en §15–16) |
| **PROPOSITION** | Voir §15–16 — pas de changement de migration nécessaire ici, uniquement des gardes Java en amont des contraintes déjà en place. |
| **IMPACT** | — |

---

## Synthèse des actions — **livrées et validées** (`./mvnw test` : 170 tests, 0 échec)

| # | Action | Modules / fichiers | Migration | Statut |
|---|---|---|---|---|
| 1 | Garde d'invariant `consume`/`release` : rejet `BusinessException(INSUFFICIENT_TREASURY / INSUFFICIENT_WALLET_BALANCE)` **avant** la contrainte SQL, si le montant dépasse le solde réservé (agrégé) | `TreasuryAccount`, `Wallet` | non | ✅ Livré |
| 2 | `SettlementService.execute` ne consomme la trésorerie **que si `order.isTreasuryReserved()`** (miroir de `OrderService.releaseReservationIfNeeded`) ; sinon `WARN` + saut de la consommation | `SettlementService` | non | ✅ Livré |
| 3 | **Idempotence HTTP réelle** : `common/idempotency/` (`IdempotencyKey`, `IdempotencyKeyRepository`, `IdempotencyService`, `IdempotencyGuard`), 2 nouveaux `ErrorCode` (`IDEMPOTENCY_KEY_REUSED`, `IDEMPOTENT_REQUEST_IN_PROGRESS`), en-tête `Idempotency-Key` **optionnel** câblé sur `POST /api/v1/orders`, `POST /api/v1/orders/{id}/payments`, `POST /api/admin/settlements/{id}/execute`, `POST /api/admin/treasury/deposit`, `POST /api/admin/treasury/adjust` | `common`, `order`, `payment`, `settlement`, `treasury` | **`V15`** (`request_hash` `CHAR(64)` → `VARCHAR(64)`, alignement identique à `checksum_sha256` en V10/V11) | ✅ Livré |
| 4 | `RateProvider.isAvailable(currencyPair)` ajouté au contrat (mission §4 « availability ») + implémenté par `ManualRateProvider` (lecture non verrouillée) | `rate` | non | ✅ Livré — **mais non câblé dans `PreferredRateService`** (voir note ci-dessous) |
| 5 | `NotificationService.create` en `@Transactional(REQUIRES_NEW)` + exception d'écriture **absorbée** (log `ERROR`, jamais propagée) — une notification ne peut plus faire échouer ni rollback une transaction financière déjà validée | `NotificationService` | non | ✅ Livré |
| 6 | `SettingsService.update` émet `AuditAction.SETTING_UPDATED` avec `metadata = {previousValue, newValue}` (dépendance `settings → audit` ajoutée, sans cycle) | `SettingsService` | non | ✅ Livré |

### Pièges rencontrés et corrigés pendant l'implémentation (traçabilité)

- **Type de colonne `idempotency_keys.request_hash`** : `CHAR(64)` en V1, incompatible avec le mapping JPA `String` (→ `VARCHAR`) sous `ddl-auto=validate`. Résolu par la migration `V15` (élargissement strict, aucune donnée affectée — la table n'avait jamais été alimentée).
- **PostgreSQL « current transaction is aborted »** : dans `IdempotencyService`, rattraper une `DataIntegrityViolationException` (`uq_idempotency_keys`) puis relire dans la **même** transaction échoue (la transaction JDBC est empoisonnée par l'erreur SQL). Corrigé en séparant en deux transactions `REQUIRES_NEW` distinctes : `tryInsert(...)` (échoue et roll back proprement) puis, **après rattrapage hors de cette transaction**, `findExisting(...)` (transaction neuve).
- **Hibernate `@Immutable` + `PESSIMISTIC_READ`** : appeler `rateProvider.isAvailable(...)` (lecture non verrouillée de `RateSource`) puis `currentPricing(...)` (lecture `PESSIMISTIC_READ` de la même ligne) dans la **même** transaction fait lever `UnsupportedLockAttemptException` (« Lock mode not supported » — Hibernate refuse d'élever le lock mode d'une entité immuable déjà gérée). `PreferredRateService.processOne` et `toResponse` **conservent donc la gestion par `try/catch(BusinessException)`** ; `RateProvider.isAvailable` reste une méthode d'interface valide et testée unitairement, simplement pas exploitée à cet endroit précis. Documenté par un commentaire dans le code.

### Non retenu pour cette itération (documenté, hors périmètre ou déjà conforme)

- Généralisation multi-devises du `RateEngine` (§7) — une seule paire XOF/CNY existe ; `AmountBasis` couvre déjà l'exigence « ne pas dupliquer la logique des deux sens ». Généraliser le schéma serait une régression de simplicité (mission §31).
- Job d'expiration `Order` / `Quote` — hors périmètre des 30 sections de la mission ; l'expiration paresseuse du `Quote` est cohérente et testée (mission §10 l'autorise explicitement).
- Stats admin `orderCount` / `totalAmountCfa` codées en dur — déjà signalé (`BACKEND.md §20`), non financier, non demandé.
- Modules `risk` / `dashboard` — non demandés par cette mission.
