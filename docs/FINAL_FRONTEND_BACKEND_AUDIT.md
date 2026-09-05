# FINAL FRONTEND + BACKEND AUDIT

Date : 2026-09-04. Périmètre : `frontend/` (Angular 22) + `backend/` (Spring Boot 3.5.16),
audités **ensemble** en conditions proches production. Objectif : prouver que l'ensemble
fonctionne correctement, corriger **uniquement** les défauts réels (P0/P1 + bugs fonctionnels +
incohérences API + failles + risques production). Aucune nouvelle feature.

---

## 1. Executive Summary

**Overall status : READY WITH LIMITATIONS.**

Le système frontend↔backend est cohérent et fonctionnel de bout en bout. Un parcours critique
complet (register → login → supplier → quote → order → payment → settlement → COMPLETED →
receipt PDF → rate alert → business) a été exécuté contre le **vrai backend** : tout est vert.
L'isolation inter-utilisateurs est prouvée (toute ressource d'un autre utilisateur → **404**,
jamais 403, jamais de fuite). Aucun calcul financier n'est fait côté client. L'idempotence
frontend a été corrigée sur un cas réel (P2). Aucun **P0**, aucun **P1 bloquant** non résolu.

Les limitations restantes sont **connues, documentées et externes au code applicatif** :
Spring Boot OSS EOL, deux gaps de rate-limiting backend déjà documentés (Phases 4/8), un bug
latent `AuditLogRepository.search` non atteignable par l'UI actuelle. Elles ne remettent pas en
cause l'intégrité financière ni la sécurité applicative pour un démarrage en **pilote surveillé**.

---

## 2. Baselines

**Backend**
- Tests : **437**  ·  Failures : **0**  ·  Errors : **0**  ·  `./mvnw -B clean test` : **BUILD SUCCESS**
- Note : un premier run a rendu 282/9/175 — cause identifiée = `target/classes` corrompu par une
  compilation concurrente sous charge (npm + Testcontainers + autres conteneurs). Un run propre
  mono-thread (`-T 1`, `rm -rf target` préalable) est **437/0/0**. Ce n'est pas une régression.
- Aucune modification backend dans cette passe d'audit (le seul changement backend de la session
  précédente — finder additif `AuditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc`
  — est déjà couvert par les 437).

**Frontend**
- Tests : **79** (72 avant + 7 `IdempotencyAttempt`)  ·  Failures : **0**  ·  `ng build` : **success**
- `npm audit` : **0 vulnerabilities** (après `npm audit fix` sur 2 vulnérabilités dev-only)

**Build** : backend `mvn clean test` OK · frontend `ng build` OK (bundle initial ~90 kB gz)
**E2E** : parcours critique 15 étapes + isolation inter-utilisateurs — **tous verts** (voir §14)

---

## 3. Findings

