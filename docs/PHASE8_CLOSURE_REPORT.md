# PHASE 8 — CLOSURE REPORT

Évolution Burkina Faso ↔ Chine — Backend Converter. Date : 2026-09-03.
Périmètre : profil professionnel, distinction PERSONAL/BUSINESS, historique enrichi des
ordres, reporting Business consolidé. Reprise et clôture d'une implémentation Phase 8
partielle laissée par une session précédente (quota épuisé avant validation).

---

## 1. Architecture BusinessProfile

Nouveau module racine `com.converter.business`, package-by-feature, deux sous-domaines :

- `business.profile` — identité professionnelle de l'utilisateur (CRUD singleton).
- `business.reporting` — vue consolidée en lecture seule des ordres d'un utilisateur Business.

`business` ne dépend en écriture d'**aucun** module du pipeline financier
(`Quote`/`Order`/`Payment`/`Settlement`/`Treasury`/`Wallet`). `business.reporting` lit `Order`
via `OrderRepository` (agrégation SQL), jamais une table dupliquée : les mêmes ordres servent
aux particuliers et aux professionnels.

La distinction PERSONAL/BUSINESS n'ajoute **aucune colonne `accountType` sur `users`**
(décision d'architecture, migration V26 en-tête) : elle est portée exclusivement par
l'existence d'une ligne `business_profiles`, encapsulée dans
`BusinessProfileService.isBusinessUser(UUID)`.

---

## 2. Fichiers créés

### Code principal (13)

| Fichier | Rôle |
|---|---|
| `business/profile/domain/BusinessProfile.java` | Entité (`@Version`, `update(...)` en place) |
| `business/profile/domain/BusinessType.java` | Enum `IMPORTER, MERCHANT, SERVICES, OTHER` |
| `business/profile/dto/UpsertBusinessProfileRequest.java` | Corps `PUT` (jamais de `userId`) |
| `business/profile/dto/BusinessProfileResponse.java` | Réponse lecture |
| `business/profile/repository/BusinessProfileRepository.java` | `findByUserId`, `existsByUserId` |
| `business/profile/service/BusinessProfileService.java` | Upsert idempotent, `isBusinessUser`, audit |
| `business/profile/web/BusinessProfileController.java` | `GET` / `PUT /api/v1/business-profile` |
| `business/reporting/dto/BusinessPaymentSummaryResponse.java` | Résumé consolidé |
| `business/reporting/dto/ReportPeriod.java` | Bornes `from`/`to` (nullable) |
| `business/reporting/service/BusinessPaymentReportService.java` | Agrégation SQL par statut |
| `business/reporting/web/BusinessReportingController.java` | `GET /api/v1/business/payments/summary` |
| `order/dto/OrderHistoryResponse.java` | Ligne d'historique enrichi |
| `order/service/OrderHistoryService.java` | Projection lecture seule, tri fixe, filtres |
| `order/repository/OrderStatusAggregate.java` | Projection d'agrégation `GROUP BY status` |

### Migration (1)

| Fichier | Rôle |
|---|---|
| `src/main/resources/db/migration/V26__business_profiles.sql` | Table `business_profiles` |

### Tests (4 classes)

| Fichier | Tests |
|---|---|
| `business/profile/service/BusinessProfileServiceTest.java` | 5 (unitaire, Mockito) |
| `business/profile/web/BusinessProfileHttpIT.java` | 12 (IT HTTP) |
| `business/reporting/web/BusinessReportingHttpIT.java` | 5 (IT HTTP) |
| `order/web/OrderHistoryHttpIT.java` | 10 (IT HTTP) |

---

## 3. Fichiers modifiés

| Fichier | Changement | Session |
|---|---|---|
| `audit/domain/AuditAction.java` | `+ BUSINESS_PROFILE_CREATED`, `+ BUSINESS_PROFILE_UPDATED` | Phase 8 (antérieure) |
| `common/exception/ErrorCode.java` | `+ BUSINESS_PROFILE_NOT_FOUND` (404) | Phase 8 (antérieure) |
| `order/web/OrderController.java` | `+ GET /history` (endpoint additif) | Phase 8 (antérieure) |
| `order/repository/OrderRepository.java` | `+ searchHistory(...)`, `+ aggregateByStatus(...)` (patron `hasXxx`/`xxx`) | Phase 8 (antérieure) |
| `support/AbstractOrderPipelineIT.java` | `+ completeOrder(...)`, `+ rejectPayment(...)`, helpers refund | Phases 4-8 (antérieures) |
| **`audit/repository/AuditLogRepository.java`** | **`+ findByEntityTypeAndEntityIdOrderByCreatedAtDesc(...)`** — finder dérivé additif ; `search` **inchangé** | **Reprise (cette session)** |
| **`business/profile/web/BusinessProfileHttpIT.java`** | **2 méthodes de vérification d'audit recâblées** sur le nouveau finder ; import `PageRequest` retiré | **Reprise (cette session)** |
| `docs/ARCHITECTURE.md` | Section 0.F : « Phases 1-7 » → « Phases 1-8 » + sous-section Phase 8 + note sur `AuditLogRepository.search` | Reprise (cette session) |

Aucune modification du pricing, de la logique `Quote/Order/Payment/Settlement/Treasury/Refund`,
de l'idempotence, des contraintes SQL du cœur financier, de `RateLimitFilter`, de
`GlobalExceptionHandler`. La méthode `AuditLogRepository.search` n'a **pas** été touchée
(seul un finder dérivé indépendant a été ajouté).

---

## 4. Migration(s)

**`V26__business_profiles.sql`** — additive, aucune donnée existante affectée.

```sql
CREATE TABLE business_profiles (
    id UUID PK DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    business_name VARCHAR(160) NOT NULL,
    business_type VARCHAR(24) NOT NULL,
    registration_number VARCHAR(60) NULL,
    country VARCHAR(100) NOT NULL,
    city VARCHAR(100) NULL,
    address VARCHAR(255) NULL,
    created_at / updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_business_profiles_user UNIQUE (user_id),
    CONSTRAINT ck_business_profiles_type CHECK (business_type IN ('IMPORTER','MERCHANT','SERVICES','OTHER')),
    CONSTRAINT fk_business_profiles_user FOREIGN KEY (user_id) REFERENCES users (id)
);
```

- Enum stocké `VARCHAR + CHECK` (jamais un type ENUM PostgreSQL).
- Aucun index supplémentaire : `UNIQUE(user_id)` crée déjà l'index `btree` qui couvre
  `findByUserId`/`existsByUserId`.
- Migrations V1–V25 **non modifiées**. Prochaine migration libre : **V27**.

---

## 5. API endpoints

| Méthode | Chemin | Auth | Accès | Statuts |
|---|---|---|---|---|
| `GET` | `/api/v1/business-profile` | JWT | tout utilisateur | `200` / `404 BUSINESS_PROFILE_NOT_FOUND` / `401` |
| `PUT` | `/api/v1/business-profile` | JWT | tout utilisateur | `201` (créé) / `200` (mis à jour) / `400 VALIDATION_ERROR` / `409 DUPLICATE_RESOURCE` (course) / `401` |
| `GET` | `/api/v1/orders/history` | JWT | **tout utilisateur** (Personal comme Business) | `200` (page) / `401` |
| `GET` | `/api/v1/business/payments/summary` | JWT | **profils Business uniquement** | `200` / `404 BUSINESS_PROFILE_NOT_FOUND` / `401` |

`GET /orders/history` — filtres optionnels : `status`, `purpose`, `supplierId`, `from`, `to`
(`from <= createdAt < to`), pagination `Pageable` (`size` défaut 20), tri **fixe**
`createdAt DESC, id DESC` (non surchargeable par l'appelant).

`GET /business/payments/summary` — `from`/`to` optionnels.

Swagger/OpenAPI reste désactivé en profil `prod` (inchangé).

---

## 6. Personal/Business detection

- Règle unique : `BusinessProfileService.isBusinessUser(userId)` = `repository.existsByUserId(userId)`.
- Aucune colonne `users.account_type`. Aucune déduction `if (profile != null)` dans un
  contrôleur.
- `GET /business/payments/summary` est le seul endpoint qui exige `isBusinessUser == true` ;
  sinon `404 BUSINESS_PROFILE_NOT_FOUND` (jamais `403` — convention du backend).
- `GET /orders/history` **n'exige pas** de profil Business : l'historique est ouvert à tous.

---

## 7. BusinessProfile lifecycle

`PUT` idempotent (`upsert`) :

- profil absent → `new BusinessProfile(userId, ...)` → `save` → audit `BUSINESS_PROFILE_CREATED`
  → `201`.
- profil présent → `profile.update(...)` en place (jamais un second `save`/une seconde ligne)
  → audit `BUSINESS_PROFILE_UPDATED` → `200`.

Aucune suppression exposée (pas de `DELETE`). `@Version` optimiste sur l'entité.

---

## 8. Ownership

- Le propriétaire vient **exclusivement** de l'utilisateur authentifié (`CurrentUser.getId()`),
  jamais d'un champ `userId` du corps (`UpsertBusinessProfileRequest` n'en a pas).
- Profils isolés par utilisateur — test `profiles_areIsolatedPerUser`.
- `GET /orders/history` : `OrderRepository.searchHistory` filtre **toujours** par `userId` en
  premier ; un `supplierId` appartenant à un autre utilisateur ne renvoie jamais ses ordres,
  seulement zéro résultat (test `history_supplierIdOfAnotherUser_returnsEmpty` dans
  `OrderHistoryHttpIT`).

---

## 9. Audit

- `BUSINESS_PROFILE_CREATED` / `BUSINESS_PROFILE_UPDATED` via `AuditService.record(...)`
  (transaction `REQUIRES_NEW`, comportement inchangé).
- Aucun audit sur les lectures (`GET` profil, historique, résumé).
- Vérification de bout en bout : `BusinessProfileHttpIT.put_create_recordsAuditEvent` /
  `put_update_recordsAuditEvent`, via le finder dérivé `findByEntityTypeAndEntityIdOrderByCreatedAtDesc`
  (voir §27).

---

## 10. Order history

`OrderHistoryService.search(userId, status, purpose, supplierId, from, to, pageable)` →
`OrderRepository.searchHistory(...)` → `Page<Order>` → `OrderHistoryResponse`.

- Projection lecture seule (`@Transactional(readOnly = true)`), aucune mutation, aucune seconde
  source de vérité — même séparation que `OrderTrackingService`.
- Pas de `findAll()` global : pagination poussée dans PostgreSQL.

---

## 11. Filters

| Filtre | Type | Sémantique |
|---|---|---|
| `status` | `OrderStatus` | égalité exacte |
| `purpose` | `Purpose` | égalité exacte |
| `supplierId` | `UUID` | égalité exacte, **toujours** intersecté avec `userId` |
| `from` | `Instant` | `createdAt >= from` |
| `to` | `Instant` | `createdAt < to` |

Tous optionnels, implémentés avec le patron **paire `hasXxx` (boolean) / `xxx` (valeur)** —
jamais `:x IS NULL` isolé (évite le bug de type PostgreSQL décrit §27). Même patron que
`PublicRateSnapshotRepository` (Phase 5).

---

## 12. Pagination

- `Pageable` fourni par Spring (`@PageableDefault(size = 20)` pour `/history`).
- Seuls `pageNumber` / `pageSize` sont retenus ; tout `sort` de l'appelant est **ignoré** —
  tri serveur imposé `createdAt DESC, id DESC` (déterministe, départage les ordres de même
  instant).
- Réponse `PageResponse<T>` (enveloppe pagination standard du projet).

---

## 13. Business reporting

`GET /api/v1/business/payments/summary` → `BusinessPaymentSummaryResponse` :

```json
{
  "period": { "from": null, "to": null },
  "transferCount": 4,
  "completedCount": 2,
  "cancelledCount": 1,
  "rejectedCount": 1,
  "totalAmountXof": 300000.00,
  "totalAmountCny": 3562.94,
  "totalFeesXof": 0.00
}
```

- `transferCount` = somme de tous les statuts sur le périmètre.
- `completedCount` / `cancelledCount` / `rejectedCount` = comptes par statut.
- `totalAmountXof` / `totalAmountCny` / `totalFeesXof` = sommes des ordres **`COMPLETED`
  uniquement**.

---

## 14. SQL aggregation

`OrderRepository.aggregateByStatus(userId, hasFrom, from, hasTo, to)` :

```sql
SELECT new com.converter.order.repository.OrderStatusAggregate(
    o.status, COUNT(o),
    COALESCE(SUM(o.amountXof), 0), COALESCE(SUM(o.amountCny), 0), COALESCE(SUM(o.feeXof), 0))
FROM Order o
WHERE o.userId = :userId
  AND (:hasFrom = false OR o.createdAt >= :from)
  AND (:hasTo   = false OR o.createdAt <  :to)
GROUP BY o.status
```

Une ligne par statut, calcul dans PostgreSQL — jamais un `findAll()` + somme Java. `COALESCE`
garantit `0` (pas `null`) pour un statut sans ordre.

---

## 15. Financial truth

`transferCount` et les totaux proviennent **exclusivement** des colonnes déjà figées d'`Order`
(copie du `Quote` au moment de sa création). Aucune dépendance de `BusinessPaymentReportService`
ni de `OrderHistoryService` vers `RateEngine` / `SettingsService` / tout pricing courant.
Republier une configuration de coût ou changer la marge **ne modifie jamais** un montant
historique. Prouvé par `summary_dateRange_excludesOrdersOutsideWindow` et par la construction
même des requêtes (aucun recalcul).

---

## 16. Refund treatment

- Un `Refund` **n'est jamais** un transfert : il n'entre dans aucun compteur
  (`transferCount`/`completedCount`/...) ni dans aucun total.
- `Order.status` ne connaît aucune valeur `REFUNDED` (invariant déjà acté Phase 3,
  `RefundService`) : un `COMPLETED + Refund PROCESSED` reste `COMPLETED` dans le reporting.
- Preuve : `summary_refundOnCompletedOrder_neverAltersTheTotals` — `completedCount`,
  `totalAmountXof`, `totalAmountCny`, `totalFeesXof` identiques avant/après remboursement.
- Le résumé actuel n'expose pas de métriques `refundCount`/`refundAmountXof` (non demandées
  explicitement, hors périmètre minimal) ; si ajoutées plus tard, elles devront rester des
  champs séparés, jamais fusionnées aux compteurs de transfert.