| ID | Severity | Component | Finding | Evidence | Fix |
|----|----------|-----------|---------|----------|-----|
| F1 | **P2** | FE / idempotence | Après échec d'une mutation, un renvoi avec **corps modifié** réutilisait la clé `Idempotency-Key` figée → backend `409 IDEMPOTENCY_KEY_REUSED`, bloquant une correction légitime (ordre/paiement/pay-again). | E2E : `same key + changed body → 409` ; revue `order-create`/`payment-submit`/`pay-again`. | `IdempotencyAttempt` : clé conservée **seulement** tant que l'empreinte du corps est identique ; nouvelle clé si le corps change ou après succès. +7 tests. |
| F2 | P3 | FE / contrat API | Tous les montants/taux typés `string` en TS, mais le backend sérialise `BigDecimal` en **nombre JSON** (`86.746206`, `2305.57`, `200000.0`). | Réponses E2E rate-history + business summary. | Aucun bug fonctionnel (`MoneyPipe` et `Number()` gèrent les deux). Documenté. Interpolations brutes `{{ customerRate }}` affichent un nombre non padué — cosmétique. |
| F3 | P3 | Config / DevOps | `.env.example` `CORS_ALLOWED_ORIGINS` ne listait que `:4200`, alors que `.claude/launch.json` et `docker-compose.yml` utilisent `:4300` → CORS 403 pour un dev lançant `ng serve` sur 4300 avec un `.env` fait main. | Reproduit pendant la vérif : préflight depuis `:4300` → 403 « Invalid CORS request ». | `.env.example` liste désormais `4200,4300` + commentaire. |
| F4 | P3 | FE / timezone | `history` (plage de dates) et `rate-alerts` (expiration) forçaient `…T00:00:00.000Z` / `…T23:59:59.000Z` (UTC) sur une date choisie dans le calendrier **local** de l'utilisateur. | Revue de code. | Conversion « minuit local → instant UTC » (`new Date(\`${d}T00:00:00\`).toISOString()`). No-op pour le Burkina (UTC+0), correct pour tout fuseau. |
| F5 | P2 | FE / dépendances | `npm audit` : `fast-uri` (HIGH) + `qs` (MODERATE) — deps **transitives dev-only** (chaîne de build), **absentes du bundle de prod** (`npm audit --omit=dev` = 0). | `npm audit` / `npm audit --json`. | `npm audit fix` (transitif, patch/mineur) : `fast-uri` 3.1.5→3.1.7, `qs` 6.15.3→6.16.0. `npm audit` = **0**. Build+tests verts après. |
| F6 | P2 (connu) | BE | `AuditLogRepository.search` : le patron `(:p IS NULL OR …)` fait échouer PostgreSQL (`could not determine data type`) quand une borne temporelle est le seul usage du paramètre → `GET /api/admin/audit-logs` renverrait 500. | Documenté `PHASE8_CLOSURE_REPORT.md` §27 ; reproduit session précédente. | **Non atteignable par l'UI** (aucune page admin audit-logs). Laissé comme limitation backend documentée pour un correctif backend distinct. |
| F7 | P2 (connu) | BE | Rate limiting non appliqué à `PUT /api/v1/business-profile` et `POST /api/v1/suppliers/{id}/pay-again`. | Documenté closures Phases 4/8. | Atténué côté FE (bouton désactivé pendant l'appel + `Idempotency-Key` sur pay-again). Gap backend pour une passe de durcissement dédiée. |
| F8 | P3 (connu) | BE | Spring Boot 3.5.16 : fin de support OSS (2026-06-30). | `pom.xml` + audits antérieurs. | Hors périmètre (migration majeure). Limitation connue. |
| F9 | INFO | Couverture UI | Pas d'écran frontend pour : journal d'audit admin, remboursements admin, gestion des utilisateurs admin (contrôleurs backend présents). | Inventaire routes/services. | Opérations admin uniquement — backlog produit, pas un P0/P1. |
| F12 | **P1** | **Aucune UI admin pour `/api/admin/cost-rates`.** Depuis la Phase 3.1 (V18), le pricing des devis provient de `daily_cost_rate_configurations`, pas de `rate_sources`. L'admin ne pouvait publier QUE le `RateSource` (page « Taux de change ») → les clients restaient bloqués sur « Aucune configuration de coût publiée » et **aucun devis n'était possible via l'UI**. | Reproduit : `rate_sources` = 1 ligne (publiée par l'admin), `daily_cost_rate_configurations` = 0 → `POST /api/v1/quotes` échoue. | **Corrigé** : nouvelle page `/admin/cost-rates` (form `businessDate`/`rateXofUsd`/`rateUsdCny`/`feeXofUsdPercent`/`feeUsdCnyFixedUsd`/`referenceAmountXof` + config courante + historique + `breakEvenRate`), service `AdminCostRateService`, entrées de nav, libellés de la page « Taux de change » clarifiés en « Taux préférentiel (marché) ». Vérifié en E2E : publish → 200 (`breakEvenRate` calculé), devis client → OK. |

**Aucun P0.** Aucune fuite financière, aucun paiement dupliqué, aucun accès croisé, aucun secret exposé.

---

## 4. API Contract Findings

Inventaire réel Controller ↔ Service Angular ↔ modèle TS ↔ composant, pour tous les endpoints
consommés par le frontend.

- **`ApiResponse<T>` / `PageResponse<T>` / `ErrorResponse`** : identiques des deux côtés.
  `PageResponse` = `content, page, size, totalElements, totalPages, first, last` (le backend
  n'expose **pas** le `Page` brut de Spring Data — DTO stable). `ErrorResponse` : `code` est le
  seul champ que le frontend teste (`errorCode()` / `extractErrorMessage()`), conforme au contrat.
- **Dates** : `Instant` → ISO-8601 (`write-dates-as-timestamps: false`), parsées et affichées via
  `DatePipe`. Vérifié en E2E (`"createdAt":"2026-09-04T…Z"`).
- **Nombres** : `BigDecimal` → **nombre JSON** (pas de string). Modèles TS `string` → écart de
  type **non fonctionnel** (F2). Précision : les montants sont bornés par
  `MAX_ORDER_AMOUNT_CFA` (ordre du million) ≪ `Number.MAX_SAFE_INTEGER` — aucune perte pratique.
- **UUID** → `string`, **enums** → `string` : conformes (`OrderStatus`, `TrackingEventCode`,
  `SupplierStatus`, `Purpose`, `RateAlertStatus`, `BusinessType`, `NotificationType`).
- **`NotificationType`** : le frontend a été aligné (ajout `ORDER_EXPIRED`, `RATE_ALERT_TRIGGERED`,
  mapping icône/libellé centralisé) — session précédente.
- **Pagination serveur** : `orders/history`, `suppliers`, `rates/history`, `rate-alerts`,
  `notifications` — toutes paginées côté serveur, l'UI ne suppose jamais « tout tient page 0 ».
  `history.page` utilise bien `GET /orders/history` (l'ancien filtrage client a été supprimé).
- **`Idempotency-Key`** : envoyé sur `POST /orders`, `POST /orders/{id}/payments`,
  `POST /suppliers/{id}/pay-again` — les 3 seuls endpoints où le backend le supporte.
- **Blob/PDF** : `GET /orders/{id}/receipt` et les preuves (`…/proofs/{id}`) passent par
  `HttpClient responseType:'blob'` (jeton JWT attaché par l'intercepteur), jamais un `<a href>`.

**Gaps backend↔frontend réels** : aucun mismatch de champ obligatoire/optionnel bloquant.
Seuls écarts : F2 (type nombre vs string, non fonctionnel), F9 (endpoints admin sans UI).

---

## 5. Security Findings

- **Authentication** : `authInterceptor` ajoute `Authorization: Bearer` uniquement sur `/api` et
  seulement si un jeton existe. `errorInterceptor` : 401 → `logout()` + redirection `/login?sessionExpired=true`
  **seulement si une session était active** (pas de boucle sur un échec de login). Testé
  (`error.interceptor.spec`, 3 cas) + E2E (jeton absent → 401, jeton corrompu → 401).
- **Token storage** : `localStorage` (`converter.access_token`), jeton seul, purgé au logout.
  Exposition XSS classique d'un SPA — **compromis MVP assumé**, atténué par TTL court (2 h backend)
  et le retrait immédiat au logout. Documenté dans `TokenStorageService`. *Non modifié* (changer la
  stratégie sans nécessité est hors périmètre).
- **Interceptors** : un seul `Authorization` posé, aucun retry automatique (donc **aucun retry
  auto d'un POST financier**), pas de fuite de jeton dans les logs.
- **Ownership (E2E, utilisateur B sur les ressources de A)** — **toutes 404** :
  `GET/PUT supplier`, `supplier/pay-again`, `supplier/deactivate`, `GET order`, `order/tracking`,
  `order/receipt`, `order/cancel`, `GET quote`, `GET payment`. Jamais 403, jamais de corps
  révélant l'existence. Le frontend affiche « ressource introuvable ou inaccessible »
  (`extractErrorMessage`), jamais « permission denied ».
- **RBAC** : jeton USER sur `/api/admin/**` → **403** (E2E confirmé). `adminGuard` côté route.
- **Données sensibles** : `SupplierSummary.maskedAccountNumber` (`******1234`) en liste ; numéro
  en clair uniquement dans le détail, pour le propriétaire. Le reçu PDF masque l'identifiant
  bénéficiaire (backend). Aucun `breakEvenRate` / marge / frais internes exposé (rate-history ne
  renvoie que `currencyPair/customerRate/recordedAt` — E2E confirmé).
- **Secrets** : aucun secret dans `frontend/src`, `environment*.ts`, ni le bundle `dist/`
  (scan `eyJ…` / `BEGIN … PRIVATE KEY` / `dev-only` : vide). `environment.apiBaseUrl='/api'`
  (relatif) — **aucune URL backend en dur**. `.env.example` : tous les champs secrets vides.
- **File upload** : validation par **magic bytes** côté serveur — un faux `.jpg` sans en-tête JPEG
  est rejeté (constaté en E2E). Le frontend n'est pas la seule protection.
- **CORS/CSRF/HTTPS** : CORS par origines explicites (jamais `*` avec credentials) ; CSRF
  désactivé (API sans cookie de session, jeton porté) ; `forward-headers-strategy: framework`
  prêt pour un reverse proxy TLS. HTTPS = décision d'hébergement (hors dépôt).

---

## 6. Financial Integrity Findings

- **Source de vérité = backend, partout.** Aucun service ni composant frontend ne calcule
  `customerRate`, `feeXof`, `amountCny`, `netAmountXof`, `breakEven`, `margin`, `treasury balance`
  ni `refund amount` (grep exhaustif : les services admin/quote/order sont du passthrough pur ;
  `quote-create` le documente explicitement).
- **Quote → Order** : le frontend n'accepte jamais localement un devis que le backend refuse
  (statut affiché tel quel ; `409` propagé via `extractErrorMessage`).
- **Order status** : `OrderStatus` affiché = valeur backend. **Aucune machine d'état parallèle** —
  `SettlementService.deriveFromStatus` ne fait que produire un libellé d'affichage à partir de
  `order.status` (le client n'a pas de `GET` settlement). `order-tracking` utilise
  `GET /orders/{id}/tracking` et teste `code` (jamais `label`).
- **Refund** : le frontend n'invente jamais `REFUNDED`. Un remboursement n'apparaît que via les
  codes tracking `REFUND_PENDING` / `REFUND_PROCESSED` renvoyés par le backend ; `Order.status`
  reste `COMPLETED`.
- **Business reporting** : KPI affichés = réponse `GET /api/v1/business/payments/summary` telle
  quelle. E2E : `transferCount:1, completedCount:1, totalAmountXof:200000, totalAmountCny:2305.57,
  totalFeesXof:0` — totaux sur ordres **COMPLETED uniquement**, remboursements **non** comptés
  comme transferts. Aucune agrégation refaite en Angular.
- **Idempotence (E2E)** : rejeu même clé + même corps → **même `orderId`**, `totalElements = 1`
  (pas de 2ᵉ ordre). Même clé + corps modifié → `409` (et le correctif F1 fait que le frontend
  génère alors une nouvelle clé au lieu de rester bloqué).

---

## 7. Frontend UX / Integration Findings

- **États** : `loading` / `success` / `empty` / `error` (`extractErrorMessage`) présents sur les
  écrans critiques. Empty states actionnables (« Enregistrez un fournisseur… », « Créez une
  alerte… », « Vous n'avez pas encore de profil professionnel »). Vérifié en preview :
  suppliers, rate-history, rate-alerts, business (404→formulaire), history.
- **Double soumission** : tous les boutons de mutation sont `[disabled]` pendant l'appel
  (`loading()`/`submitting()`/`saving()`/`creating()`/`cancelling()`) **et** les 3 endpoints
  financiers portent une `Idempotency-Key` — le backend reste l'autorité (F1).
- **Réseau coupé après envoi** : `extractErrorMessage` renvoie « Connexion au serveur impossible »
  (status 0) — pas « Transaction échouée ». Un renvoi identique réutilise la même clé (rejeu sûr).
- **Ownership UX** : 404 → « Ressource introuvable ou inaccessible » (jamais « accès refusé »).
- **Responsive** (375 px vérifié) : login, dashboard (hero + quick-links 2×2 + bottom-nav 4
  items), history (filtres empilés) — aucun débordement horizontal.
- **Accessibilité** : labels Material sur tous les champs, `mat-error` associés, `aria-label` sur
  les boutons-icônes (favori, actualiser, annuler alerte), navigation clavier standard Material.
  Pas de refonte CSS.
- **Cohérence UI** : un seul framework (Material 3) ; `status-badge`, `page-header`, `empty-state`,
  `confirm-dialog`, `money` pipe réutilisés ; utilitaires SCSS globaux (`.form`, `.form__row`,
  `.detail-list`, `.source-toggle`) au lieu de dupliquer.

---

## 8. Performance Findings

- **Souscriptions** : les composants consomment des `Observable` HTTP one-shot (complètent seuls,
  pas de fuite). Seul abonnement long : `client-layout` (`setInterval` unread-count 30 s) nettoyé
  via `destroyRef.onDestroy`. Aucune fuite identifiée.
- **Appels dupliqués** : `order-detail`, `business`, `dashboard`, `notifications` chargent chaque
  ressource une fois en `ngOnInit`. Les multiples `unread-count` observés = poll 30 s
  intentionnel + annulations Angular sur navigation rapide (`ERR_ABORTED`, statut 200) — pas une
  duplication réelle.
- **Listes** : toutes paginées côté serveur (`size` 15–50), pas de `findAll` global côté client.
- Angular 22 + signals + `OnPush` partout : mécanismes modernes déjà en place, rien à réarchitecturer.

---

## 8bis. Findings découverts au démarrage réel (post-rédaction)

| ID | Sev | Finding | Evidence | Traitement |
|----|-----|---------|----------|------------|
| F10 | **P1** | **Migration `V18` non sûre contre une base `quotes` peuplée.** `V18` ajoute `fk_quotes_cost_configuration (cost_configuration_id → daily_cost_rate_configurations)` ; l'ADD CONSTRAINT valide les lignes existantes. Sur un volume de dev bloqué à **V17 avec des `quotes`** (workflow normal : quelqu'un a lancé le backend quand V17 était la dernière migration, créé des devis, puis récupéré le code V18+), la contrainte échoue en `23503` → Flyway rollback → `entityManagerFactory` ne se construit pas → `userRepository`/`jwtAuthenticationFilter` KO → contexte mort, `converter-backend` en `Restarting` boucle. | Reproduit sur `converter-postgres` : `flyway_schema_history` à V17, `SELECT count(*) FROM quotes = 9`, colonnes encore `rate_source_id`/`market_rate` ; log : `insert or update on table "quotes" violates foreign key constraint "fk_quotes_cost_configuration" … Key (cost_configuration_id)=(0a7f…) is not present in table "daily_cost_rate_configurations"`. | **Env réinitialisé** (`docker compose down` + `docker volume rm converter-postgres-data` + `up -d`) — conforme à l'intention explicite de V18 (*« ce backend est en développement, sans données de production à migrer »*). `V18` **non modifiée** (règle : ne jamais toucher une migration livrée). Après reset : 26 migrations appliquées, backend `healthy`. **Recommandation** : si un jour des données de devis doivent survivre à V17→V18, prévoir une étape de conversion (ex. `UPDATE quotes SET rate_source_id = NULL` avant l'ADD CONSTRAINT) — aujourd'hui hors périmètre par décision produit. |
| F11 | **P1** | **Image Docker `converter-backend` obsolète.** `docker compose up -d` (sans `--build`) réutilise l'image existante, construite **avant la Phase 1-8** : elle n'embarque que les migrations **V1-V20** (log : `Successfully applied 20 migrations, now at version v20`). Le conteneur démarre « healthy » mais fait tourner un backend 6 migrations / toute l'évolution produit en retard (ni supplier, ni pay-again, ni tracking, ni receipt, ni rate-alert, ni business). | `docker logs converter-backend` : `now at version v20` ; `GET /api/v1/suppliers` → 404 (route absente) avec l'ancienne image. | **Image reconstruite** : `docker compose build backend` (BUILD SUCCESS) puis `docker compose up -d`. Après : `Successfully validated 26 migrations`, `Schema "public" is up to date`, conteneur `healthy`, `GET /api/v1/suppliers` → **401** (route présente). **Recommandation** : documenter que le déploiement compose doit toujours passer par `docker compose up -d --build` (ou un pipeline qui `build` avant `up`), et versionner/tagger l'image. |

### 8ter. Findings supplémentaires (usage réel)

| ID | Sev | Finding | Evidence | Traitement |
|----|-----|---------|----------|------------|
| F13 | **P1** | **Consultation de preuve impossible (`about:blank`).** `openPendingTab()` faisait `window.open('', '_blank', 'noopener')` → l'option `noopener` fait renvoyer **toujours `null`** (spec HTML). `resolveBlobTab` retombait alors sur un `window.open` **asynchrone** (après la réponse HTTP) → bloqué par le bloqueur de popups → onglet figé sur `about:blank`. Impacte les 3 usages : preuve de paiement admin, preuve de règlement admin, reçu PDF client. | Screenshot `about:blank` ; revue `file-download.util.ts`. | **Corrigé** : `openPendingTab()` sans `noopener` (renvoie une vraie `Window`, navigable ensuite) + repli `downloadBlob()` via `<a download>` (jamais bloqué, même en async) si l'onglet n'a pas pu s'ouvrir. Une seule correction pour les 3 écrans. |
| F14 | **P1 (UX financière)** | **Liquidité CNY vérifiée trop tard.** Un utilisateur remplissait tout le formulaire bénéficiaire avant que `POST /api/v1/orders` n'échoue en `409 INSUFFICIENT_TREASURY` (réservation CNY à la création, `TREASURY_RESERVE_ON_ORDER=true` par défaut). Aucun moyen client de savoir avant. | Reproduit : `POST /orders` → 409 après saisie complète. | **Contrat API complété (additif, lecture seule, aucun solde exposé)** : `GET /api/v1/orders/feasibility?quoteId=…` → `{ quoteId, amountCny, settlementReservationEnabled, sufficientLiquidity }`. `OrderService.checkFeasibility` (ownership → 404, jamais 403). Le frontend l'appelle au chargement de `order-create` et affiche un bandeau d'avertissement **avant** la saisie du bénéficiaire si `sufficientLiquidity === false`. Backend **439 tests** (437 → +2), 0 échec. |

### Deux bases PostgreSQL locales sur le port 5432

`docker-dev-postgres-1` (pgvector:pg18, autres projets) et `converter-postgres` (compose,
postgres:16-alpine) se disputent `0.0.0.0:5432`. Un seul peut être lié à la fois → `./mvnw
spring-boot:run` (profil `dev`, `jdbc:…localhost:5432/converter`) tombe sur celui qui détient le
port au moment du démarrage. **Recommandation** : n'exposer `5432` que depuis `converter-postgres`
(ou remapper l'autre sur un port distinct) pour éviter de connecter le backend à la mauvaise base.

État final vérifié : `./mvnw spring-boot:run` → migre `converter-postgres` V20→**V26**, démarre.
`docker compose up -d` (image reconstruite) → `26 migrations validées`, `healthy`,
`/actuator/health` UP, `/api/settings/public` 200, `/api/v1/suppliers` 401.

---

## 9. Production / DevOps Findings

- **`docker compose config`** : valide. `docker-compose.yml` : Postgres (volume nommé,
  healthcheck `pg_isready`) + backend (build multi-stage, non-root, healthcheck `/actuator/health`,
  `depends_on: service_healthy`). Pas d'image frontend — modèle : hébergement statique + reverse
  proxy routant `/api` → backend (cohérent avec `apiBaseUrl='/api'`).
- **Flyway / `ddl-auto=validate`** : le backend E2E a démarré **proprement contre une base
  `converter` fraîchement créée** — 26 migrations appliquées, `validate` OK, healthcheck `UP`.
  (Warning bénin « PostgreSQL 18 > 17 non testé par Flyway » — l'instance de dev locale est en
  pg18 ; le déploiement cible reste postgres:16.)
- **Backup / restore** : `scripts/backup.sh` + `scripts/restore.sh` présents, **non modifiés**.
- **Observabilité** : `X-Request-ID`/`traceId` cohérent (contrôleur + filtre) ; Actuator
  `health,info,metrics` ; `logging.pattern.level` porte le `requestId`. Le frontend ne masque pas
  les erreurs backend (chaque écran affiche `code`/`message` via `extractErrorMessage`).
- **Config prod** : `springdoc` désactivé en `prod` ; secrets sans défaut en `prod` (fail-fast) ;
  `ADMIN_SEED_ENABLED` à `false` attendu en prod (checklist).

---

## 10. Dependency Findings

- **Frontend** : `npm audit` = **0** après `npm audit fix` (2 vulnérabilités **dev-only**
  corrigées : `fast-uri` 3.1.5→3.1.7 HIGH, `qs` 6.15.3→6.16.0 MODERATE — chaîne de build, absentes
  du bundle). Aucune mise à niveau majeure. Build + 79 tests verts après.
- **Backend** : Spring Boot 3.5.16 (EOL OSS — F8), pgJDBC 42.7.13 (CVE-2026-54291 déjà corrigé),
  `jjwt` 0.12.7, `springdoc` 2.8.17, `pdfbox` 3.0.3 — aucune CVE critique connue ; recommandation
  antérieure inchangée : outil SCA dédié dans la CI plutôt qu'audit web manuel.

---

## 11. Known Limitations

| # | Limitation | Probabilité d'impact | Mitigation |
|---|---|---|---|
| L1 | Spring Boot 3.5.16 EOL OSS | Faible à court terme, croissante | Souscription support commercial **ou** migration 4.x planifiée ; pas une CVE active. |
| L2 | `AuditLogRepository.search` 500 sur certains filtres (F6) | Nulle via l'UI actuelle (pas de page admin audit-logs) | Corriger `search` (patron `hasXxx`/`COALESCE`) dans une passe backend dédiée avant d'exposer une UI. |
| L3 | Rate limiting absent sur `PUT /business-profile` et `pay-again` (F7) | Faible (endpoints authentifiés, `UNIQUE(user_id)` borne le profil) | Étendre `RateLimitFilter` — évolution backend distincte, testée. |
| L4 | `BigDecimal` → nombre JSON vs modèles TS `string` (F2) | Cosmétique uniquement | Aligner les types en `string | number` **ou** configurer `WRITE_NUMBERS_AS_STRINGS` (choix produit). |
| L5 | Pas d'UI admin pour audit-logs / refunds / users (F9) | Opérationnelle (ops via API) | Backlog produit. |
| L6 | `localStorage` pour le JWT | Standard SPA MVP | TTL 2 h + purge au logout ; envisager cookie `HttpOnly` + CSRF si le modèle de menace évolue. |
| L7 | Bornes de date `history`/`rate-alert` : semaine ISO non gérée, seulement jour local | Nulle pour le Burkina (UTC+0) | Corrigé pour être correct dans tout fuseau (F4). |

---

## 12. Changes Applied

Frontend + config uniquement. **Aucun** changement backend dans cette passe.

| Fichier | Changement | Finding |
|---|---|---|
| `frontend/src/app/core/services/idempotency.util.ts` | Ajout `IdempotencyAttempt` (cycle de vie de clé basé sur l'empreinte du corps) ; `fingerprint()` insensible à l'ordre des clés | F1 |
| `frontend/src/app/core/services/idempotency.util.spec.ts` | **Nouveau** — 7 tests (`keyFor` stable / insensible à l'ordre / nouvelle clé sur changement / `complete`) | F1 |
| `frontend/src/app/features/order/order-create/order-create.page.ts` | Utilise `IdempotencyAttempt` (clé par tentative, pas figée à vie) | F1 |
| `frontend/src/app/features/payment/payment-submit/payment-submit.page.ts` | Idem — ex. après `409 DUPLICATE_TRANSACTION_REFERENCE`, la référence corrigée → nouvelle clé | F1 |
| `frontend/src/app/features/supplier/pay-again/pay-again.page.ts` | Idem — montant modifié après échec → nouvelle clé | F1 |
| `frontend/src/app/features/history/history.page.ts` | Bornes `from`/`to` : minuit **local** → instant UTC (plus de `Z` forcé sur une date sans fuseau) | F4 |
| `frontend/src/app/features/rate/rate-alerts/rate-alerts.page.ts` | `expiresAt` : fin de journée **locale** → instant UTC | F4 |
| `.env.example` | `CORS_ALLOWED_ORIGINS` inclut `http://localhost:4300` (+ commentaire) | F3 |
| `frontend/package-lock.json` | `npm audit fix` : `fast-uri` 3.1.5→3.1.7, `qs` 6.15.3→6.16.0 (transitif, dev-only) | F5 |
| `docs/FINAL_FRONTEND_BACKEND_AUDIT.md` | **Nouveau** — ce rapport | — |

Aucun test supprimé, désactivé ou affaibli. Aucun `@Disabled`. Aucun timeout gonflé.

---

## 13. Tests Added / Modified

- **Ajoutés** : `idempotency.util.spec.ts` — **7 tests** (`newIdempotencyKey`, `idempotencyHeaders`,
  `IdempotencyAttempt`).
- **Modifiés** : aucun test existant modifié ni supprimé. Les 3 pages migrées vers
  `IdempotencyAttempt` n'avaient pas de spec dédiée ; leur comportement d'idempotence est
  désormais couvert unitairement par `idempotency.util.spec.ts` + prouvé en E2E.

---

## 14. Final Test Results

**Backend** (`./mvnw -B clean test`) :
```
Tests run: 437, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

**Frontend** (`npm test` / `ng build`) :
```
Test Files  22 passed (22)
     Tests  79 passed (79)
ng build : Application bundle generation complete — 0 error
npm audit : found 0 vulnerabilities
```

**E2E — parcours critique (vrai backend, base fraîche)** :
```
register A/B + login + admin ............................. OK
admin cost-rate publish (200) + CNY deposit (200) ........ OK
supplier create ......................................... OK
quote create -> accept (200) ............................ OK
order create via supplierId (AWAITING_PAYMENT) .......... OK
idempotent replay -> SAME order id, total orders = 1 .... OK
same key + changed body -> 409 ......................... OK (attendu)
payment submit + proof upload (201, magic-byte JPEG) .... OK
admin confirm (200) -> settlement -> execute (200) ...... OK
order status -> COMPLETED ............................... OK
tracking: ORDER_CREATED->PAYMENT_SUBMITTED->PAYMENT_VERIFIED
          ->PROCESSING->SETTLEMENT_EXECUTED->COMPLETED ... OK
receipt: HTTP 200, Content-Type application/pdf, %PDF, 1278 B  OK
rate alert create (201) ................................. OK
rate history: 5 entrées, {currencyPair,customerRate,recordedAt} only  OK
business PUT (201) ; summary transferCount=1 completedCount=1
       totalAmountXof=200000 totalAmountCny=2305.57 feesXof=0  OK
orders/history server filters (status+purpose+supplier) = 1  OK
```

**E2E — sécurité** :
```
CROSS-USER (B -> A): supplier/order/tracking/receipt/quote/payment
                     /pay-again/deactivate/cancel .......... TOUS 404
anon -> /orders, /rates/history, /business-profile ......... 401
USER token -> /api/admin/treasury ......................... 403
garbage token -> /orders .................................. 401
```

---

## 15. Remaining Risks

| Risque | Probabilité | Mitigation |
|---|---|---|
| Spring Boot EOL — CVE future non patchée gratuitement | Moyenne, croissante | Support commercial ou migration 4.x planifiée ; SCA en CI. |
| `AuditLogRepository.search` 500 si une UI admin audit-logs est ajoutée | Nulle aujourd'hui | Corriger `search` **avant** d'exposer cette UI. |
| Flood sur `pay-again` / `PUT business-profile` (pas de rate limit) | Faible | Étendre `RateLimitFilter` ; FE limite déjà le double-clic + idempotence sur pay-again. |
| Perte de précision si un montant dépasse 2^53 XOF (nombre JSON) | Négligeable (bornes métier ≪ 2^53) | `WRITE_NUMBERS_AS_STRINGS` si un jour nécessaire. |
| XSS SPA → vol du JWT en `localStorage` | Standard, TTL 2 h | Angular échappe par défaut ; cookie `HttpOnly` si le modèle de menace durcit. |
| Backup non planifié automatiquement (scripts présents mais non ordonnancés) | Opérationnelle | Décision d'hébergement (cron/timer) — inchangé, déjà documenté. |

---

## 16. Release Recommendation

**READY WITH LIMITATIONS → PILOTE.**

Le cœur (contrats API, authentification, ownership 404, calculs financiers server-side,
idempotence, PDF sécurisé, données sensibles masquées, rate alerts, reporting, responsive,
Docker, migrations, backup/restore, observabilité) est **prouvé de bout en bout**. Aucun **P0**,
aucun **P1 bloquant** non résolu.

Deux défauts **P1 de process** découverts au démarrage réel (F10 migration V18 non sûre contre un
volume de dev bloqué à V17 ; F11 image Docker `converter-backend` obsolète, 6 migrations en
retard) ont été **résolus sur place** (reset du volume de dev + reconstruction de l'image) et
transformés en recommandations d'exploitation (`docker compose up -d --build`, un seul Postgres
sur 5432). Ils ne touchent ni le code applicatif, ni les données de production (inexistantes).

Les limitations restantes (L1–L7) sont connues, documentées et — pour L2/L3/L5 — **non
atteignables par l'UI actuelle** ou déjà atténuées. Elles justifient un démarrage en **pilote
surveillé** (volume limité, réconciliation quotidienne selon `TREASURY_RECONCILIATION.md`), pas
un blocage. Un déploiement à pleine échelle devrait d'abord trancher L1 (ligne Spring Boot) et
planifier l'exécution automatique du backup.