---

## 17. Concurrency

- `uq_business_profiles_user` (PostgreSQL) : garantit **un seul** `BusinessProfile` par
  utilisateur quelle que soit la concurrence applicative.
- Deux `PUT` concurrents → l'un réussit (`201`/`200`), l'autre soit réussit soit `409
  DUPLICATE_RESOURCE` (violation de contrainte convertie par `GlobalExceptionHandler`) —
  jamais un `5xx`, jamais deux lignes.
- Test dédié : `BusinessProfileHttpIT.twoConcurrentCreations_forSameUser_resultInExactlyOneProfile`
  (2 threads, assertion finale : exactement 1 ligne en base pour l'utilisateur). Vert.

---

## 18. Security

- Tous les endpoints exigent un JWT (`401` sinon — testé anonyme sur les 3 GET).
- `PUT /business-profile` : ownership implicite (aucun `userId` dans le corps).
- `GET /business/payments/summary` : `404` (jamais `403`) si pas de profil Business — aucune
  fuite d'existence.
- `GET /orders/history` : `userId` non optionnel dans la requête SQL — impossible de lire les
  ordres d'autrui via `supplierId`.
- Validation Bean Validation sur `UpsertBusinessProfileRequest` (`@NotBlank businessName`,
  `@NotNull businessType`, `@NotBlank country`, tailles) → `400 VALIDATION_ERROR`.

---

## 19. Rate limiting

- `RateLimitFilter` (par IP, en mémoire) couvre `POST /api/auth/login`, `POST /api/auth/register`
  et les `POST` d'écriture `/api/v1/quotes`, `/api/v1/orders`, `/api/v1/orders/*/payments`.
- Les endpoints Phase 8 sont : 3 × `GET` (profil, historique, résumé) + 1 × `PUT` (profil).
  **Aucun n'est soumis** au rate limiting.
- Les `GET` de reporting/historique sont des lectures authentifiées peu coûteuses (agrégation
  SQL `GROUP BY`, pagination bornée) — non prioritaires.
- `PUT /business-profile` (écriture) **n'est pas** couvert. **GAP signalé, non corrigé**
  unilatéralement (même statut que le gap `/suppliers/*/pay-again` de la Phase 4). À arbitrer
  dans le Final Backend Audit : étendre `RateLimitFilter` aux écritures Phase 8/4, ou
  documenter l'acceptation.

---

## 20. Tests ajoutés

| Classe | Tests | Nature |
|---|---|---|
| `BusinessProfileServiceTest` | 5 | Unitaire (Mockito) : `isBusinessUser` (2 cas), `get` 404, upsert création + audit, upsert mise à jour en place (jamais de 2ᵉ ligne) |
| `BusinessProfileHttpIT` | 12 | IT HTTP : 404 sans profil, 401 anonyme, création 201, mise à jour 200, `GET` après création, isolation par utilisateur, 3 × validation 400, audit création, audit mise à jour, concurrence (1 seule ligne) |
| `BusinessReportingHttpIT` | 5 | IT HTTP : 404 sans profil Business, 401 anonyme, comptes/totaux = états réels des ordres, remboursement n'altère pas les totaux, fenêtre de dates exclut hors période |
| `OrderHistoryHttpIT` | 10 | IT HTTP : pagination, filtres status/purpose/supplierId/dates, isolation cross-user via `supplierId`, tri fixe, vérité financière (montants figés), 401 anonyme |

**Total Phase 8 : 32 tests.**

---

## 21. Previous baseline

**405 tests, 0 échec, 0 erreur** (état validé à la fin de la Phase 7).

---

## 22. Final test count

Commande : `./mvnw -B clean test` (compile + unitaires + intégration Testcontainers, une seule
passe — pas de plugin Failsafe séparé dans ce projet).

```
[INFO] Results:
[INFO] Tests run: 437, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

**405 (baseline) + 32 (Phase 8) = 437.**

---

## 23. Failures

**0.**

---

## 24. Errors

**0.**

(Avant correction de reprise : 2 erreurs, toutes deux dans `BusinessProfileHttpIT`
— `put_create_recordsAuditEvent`, `put_update_recordsAuditEvent` — cause unique décrite §27.
Corrigées. Aucune autre classe n'a jamais échoué.)

---

## 25. Build result

**BUILD SUCCESS** — `mvn` exit code 0. `clean test` exécuté deux fois dans cette session de
reprise (avant correctif : `BUILD FAILURE`, 437 run / 2 errors ; après correctif :
`BUILD SUCCESS`, 437 run / 0 error).

`test-compile` seul également vérifié vert avant la passe complète.

---

## 26. Documentation

- `docs/ARCHITECTURE.md` — section **0.F** :
  - en-tête « Phases 1-7 » → « **Phases 1-8** », compteur migrations 20→25 → **20→26**,
    total tests suite complète noté (437).
  - nouveau point de liste « Phase 8 — profil professionnel, historique enrichi, reporting ».
  - nouvelle sous-section **« Phase 8 — profil professionnel, historique enrichi, reporting
    Business »** : `business_profiles`/PERSONAL-BUSINESS, `GET`/`PUT /business-profile`,
    `GET /orders/history`, `GET /business/payments/summary`, conventions de comptage,
    séparation `Refund`/`Order`, et **note dédiée sur la limitation
    `AuditLogRepository.search`** (voir §27).
- `docs/PHASE8_CLOSURE_REPORT.md` — le présent rapport.
- KYC : documenté comme **hors périmètre volontaire** (aucun statut de vérification, aucune
  vérification RCCM/fiscale) dans l'en-tête de `V26` et dans `ARCHITECTURE.md`.

---

## 27. Risks / limitations

### R1 — `AuditLogRepository.search` : type non inférable sous PostgreSQL (préexistant)

- **Constat** : `search` filtre chaque critère optionnel avec `(:p IS NULL OR col = :p)`. Sous
  le protocole étendu de PostgreSQL, le paramètre dont la seule occurrence exploitable est
  `$n IS NULL` (la borne `from`) n'a **pas de type inférable au moment du Parse** →
  `ERROR: could not determine data type of parameter $9`. Passer une valeur concrète ne
  corrige pas (échec au *parse*, avant le *bind*).
- **Portée réelle** : `GET /api/admin/audit-logs` (`AdminAuditLogController` → `AuditService.search`)
  est exposé et **échouerait en `500`** dès qu'il est appelé avec ce profil de paramètres. Ce
  chemin n'avait jamais été exercé par un test avant la Phase 8.
- **Traitement retenu (minimal, additif)** : ajout d'un finder dérivé
  `AuditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String, String)` — aucun
  paramètre optionnel, aucune modification de `search`. Les 2 tests d'audit de la Phase 8
  l'utilisent.
- **Reste à faire (hors périmètre Phase 8, à traiter dans le Final Backend Audit)** : migrer
  `search` vers le patron `hasXxx`/`xxx` (déjà employé par les Phases 5 et 8) ou `COALESCE`,
  avec un test couvrant l'endpoint admin. **Non fait ici** : `search` fait partie de
  l'infrastructure gelée, sa correction ripple sur `AuditService.search` + `AdminAuditLogController`
  et dépasse le périmètre de clôture Phase 8.

### R2 — Rate limiting non étendu aux écritures Phase 8

`PUT /api/v1/business-profile` (écriture) n'est pas couvert par `RateLimitFilter`. Gap signalé,
non corrigé unilatéralement (même statut que `/suppliers/*/pay-again`, Phase 4). Impact faible
(ressource singleton par utilisateur, `UNIQUE(user_id)` borne les effets d'un flood).

### R3 — `registration_number` non validé

`business_profiles.registration_number` est un simple `VARCHAR(60)` nullable : aucune
vérification de format RCCM ni d'unicité. Décision délibérée (pas de moteur KYC dans cette
phase). Si une conformité entreprise est requise plus tard : nouvelle migration + statut de
vérification + module dédié.

### R4 — Métriques Refund absentes du reporting

Le résumé n'expose pas `refundCount`/`refundAmountXof`. Non demandé explicitement ; si ajouté,
doit rester des champs **séparés** des compteurs de transfert.

---

## 28. FINAL BACKEND STATUS

- **Phase 8 : CLÔTURÉE.** Tous les items de la Definition of Done atteints : BusinessProfile +
  `UNIQUE(user_id)`, détection PERSONAL/BUSINESS encapsulée, `GET`/`PUT` profil, validation,
  ownership, audit, historique des ordres paginé/filtré, reporting Business avec agrégation
  SQL, intégrité des snapshots financiers, séparation `Refund`/`Order`, concurrence prouvée,
  sécurité, migration additive V26, documentation, **suite complète verte**.
- **Suite complète : 437 tests, 0 échec, 0 erreur, BUILD SUCCESS.** Résultat reproductible
  (`./mvnw -B clean test`).
- **Cœur financier : intact.** Aucune modification du pricing, du pipeline
  `Quote→Order→Payment→Settlement→Treasury→Refund`, de l'idempotence, des contraintes SQL du
  cœur, de `RateLimitFilter`, de `GlobalExceptionHandler`. Le seul fichier gelé approché
  (`AuditLogRepository`) l'a été par **ajout** d'un finder dérivé indépendant ; la méthode
  `search` existante n'a pas été touchée.
- **Migrations** : V1–V25 inchangées, V26 additive. Prochaine libre : **V27**.
- **Prochaine étape (mission séparée, NON démarrée)** : Final Backend Audit / Pre-production
  Hardening — traiter notamment R1 (`AuditLogRepository.search` + endpoint admin), R2 (rate
  limiting des écritures Phase 4/8), et la revue transverse security / invariants / concurrency
  / idempotency / DB constraints / Flyway / backup-restore / Docker / observability / logging /
  API consistency / performance / dependency security / production config.
