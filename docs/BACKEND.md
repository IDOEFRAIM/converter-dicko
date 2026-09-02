# Converter — Documentation backend détaillée

> **Portée.** Ce document décrit **exclusivement le backend** (`backend/`), tel qu'il est
> réellement implémenté au **2026-09-02** (après les passes de durcissement 1 et 2 —
> voir [FINANCIAL_INVARIANTS.md](FINANCIAL_INVARIANTS.md) et
> [AUDIT_BUSINESS_LOGIC_PASS2.md](AUDIT_BUSINESS_LOGIC_PASS2.md)). Il fait référence pour tout ce qui touche au
> code Java, au schéma PostgreSQL, aux endpoints REST et à la configuration.
> Pour la vision produit, le raisonnement de conception et l'historique des décisions,
> voir [ARCHITECTURE.md](ARCHITECTURE.md).
>
> **État réel vs. ARCHITECTURE.md.** Le document d'architecture a été rédigé *avant* le
> code des modules métier et décrit une roadmap par phases. Cette roadmap est **dépassée** :
> tous les modules `rate`, `quote`, `order`, `payment`, `settlement`, `treasury`, plus
> `wallet`, `preferredrate` et `notification` (non prévus initialement) sont construits et
> testés. Les écarts entre la conception et le code livré sont recensés en
> [§20 Écarts connus & dette technique](#20-écarts-connus--dette-technique).

---

## Table des matières

1. [Vue d'ensemble](#1-vue-densemble)
2. [Build, outillage et dépendances](#2-build-outillage-et-dépendances)
3. [Configuration applicative](#3-configuration-applicative)
4. [Démarrage, profils et amorçage administrateur](#4-démarrage-profils-et-amorçage-administrateur)
5. [Conteneurisation](#5-conteneurisation)
6. [Organisation des packages](#6-organisation-des-packages)
7. [Socle commun (`common`)](#7-socle-commun-common)
8. [Sécurité (`security`)](#8-sécurité-security)
9. [Schéma de base de données](#9-schéma-de-base-de-données)
10. [Module `rate` — cotations et moteur de tarification](#10-module-rate)
11. [Module `quote` — devis](#11-module-quote)
12. [Module `order` — ordres](#12-module-order)
13. [Module `payment` — paiements XOF](#13-module-payment)
14. [Module `settlement` — règlements CNY](#14-module-settlement)
15. [Module `treasury` — trésorerie](#15-module-treasury)
16. [Module `storage` — stockage de fichiers](#16-module-storage)
17. [Modules `wallet`, `preferredrate`, `notification`](#17-modules-wallet-preferredrate-notification)
18. [Modules transverses (`user`, `auth`, `settings`, `audit`, `admin`)](#18-modules-transverses)
19. [Planificateurs, concurrence, calcul monétaire](#19-planificateurs-concurrence-calcul-monétaire)
20. [Écarts connus & dette technique](#20-écarts-connus--dette-technique)
21. [Référence complète des endpoints REST](#21-référence-complète-des-endpoints-rest)
22. [Tests](#22-tests)
23. [Conventions de code](#23-conventions-de-code)

---

## 1. Vue d'ensemble

| Élément | Valeur |
|---|---|
| Langage | Java 21 (LTS, `maven.compiler.release=21`) |
| Framework | Spring Boot **3.5.16** (parent `spring-boot-starter-parent`) |
| Build | Maven via **Maven Wrapper** (`mvnw`), distribution Maven 3.9.16 |
| Base de données | PostgreSQL 16 (image `postgres:16-alpine`) |
| Migrations | Flyway, `V1` → `V16`, `validate-on-migrate=true` |
| ORM | Spring Data JPA / Hibernate, `ddl-auto=validate` (Flyway seul maître du schéma) |
| Mapping DTO | MapStruct 1.6.3 (+ Lombok 0.2.0 binding) — *utilisé uniquement pour `UserMapper`* ; ailleurs, mapping manuel dans les services |
| Sécurité | Spring Security + JWT HS256 (`io.jsonwebtoken` 0.12.7), BCrypt coût 12 |
| Cache | Caffeine (paramètres métier + compteur anti-brute-force) |
| Doc API | springdoc-openapi 2.8.17 (`/swagger-ui.html`, `/v3/api-docs`) |
| Supervision | Spring Boot Actuator (`/actuator/health`, `/actuator/info`) |
| Tests | JUnit 5, Mockito, Testcontainers (PostgreSQL jetable) — **183** méthodes `@Test` |
| Groupe / artefact | `com.converter` / `converter-backend` `0.1.0-SNAPSHOT` |

**Principes structurants (invariants du code) :**

- **Monolithe modulaire, package-by-feature.** Une base PostgreSQL unique, des transactions ACID partagées. Pas de microservices.
- **`BigDecimal` exclusivement** pour tout montant ; `NUMERIC` en base. Aucun `double`/`float`.
- **Temps en UTC de bout en bout.** `TimeZone.setDefault(UTC)` dans `main()`, colonnes `TIMESTAMPTZ`, `Instant` côté Java, `Clock` injectable pour les fenêtres temporelles testables.
- **Aucun décaissement automatisé.** Le backend orchestre un *workflow administratif* ; les mouvements réels (Mobile Money, virements Chine) restent manuels et déclarés.
- **Stateless.** Aucune session HTTP ; chaque requête porte son JWT ; le statut du compte est **revalidé en base à chaque requête**.
- **Défense en profondeur.** Chaque invariant financier est gardé à la fois par le code applicatif (verrou pessimiste + machine d'état) *et* par une contrainte SQL (CHECK, index unique partiel).
- **Enums persistés en `VARCHAR` + `CHECK`**, jamais en `ORDINAL` ni en type `ENUM` PostgreSQL.

---

## 2. Build, outillage et dépendances

### 2.1 `pom.xml`

**Dépendances de production :**

| Dépendance | Rôle |
|---|---|
| `spring-boot-starter-web` | REST, Jackson, Tomcat embarqué |
| `spring-boot-starter-validation` | Bean Validation (Hibernate Validator) |
| `spring-boot-starter-data-jpa` | JPA / Hibernate |
| `flyway-core` + `flyway-database-postgresql` | Migrations (le module PostgreSQL est séparé depuis Flyway 10) |
| `postgresql` (runtime) | Pilote JDBC |
| `spring-boot-starter-security` | Filtres, `SecurityFilterChain`, `@PreAuthorize` |
| `jjwt-api` / `jjwt-impl` / `jjwt-jackson` `0.12.7` | Émission / vérification JWT |
| `caffeine` | Cache en mémoire (version gérée par le BOM) |
| `mapstruct` `1.6.3` | Génération de mappers (annotation processor) |
| `lombok` (optional) | Réduction de boilerplate |
| `springdoc-openapi-starter-webmvc-ui` `2.8.17` | Swagger UI + OpenAPI 3 |
| `spring-boot-starter-actuator` | Sondes de santé |

**Dépendances de test :** `spring-boot-starter-test`, `spring-security-test`, `testcontainers:junit-jupiter`, `testcontainers:postgresql`, `spring-boot-testcontainers`.

### 2.2 Plugins Maven

- **`maven-compiler-plugin`** — `annotationProcessorPaths` dans l'ordre **significatif** : `lombok` → `lombok-mapstruct-binding` → `mapstruct-processor` (sinon MapStruct ne voit pas les accesseurs Lombok et génère des mappers vides). `compilerArgs` : `-parameters`, `-Amapstruct.defaultComponentModel=spring`, `-Amapstruct.unmappedTargetPolicy=ERROR`.
- **`spring-boot-maven-plugin`** — exclut Lombok du jar final.
- **`maven-surefire-plugin`** — `includes` : `**/*Test.java` **et** `**/*IT.java`. Une seule commande `./mvnw test` exécute unitaires + intégration ; **pas de plugin Failsafe séparé**.

### 2.3 Commandes

```bash
cd backend
./mvnw test              # tests unitaires + intégration (Docker requis pour Testcontainers)
./mvnw spring-boot:run   # démarrage local (profil dev par défaut)
./mvnw clean package -DskipTests   # jar exécutable
```

---

## 3. Configuration applicative

### 3.1 `application.yml` (commun à tous les profils)

| Clé | Valeur | Note |
|---|---|---|
| `spring.profiles.default` | `dev` | profil si aucun n'est actif |
| `spring.datasource.url` | `${DB_URL:jdbc:postgresql://localhost:5432/converter}` | |
| `spring.datasource.username` | `${DB_USERNAME:converter}` | |
| `spring.datasource.password` | `${DB_PASSWORD:}` | **vide** par défaut (pas de secret deviné) |
| `spring.datasource.hikari.pool-name` | `converter-pool` | |
| `spring.datasource.hikari.maximum-pool-size` | `${DB_POOL_SIZE:10}` | 20 en prod |
| `spring.datasource.hikari.connection-timeout` | `10000` | |
| `spring.jpa.hibernate.ddl-auto` | **`validate`** | Flyway seul maître ; divergence entité/migration = échec au démarrage |
| `spring.jpa.open-in-view` | `false` | pas de session Hibernate ouverte pendant le rendu |
| `spring.jpa.properties.hibernate.jdbc.time_zone` | `UTC` | |
| `spring.jpa.properties.hibernate.jdbc.batch_size` | `25` | + `order_inserts`/`order_updates` |
| `spring.flyway.enabled` | `true` | `locations=classpath:db/migration`, `baseline-on-migrate=false`, `validate-on-migrate=true` |
| `spring.jackson.default-property-inclusion` | `non_null` | |
| `spring.jackson.serialization.write-dates-as-timestamps` | `false` | dates ISO-8601 |
| `spring.servlet.multipart.max-file-size` | `10MB` | plafond servlet ; le plafond métier réel est `MAX_PROOF_FILE_SIZE_BYTES` (5 Mo) |
| `spring.servlet.multipart.max-request-size` | `12MB` | |
| `server.port` | `${SERVER_PORT:8080}` | |
| `server.error.include-stacktrace` | `never` | aucune stack trace vers le client |
| `server.error.include-message` | `never` | |
| `server.forward-headers-strategy` | `framework` | derrière reverse proxy de confiance (lecture `X-Forwarded-For`) |
| `server.shutdown` | `graceful` | |
| `management.endpoints.web.exposure.include` | `health,info` | |
| `management.endpoint.health.probes.enabled` | `true` | `show-details: never` |
| `springdoc.api-docs.path` | `/v3/api-docs` | Swagger UI : `/swagger-ui.html` |
| `app.jwt.secret` | `${JWT_SECRET:}` | **obligatoire** ; validé `@NotBlank` |
| `app.jwt.expiration-minutes` | `${JWT_EXPIRATION_MINUTES:120}` | 2 h |
| `app.jwt.issuer` | `converter-api` | claim `iss` exigé à la vérification |
| `app.cors.allowed-origins` | `${CORS_ALLOWED_ORIGINS:http://localhost:4200}` | liste (virgules), jamais `*` |
| `app.login-protection.max-attempts` | `${LOGIN_MAX_ATTEMPTS:5}` | |
| `app.login-protection.lock-duration-minutes` | `${LOGIN_LOCK_MINUTES:15}` | |
| `app.storage.root-dir` | `${STORAGE_ROOT_DIR:./storage}` | hors racine web |
| `app.admin-seed.*` | voir §4 | |
| `logging.level.com.converter` | `INFO` (`DEBUG` en dev, `INFO` en prod) | |

### 3.2 `application-dev.yml`

- `DB_PASSWORD` par défaut `converter_local_dev`.
- `app.jwt.secret` par défaut : `dev-only-insecure-signing-key-please-override-in-production` (≥ 32 octets, publique par construction).
- `app.admin-seed.enabled` par défaut `true`, `phone` `+22500000000`, `password` **vide** (mot de passe aléatoire généré et affiché une fois dans les logs).
- `format_sql: true`, `org.hibernate.SQL: DEBUG`.

### 3.3 `application-prod.yml`

- **Aucune valeur par défaut pour les secrets.** `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` proviennent tous de l'environnement — absence ⇒ **échec au démarrage** (`@Validated` + `@NotBlank` sur `JwtProperties`).
- `app.admin-seed.enabled` par défaut `false` ; si `true`, `ADMIN_PASSWORD` est **obligatoire** (un mot de passe généré est refusé en prod).
- Pool Hikari : 20 connexions. `logging.level.root: WARN`.

### 3.4 `application-test.yml` (`src/test/resources`)

- `app.jwt.secret` : clé de test locale ; `admin-seed.enabled: false`.
- `spring.datasource.url` **écrasé à chaud** par Testcontainers via `@ServiceConnection`.

### 3.5 Classes `@ConfigurationProperties` (`config/props/`)

| Record | Préfixe | Champs / règles |
|---|---|---|
| `JwtProperties` | `app.jwt` | `secret` (`@NotBlank`), `expirationMinutes` (`@Min(1)`), `issuer` (`@NotBlank`). `@Validated`. Constante `MIN_SECRET_LENGTH = 32`. |
| `CorsProperties` | `app.cors` | `allowedOrigins : List<String>` (copie immuable, jamais nul). |
| `AdminSeedProperties` | `app.admin-seed` | `enabled`, `phone`, `password`, `firstName`, `lastName`. Méthode `hasExplicitPassword()`. |
| `LoginProtectionProperties` | `app.login-protection` | `maxAttempts` (défaut 5 si ≤ 0), `lockDurationMinutes` (défaut 15 si ≤ 0). |
| `StorageProperties` | `app.storage` | `rootDir` (défaut `./storage` si vide). |

Enregistrées via `@EnableConfigurationProperties({...})` sur `ConverterApplication`.

### 3.6 Autres classes `@Configuration` (`config/`)

| Classe | Rôle |
|---|---|
| `ClockConfig` | Bean `Clock systemClock()` = `Clock.systemUTC()`. Injecté partout où une fenêtre temporelle doit être testable (`Clock.fixed(...)`). |
| `PersistenceConfig` | `@EnableJpaAuditing` — remplit `createdAt`/`updatedAt` sur `AuditableEntity`. |
| `WebMvcConfig` | Enregistre `CurrentUserArgumentResolver`. |
| `OpenApiConfig` | Métadonnées OpenAPI + schéma de sécurité `bearer-jwt` (HTTP bearer, format JWT). |

---

## 4. Démarrage, profils et amorçage administrateur

**`ConverterApplication`** : `@SpringBootApplication`, `@EnableScheduling`, `@EnableConfigurationProperties(...)`. `main()` force `TimeZone.setDefault(UTC)` **avant** `SpringApplication.run`.

**`AdminAccountSeeder`** (`user/service/`, `ApplicationRunner`, `@Transactional`) :

1. Si `app.admin-seed.enabled = false` → rien.
2. Si un compte `ADMIN` existe déjà (`UserRepository.countByRole(ADMIN) > 0`) → rien.
3. `ADMIN_PHONE` obligatoire, normalisé et validé E.164.
4. Si un compte existe déjà sur ce numéro (non-admin) → `IllegalStateException` (pas de promotion silencieuse).
5. Résolution du mot de passe :
   - fourni → utilisé tel quel ;
   - absent + profil `prod` → `IllegalStateException` ;
   - absent + autre profil → mot de passe aléatoire de **20 caractères** (`SecureRandom`, alphabet sans caractères ambigus), affiché **une seule fois** dans un bandeau `WARN`.
6. Création du `User` avec le rôle `ADMIN` (`roles` amorcée par `V3`).

---

## 5. Conteneurisation

### 5.1 `backend/Dockerfile` (multi-stage)

| Étape | Base | Contenu |
|---|---|---|
| `build` | `eclipse-temurin:21-jdk` | Copie `.mvn/`, `mvnw`, `pom.xml` → `./mvnw -B dependency:go-offline` (couche de cache), puis `src/` → `./mvnw -B clean package -DskipTests`. |
| `runtime` | `eclipse-temurin:21-jre` | Installe `wget` (pour le HEALTHCHECK), crée l'utilisateur système non-root `converter`, copie `target/converter-backend-*.jar` → `app.jar`, `USER converter`, `EXPOSE 8080`. |

`HEALTHCHECK` : `wget -q -O - http://localhost:8080/actuator/health | grep '"status":"UP"'`, `interval=10s`, `start-period=45s`, `retries=5`.
`ENTRYPOINT ["java","-jar","/app/app.jar"]`.

### 5.2 `docker-compose.yml` (racine)

| Service | Détail |
|---|---|
| `postgres` | `postgres:16-alpine`, port `5432`, volume nommé `converter-postgres-data`, `healthcheck` via `pg_isready`. |
| `backend` | `build: ./backend`, `depends_on: postgres (service_healthy)`. Variables : `SPRING_PROFILES_ACTIVE`, `DB_URL/USERNAME/PASSWORD`, `JWT_EXPIRATION_MINUTES`, `CORS_ALLOWED_ORIGINS`, `ADMIN_*`, `SERVER_PORT`. **`JWT_SECRET` et `ADMIN_PASSWORD` sont transmis sous forme nue** (pas de `${VAR:-défaut}`) : Compose ne les exporte que s'ils existent réellement dans l'environnement hôte, sinon `application-dev.yml` décide. Healthcheck via `wget` sur `/actuator/health`. |

Le frontend (`frontend/`, Angular) existe dans le dépôt mais **n'est pas dans le compose**.

---

## 6. Organisation des packages

```
com.converter
├── ConverterApplication
├── common/          api · domain · exception · util · validation
├── config/          ClockConfig · OpenApiConfig · PersistenceConfig · WebMvcConfig · props/
├── security/        SecurityConfig · jwt/ · CurrentUser · OwnershipService · RateLimitFilter · SecurityResponseWriter
├── auth/            AuthController · AuthService · dto/
├── user/            domain/ · repository/ · service/ (UserService, AdminAccountSeeder) · mapper/ · dto/
├── settings/        domain/ · repository/ · service/ · web/ · dto/
├── audit/           domain/ · repository/ · service/ · dto/
├── admin/           AdminUserController · AdminAuditLogController
├── rate/            domain/ · provider/ · engine/ · repository/ · service/ · web/ · dto/
├── quote/           domain/ · repository/ · service/ · web/ · dto/
├── order/           domain/ · repository/ · service/ · web/ · dto/
├── payment/         domain/ · repository/ · service/ · web/ · dto/
├── settlement/      domain/ · repository/ · service/ · web/ · dto/
├── treasury/        domain/ · repository/ · service/ · web/ · dto/
├── storage/         FileStorageService · LocalFileStorageService · FileValidator · FileNameSanitizer · StoredFile · exception/
├── wallet/          domain/ · repository/ · service/ · web/ · dto/
├── preferredrate/   domain/ · repository/ · scheduler/ · service/ · web/ · dto/
└── notification/    domain/ · repository/ · service/ · web/ · dto/
```

### 6.1 Règle de dépendance entre modules

Les flèches ne remontent jamais. En pratique, d'après les `import` du code :

```
common  ← (tout le monde)
config, security, storage, settings, audit  ← modules socle
rate  (engine ne connaît QUE BigDecimal + records) ← quote, preferredrate
quote ← order
order ← payment, settlement
treasury ← order, payment, settlement
wallet ← preferredrate
notification ← quote, payment, preferredrate
audit ← (émis par presque tous les services)
```

- `treasury` ne connaît jamais `order` : elle reçoit un `orderId` **opaque** (UUID) comme corrélation.
- `rate/engine/RateEngine` n'a **aucune** dépendance Spring/JPA — testable sans contexte. Il ignore le mot « Quote » ; `QuoteService` traduit `QuoteDirection` → `AmountBasis`.
- `payment`/`settlement` déclenchent les transitions d'`order` **uniquement** via des méthodes `OrderService.transitionToXxx(...)`, jamais en mutant `Order`.
- `admin/` ne contient aucune règle métier : orchestration/agrégation pure (et n'héberge aujourd'hui que `AdminUserController` + `AdminAuditLogController` ; les autres contrôleurs admin vivent dans leur module).

---

## 7. Socle commun (`common`)

### 7.1 `common/api`

| Type | Forme | Détail |
|---|---|---|
| `ApiResponse<T>` | `{ data, message }` | Enveloppe **de tout succès**. Fabriques `of(data)`, `of(data, msg)`, `message(msg)`. Message par défaut `"Operation successful"`. Empêche un endpoint de renvoyer un tableau nu. |
| `ErrorResponse` | `{ timestamp, status, error, code, message, path, traceId, violations[] }` | Enveloppe **de toute erreur**. `code` = identifiant machine stable (le frontend teste `code`, jamais `message`). `violations` = `List<FieldViolation(field, message)>`, nul si vide. |
| `PageResponse<T>` | `{ content[], page, size, totalElements, totalPages, first, last }` | Vue stable d'une `Page` Spring Data (jamais sérialisée directement). `from(page)` et `from(page, mapper)`. |

### 7.2 `common/domain`

| Classe | Rôle |
|---|---|
| `BaseEntity` | `@MappedSuperclass`. `@Id UUID` (`GenerationType.UUID`). `equals`/`hashCode` sur l'id ; `hashCode` **constant** (`getClass().hashCode()`) pour rester correct avant/après persistance dans un `HashSet`. |
| `AuditableEntity extends BaseEntity` | Ajoute `createdAt` (`@CreatedDate`, non modifiable), `updatedAt` (`@LastModifiedDate`), `version` (`@Version`, verrou optimiste). `@EntityListeners(AuditingEntityListener)`. |

> **Nota :** peu d'entités héritent réellement d'`AuditableEntity` — seule `User` le fait. Les autres entités déclarent `createdAt`/`updatedAt`/`version` **en propre** (souvent fixés explicitement par le service via `Clock`), notamment parce que leur `createdAt` doit correspondre à l'instant métier, pas au flush.

### 7.3 `common/exception`

**Hiérarchie :**

```
RuntimeException
└── BusinessException(ErrorCode, message)        // pas de stack trace (super(msg, null, false, false))
    ├── DuplicateResourceException               // + fabrique phone(...)
    ├── ResourceNotFoundException                // + fabriques user(...), setting(...)
    ├── TooManyAttemptsException(lockMinutes)
    ├── UserBlockedException()
    └── storage.exception.InvalidFileException    // ErrorCode.INVALID_PAYMENT_PROOF
```

`storage.exception.StorageException` hérite directement de `RuntimeException` (échec technique, pas métier).

**`ErrorCode`** — catalogue fermé, chaque valeur porte son `HttpStatus` et sa catégorie (`error`) :

| Code | HTTP | Catégorie |
|---|---|---|
| `VALIDATION_ERROR`, `MALFORMED_REQUEST` | 400 | `VALIDATION_ERROR` |
| `INVALID_PAYMENT_PROOF`, `ORDER_AMOUNT_OUT_OF_RANGE`, `INVALID_SETTING_VALUE`, `PAYMENT_AMOUNT_MISMATCH` | 400 | `BUSINESS_ERROR` |
| `AUTHENTICATION_REQUIRED`, `INVALID_CREDENTIALS`, `INVALID_TOKEN` | 401 | `AUTHENTICATION_ERROR` |
| `ACCESS_DENIED`, `USER_BLOCKED` | 403 | `AUTHORIZATION_ERROR` |
| `RESOURCE_NOT_FOUND`, `USER_NOT_FOUND`, `SETTING_NOT_FOUND`, `ORDER_NOT_FOUND`, `PAYMENT_NOT_FOUND`, `QUOTE_NOT_FOUND`, `SETTLEMENT_NOT_FOUND`, `PREFERRED_RATE_NOT_FOUND`, `NOTIFICATION_NOT_FOUND` | 404 | `NOT_FOUND` |
| `DUPLICATE_RESOURCE`, `PHONE_ALREADY_REGISTERED`, `CONCURRENT_MODIFICATION`, `INVALID_ORDER_STATE`, `EXCHANGE_RATE_EXPIRED`, `RATE_CHANGED`, `INSUFFICIENT_TREASURY`, `DUPLICATE_TRANSACTION_REFERENCE`, `PAYMENT_ALREADY_REVIEWED`, `INVALID_QUOTE_STATE`, `QUOTE_EXPIRED`, `QUOTE_NOT_ACCEPTED`, `QUOTE_ALREADY_USED`, `INVALID_PAYMENT_STATE`, `INVALID_SETTLEMENT_STATE`, `TOO_MANY_OPEN_ORDERS`, `INSUFFICIENT_WALLET_BALANCE`, `INVALID_PREFERRED_RATE_STATE`, `IDEMPOTENCY_KEY_REUSED`, `IDEMPOTENT_REQUEST_IN_PROGRESS` | 409 | `BUSINESS_ERROR` |
| `TOO_MANY_ATTEMPTS` | 429 | `RATE_LIMIT` |
| `INTERNAL_ERROR` | 500 | `INTERNAL_ERROR` |
| `EXCHANGE_RATE_UNAVAILABLE`, `RATE_SOURCE_UNAVAILABLE` | 503 | `BUSINESS_ERROR` |

> Certains codes (`RATE_CHANGED`, `DUPLICATE_TRANSACTION_REFERENCE`, `PAYMENT_ALREADY_REVIEWED`, `EXCHANGE_RATE_EXPIRED`) sont **déclarés mais pas encore levés** par le code actuel — réservés pour cohérence de contrat.

**`GlobalExceptionHandler`** (`@RestControllerAdvice`) — traductions :

| Exception interceptée | → `ErrorCode` | Log |
|---|---|---|
| `BusinessException` | son propre `errorCode()` | `DEBUG` |
| `MethodArgumentNotValidException` | `VALIDATION_ERROR` + `violations` par champ | — |
| `ConstraintViolationException` | `VALIDATION_ERROR` + `violations` | — |
| `HttpMessageNotReadableException` | `MALFORMED_REQUEST` | `DEBUG` |
| `MethodArgumentTypeMismatchException`, `MissingServletRequestParameterException` | `VALIDATION_ERROR` | — |
| `MaxUploadSizeExceededException` | `INVALID_PAYMENT_PROOF` | — |
| `AccessDeniedException` (Spring Security) | `ACCESS_DENIED` | `WARN` |
| `AuthenticationException` | `AUTHENTICATION_REQUIRED` | — |
| `DataIntegrityViolationException` | `DUPLICATE_RESOURCE` (+ `traceId`) | `WARN` (message SQL en logs uniquement) |
| `OptimisticLockingFailureException` | `CONCURRENT_MODIFICATION` | `INFO` |
| `NoResourceFoundException` | `RESOURCE_NOT_FOUND` | — |
| `Exception` (fallback) | `INTERNAL_ERROR` (+ `traceId`) | `ERROR` (stack trace en logs) |

`traceId` = 8 premiers caractères d'un UUID, transmis au client pour corrélation support sans fuite de stack trace.

### 7.4 `common/util` & `common/validation`

- **`RequestContext`** — accès à la requête HTTP courante depuis la couche service (audit uniquement). `clientIp()` lit `X-Forwarded-For` (1ʳᵉ entrée, tronquée à 45), sinon `getRemoteAddr()`. `userAgent()` tronqué à 255. Renvoie `null` hors contexte web (job planifié, test).
- **`@PhoneNumber` / `PhoneNumberValidator`** — regex E.164 `^\+[1-9][0-9]{7,14}$`. `isValid` laisse passer `null` (délégué à `@NotBlank`). `normalize(raw)` retire espaces et séparateurs `. ( ) -` avant validation/persistance. Doublé en base par `ck_users_phone_format`.

### 7.5 Idempotence HTTP (`common/idempotency`)

Exploite la table `idempotency_keys` (migrée en `V1`, activée par la migration `V15` qui aligne
`request_hash` sur `VARCHAR(64)`). En-tête `Idempotency-Key` **optionnel** — son absence ne change
rien au comportement.

| Classe | Rôle |
|---|---|
| `IdempotencyKey` (`@Entity`, table `idempotency_keys`) | `idemKey, userId, endpoint, requestHash (SHA-256 hex), responseStatus (Integer nullable), responseBody (JSONB String), createdAt, expiresAt`. `isPending()` = `responseStatus == null`. Seule transition : `complete(status, bodyJson)`. |
| `IdempotencyKeyRepository` | `findByUserIdAndEndpointAndIdemKey(...)`, `deletePending(...)` (`@Modifying`, supprime seulement une capture `responseStatus IS NULL`). |
| `IdempotencyService` (`@Service`) | `tryInsert(...)` / `findExisting(...)` / `releasePending(...)` en `@Transactional(REQUIRES_NEW)` (comme `AuditService`) ; `tryInsert` INSERT + flush **laisse remonter** `DataIntegrityViolationException` (jamais de catch interne — la transaction avortée PostgreSQL doit rollback entière). **`complete(...)` en `REQUIRED`** (passe 2 P2-3) : rejoint la transaction de `guard`. |
| `IdempotencyGuard` (`@Component`, **`@Transactional`** depuis la passe 2) | Orchestrateur appelé par les contrôleurs : `guard(userId, endpoint, idemKey, requestBody, TypeReference<T>, Supplier<ResponseEntity<T>>)`. Séquence : hash SHA-256 du corps → `claimOrLookupExisting` (tente `tryInsert`, rattrape la collision **hors transaction avortée** puis `findExisting`) → clé absente : exécute l'action **dans la transaction de `guard`**, `complete(status, body)` **dans la même transaction** (effet métier + cache de réponse committent ensemble ou pas du tout — passe 2 P2-3) ; sur `RuntimeException` : `releasePending` (REQUIRES_NEW) + rethrow (un échec métier n'est **pas** mis en cache, une nouvelle tentative ré-exécute). Clé présente : `IDEMPOTENCY_KEY_REUSED` (409) si hash ≠, `IDEMPOTENT_REQUEST_IN_PROGRESS` (409) si encore `pending`, sinon **rejeu** de la réponse mémorisée (statut + corps désérialisé). |

**Partitionnement** : la clé de recherche est toujours `(userId authentifié, endpoint, idemKey)` — jamais un identifiant fourni par le client. Deux utilisateurs peuvent utiliser la même valeur littérale sans collision ni fuite. Pour `payments` et `settlements/execute`, `endpoint` inclut l'identifiant de ressource du chemin (`POST /api/v1/orders/{orderId}/payments`, `POST /api/admin/settlements/{id}/execute`).

**Endpoints protégés** : `POST /api/v1/orders`, `POST /api/v1/orders/{orderId}/payments`, `POST /api/admin/settlements/{id}/execute`, `POST /api/admin/treasury/deposit`, `POST /api/admin/treasury/adjust`.

**Limite connue** : aucune tâche de purge des clés expirées (`expires_at` écrit = `createdAt + 24 h`, mais jamais relu). Une capture laissée `pending` par un crash du processus entre `tryInsert` et `complete`/`releasePending` bloque les rejeux de cette clé précise jusqu'à intervention manuelle (rare ; un futur job `@Scheduled` pourra la balayer).

---

## 8. Sécurité (`security`)

### 8.1 `SecurityConfig` (`@EnableWebSecurity`, `@EnableMethodSecurity`)

**`SecurityFilterChain` :**

- `csrf().disable()` — API stateless consommée par un frontend distinct ; pas de formulaire même origine.
- `cors(...)` — source explicite (voir plus bas).
- `sessionManagement` → **`STATELESS`**.
- `exceptionHandling` → `JwtAuthenticationEntryPoint` (401) + `JwtAccessDeniedHandler` (403).
- `authorizeHttpRequests` :

  | Matcher | Règle |
  |---|---|
  | `POST /api/auth/register`, `POST /api/auth/login` | `permitAll` |
  | `/api/settings/public` | `permitAll` |
  | `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html` | `permitAll` |
  | `/actuator/health/**` | `permitAll` |
  | `/api/admin/**` | `hasRole('ADMIN')` — **première barrière** |
  | `anyRequest()` | `authenticated()` |

- Filtres ajoutés `addFilterBefore(..., UsernamePasswordAuthenticationFilter.class)` : `RateLimitFilter` puis `JwtAuthenticationFilter`.

> `/api/auth/me` **n'est pas** dans `permitAll` : il tombe sous `anyRequest().authenticated()`. Volontaire — un `/api/auth/**` générique laisserait `me` franchir sans principal et produirait une 500 dans le resolver au lieu d'une 401 propre.

**CORS** (`corsConfigurationSource`) : origines = `CorsProperties.allowedOrigins` (jamais `*`), méthodes `GET/POST/PUT/PATCH/DELETE/OPTIONS`, headers autorisés `Authorization`, `Content-Type`, **`Idempotency-Key`**, header exposé `Location`, `allowCredentials(true)`, `maxAge 3600`.

**`PasswordEncoder`** : `BCryptPasswordEncoder(12)`.

### 8.2 `JwtService`

- Clé HS256 dérivée de `app.jwt.secret` (UTF-8). `@PostConstruct` : **échec net au démarrage si le secret < 32 octets**.
- `generateToken(User)` : `sub` = UUID du compte (jamais le téléphone), `iss` = `converter-api`, `iat`, `exp` = maintenant + `expirationMinutes`, claim `roles` = liste de noms de rôles.
- `parse(token)` : vérifie signature + **exige l'issuer** + expiration. Retourne `ParsedToken(userId, Set<RoleCode>)`.
- `expirationDuration()` : `Duration` utilisée pour renseigner `expiresInSeconds` dans `AuthResponse`.
- **Pas de refresh token** (décision assumée) : le seul moyen de révocation immédiate est la revalidation du compte à chaque requête.

### 8.3 `JwtAuthenticationFilter` (`OncePerRequestFilter`)

Pour chaque requête portant `Authorization: Bearer <jwt>` :

1. Pas de jeton → passe la main (un endpoint protégé sera rejeté ensuite par l'`AuthorizationFilter` → `JwtAuthenticationEntryPoint`).
2. `jwtService.parse` échoue → `401 INVALID_TOKEN`.
3. `userRepository.findById(sub)` vide → `401 INVALID_TOKEN` (« Ce compte n'existe plus »).
4. **`!user.isActive()` → `403 USER_BLOCKED`** — un blocage administratif prend effet **immédiatement**, sans attendre l'expiration du jeton (coût assumé : une lecture d'index primaire par requête).
5. Sinon : construit `CurrentUser` et pose l'`Authentication` dans le `SecurityContext`.

`shouldNotFilter` → vrai uniquement pour `OPTIONS` (préflight CORS).

### 8.4 `RateLimitFilter` (`OncePerRequestFilter`)

- S'applique uniquement à `POST /api/auth/login`.
- Compteur **par adresse IP** (jamais par téléphone soumis — sinon DoS ciblé possible sur un tiers), stocké dans un `Cache<String, AtomicInteger>` Caffeine, `expireAfterWrite = lockDurationMinutes`, `maximumSize = 10_000`.
- Au-delà de `maxAttempts` → `429 TOO_MANY_ATTEMPTS` (via `SecurityResponseWriter`).
- Incrémente **avant** de traiter (même une exception compte). Réponse HTTP < 400 → compteur de l'IP remis à zéro.
- Mémoire locale : suffisant pour une instance ; multi-instance nécessiterait Redis (hors périmètre).

### 8.5 Autres composants

| Classe | Rôle |
|---|---|
| `CurrentUser implements UserDetails` | Principal léger : `id`, `phone`, `Set<RoleCode>`, `enabled`. `getAuthorities()` → `ROLE_USER` / `ROLE_ADMIN`. `getPassword()` → `null` (auth par jeton). `isAdmin()`. Fabrique `fromEntity(User)`. |
| `@AuthenticatedUser` + `CurrentUserArgumentResolver` | Injecte `CurrentUser` dans les signatures de contrôleur. Lève `IllegalStateException` si aucun principal (ne devrait jamais arriver). |
| `OwnershipService` | `assertOwnedBy(ownerId, currentUserId, notFoundCode, message)` → lève une `BusinessException` avec un code **404** si mismatch. **Jamais 403** : un 403 confirmerait l'existence de la ressource (énumération). |
| `SecurityResponseWriter` | Sérialise un `ErrorResponse` depuis la chaîne de filtres (là où `@RestControllerAdvice` ne peut pas intercepter). Garantit un format d'erreur identique filtre/contrôleur. |
| `JwtAuthenticationEntryPoint` | `401 AUTHENTICATION_REQUIRED`. |
| `JwtAccessDeniedHandler` | `403 ACCESS_DENIED`. |

**Double barrière admin** : `/api/admin/**` verrouillé dans `SecurityFilterChain` **et** `@PreAuthorize("hasRole('ADMIN')")` sur chaque contrôleur admin. Une régression sur une annotation ne suffit pas à ouvrir l'espace d'administration.

---

## 9. Schéma de base de données

**Conventions :** PK `UUID` (`gen_random_uuid()`), `TIMESTAMPTZ` en UTC, `NUMERIC` pour les montants, enums en `VARCHAR + CHECK`, `version BIGINT` (verrou optimiste) sur les entités concurrentes, ledgers append-only (aucun `UPDATE`/`DELETE`).

### 9.1 Historique des migrations

| Migration | Contenu |
|---|---|
| `V1__initial_schema` | `roles`, `users`, `user_roles`, `system_settings`, `exchange_rates`, `orders`, `beneficiaries`, `payments`, `payment_proofs`, `treasury_accounts`, `treasury_transactions`, `order_status_history`, `audit_logs`, `idempotency_keys`. |
| `V2__indexes_and_constraints` | Index & **index uniques partiels métier** (voir §19.2). |
| `V3__seed_roles` | `USER` (id 1), `ADMIN` (id 2). |
| `V4__seed_settings` | 10 clés `system_settings` (voir §18.3). |
| `V5__seed_treasury_accounts` | Comptes `XOF` et `CNY`, soldes à 0. |
| `V6__order_reference_sequence` | Séquence `order_reference_seq` + fonction `next_order_reference()` → `ORD-YYYYMM-000123`. |
| `V7__rate_and_quote_schema` | **`rate_sources`** (remplace l'usage de `exchange_rates`) + **`quotes`**. |
| `V8__seed_pricing_settings` | `DEFAULT_MARGIN_PERCENTAGE` (1.5), `DEFAULT_FEE_PERCENTAGE` (0), `DEFAULT_FIXED_FEE_XOF` (0). |
| `V9__order_and_beneficiary_schema` | **`DROP` `payment_proofs`, `payments`, `order_status_history`, `beneficiaries`, `orders`, `exchange_rates`** ; recrée `orders` (avec `quote_id`), `beneficiaries`, `order_status_history` sous leur forme définitive ; recrée la FK `treasury_transactions → orders`. |
| `V10__payment_schema` | Recrée `payments` (`expected_amount_xof`/`received_amount_xof`, statuts `SUBMITTED/CONFIRMED/REJECTED`, `uq_payments_order` UNIQUE) + `payment_proofs`. |
| `V11__settlement_schema` | **`settlements`** (snapshot bénéficiaire, `PENDING/EXECUTED`) + **`settlement_proofs`**. |
| `V12__wallet_schema` | **`wallets`** (solde XOF par utilisateur) + **`wallet_transactions`** (ledger `CREDIT/DEBIT/RESERVE/RELEASE`). |
| `V13__preferred_rate_schema` | **`preferred_rate_requests`** + **`exchanges`** (FK croisée `preferred_rate_requests.exchange_id`). |
| `V14__notifications_schema` | **`notifications`** (canal `IN_APP`, 9 types ; 10 depuis `V16` avec `ORDER_EXPIRED`). |
| `V15__idempotency_keys_request_hash_varchar` | `idempotency_keys.request_hash` : `CHAR(64)` → `VARCHAR(64)` (activation réelle de la table par `common/idempotency`, alignement identique à `checksum_sha256` en V10/V11). Élargissement strict, aucune donnée affectée. |
| `V16__order_expiry_and_hardening` | **Passe 2.** (1) `orders.payment_deadline_at` (colonne stockée + backfill `created_at + 720 min` + `NOT NULL`) + `idx_orders_payment_deadline` partiel. (2) `uq_treasury_tx_order_type` → **remplacé** par `uq_treasury_tx_reservation_per_order` (`WHERE type='RESERVATION'`) + `uq_treasury_tx_resolution_per_order` (`WHERE type IN ('RELEASE','WITHDRAWAL')`) : une réservation est résolue *exactement une fois*. (3) `notifications` CHECK + `ORDER_EXPIRED`. (4) seed `ORDER_PAYMENT_WINDOW_MINUTES`=720, `PAYMENT_AMOUNT_TOLERANCE_XOF`=0, `RATE_MAX_AGE_MINUTES`=0. |

> **Tables présentes mais mortes dans le code actuel :** `exchange_rates` (supprimée en `V9`), `idempotency_keys` (jamais lue/écrite — voir §20).

### 9.2 Schéma effectif — tables actives

Seules les colonnes/contraintes utiles sont listées. `*` = colonne verrou optimiste `version`.

#### `roles` / `user_roles`
`roles(id SMALLINT PK, code VARCHAR(20) UNIQUE CHECK IN ('USER','ADMIN'), label)`.
`user_roles(user_id UUID FK→users ON DELETE CASCADE, role_id SMALLINT FK→roles, PK(user_id,role_id))`, `idx_user_roles_role`.

#### `users`  *
| Colonne | Type / contrainte |
|---|---|
| `id` | UUID PK |
| `phone` | VARCHAR(20) **UNIQUE NOT NULL**, `ck_users_phone_format` (E.164) |
| `password_hash` | VARCHAR(100) NOT NULL (BCrypt) |
| `first_name`, `last_name` | VARCHAR(80) NOT NULL |
| `email` | VARCHAR(160), `uq_users_email` UNIQUE partiel `WHERE email IS NOT NULL` |
| `status` | VARCHAR(20) DEFAULT `ACTIVE`, CHECK ∈ (`ACTIVE`,`BLOCKED`) |
| `blocked_at`, `blocked_reason`, `blocked_by` (FK→users) | nullable |
| `last_login_at` | nullable |
| `created_at`, `updated_at` | NOT NULL |

Index : `idx_users_status_created(status, created_at DESC)`.

#### `system_settings`  *
`setting_key VARCHAR(64) PK`, `value VARCHAR(255)`, `value_type CHECK ∈ (STRING,INTEGER,DECIMAL,BOOLEAN)`, `description`, `is_public BOOLEAN`, `updated_by` (FK→users), `updated_at`.

#### `rate_sources` (append-only, entité `@Immutable`)
| Colonne | Type / contrainte |
|---|---|
| `id` | UUID PK |
| `provider_type` | VARCHAR(16) CHECK ∈ (`MANUAL`,`MARKET`,`P2P`) |
| `currency_pair` | VARCHAR(10) DEFAULT `XOF/CNY` |
| `cfa_per_cny` | NUMERIC(18,6) CHECK > 0 |
| `effective_from` | TIMESTAMPTZ NOT NULL |
| `effective_to` | TIMESTAMPTZ nullable (**NULL = cotation courante**) |
| `note`, `created_by` (FK→users), `created_at` | |

Index : **`uq_rate_source_current`** UNIQUE `(provider_type, currency_pair, (effective_to IS NULL)) WHERE effective_to IS NULL` — au plus une cotation courante par source/paire. `idx_rate_sources_effective_from(currency_pair, effective_from DESC)`.

#### `quotes`  *
| Colonne | Type / contrainte |
|---|---|
| `id` | UUID PK |
| `user_id` | UUID FK→users |
| `direction` | VARCHAR(16) CHECK ∈ (`SEND_XOF`,`RECEIVE_CNY`) |
| `amount_xof`, `amount_cny` | NUMERIC(19,2) CHECK > 0 |
| `market_rate`, `customer_rate` | NUMERIC(18,6) CHECK > 0 |
| `margin_percentage` | NUMERIC(6,4) CHECK ≥ 0 |
| `fee_percentage` | NUMERIC(6,4) CHECK ≥ 0 AND < 100 |
| `fixed_fee_xof`, `fee_xof` | NUMERIC(19,2) CHECK ≥ 0 |
| `net_amount_xof` | NUMERIC(19,2) CHECK > 0, **`ck_quotes_net_coherent` : `net_amount_xof = amount_xof - fee_xof`** |
| `rate_source_id` | UUID FK→rate_sources |
| `status` | VARCHAR(16) CHECK ∈ (`ACTIVE`,`ACCEPTED`,`EXPIRED`,`CANCELLED`) |
| `created_at`, `expires_at` (CHECK `> created_at`), `accepted_at`, `cancelled_at` | |

Index : `idx_quotes_user_created`, `idx_quotes_status_expiry(status, expires_at) WHERE status='ACTIVE'`, `idx_quotes_rate_source`.

#### `orders`  *
| Colonne | Type / contrainte |
|---|---|
| `id` | UUID PK |
| `reference` | VARCHAR(24) **UNIQUE** (`next_order_reference()`) |
| `user_id` | UUID FK→users |
| `quote_id` | UUID FK→quotes, **`uq_orders_quote` UNIQUE** (un devis ⇒ au plus un ordre) |
| `status` | VARCHAR(24) CHECK ∈ 8 statuts (voir §12) |
| `amount_xof`, `amount_cny` | NUMERIC(19,2) CHECK > 0 |
| `customer_rate` | NUMERIC(18,6) CHECK > 0 |
| `fee_xof` | NUMERIC(19,2) CHECK ≥ 0 |
| `net_amount_xof` | NUMERIC(19,2) CHECK > 0, `ck_orders_net_coherent` |
| `note`, `cancellation_reason`, `rejection_reason` | nullable |
| `treasury_reserved` | BOOLEAN DEFAULT FALSE |
| `completed_at`, `cancelled_at`, `created_at`, `updated_at` | |

Index : `idx_orders_user_created`, `idx_orders_status_created`.
Colonnes financières = **copie figée du `Quote`** à la création, jamais recalculées. `quote_id` = seul lien vivant (traçabilité).

#### `beneficiaries` (entité `@Immutable`, 1‑1 avec `orders`)
`id`, `order_id` UUID **UNIQUE** FK→orders ON DELETE CASCADE, `type` CHECK ∈ (`ALIPAY`,`WECHAT_PAY`,`CHINESE_BANK_ACCOUNT`), `full_name`, `identifier`, `bank_name`, `bank_branch`, `created_at`.
`ck_beneficiaries_bank_required` : `type <> 'CHINESE_BANK_ACCOUNT' OR bank_name IS NOT NULL` (doublé côté Java dans le constructeur). `idx_beneficiaries_identifier`.

#### `order_status_history` (entité `@Immutable`)
`id`, `order_id` FK→orders ON DELETE CASCADE, `from_status` (NULL = création), `to_status`, `changed_by` (FK→users, NULL = système), `reason`, `created_at`. `idx_osh_order_created(order_id, created_at)`.

#### `payments`  *
| Colonne | Type / contrainte |
|---|---|
| `id` | UUID PK |
| `order_id` | UUID FK→orders, **`uq_payments_order` UNIQUE** (un paiement par ordre) |
| `method` | VARCHAR(24) CHECK ∈ (`MOBILE_MONEY`,`WAVE`,`BANK_TRANSFER`) |
| `status` | VARCHAR(16) CHECK ∈ (`SUBMITTED`,`CONFIRMED`,`REJECTED`) |
| `expected_amount_xof` | NUMERIC(19,2) CHECK > 0 (copié depuis `order.amount_xof`) |
| `received_amount_xof` | NUMERIC(19,2) CHECK > 0 (déclaré par le client) |
| `transaction_reference` | VARCHAR(100) NOT NULL |
| `payer_phone` | nullable |
| `submitted_at`, `confirmed_at`, `rejected_at`, `reviewed_by` (FK→users) | |
| `rejection_reason` | `ck_payments_rejection_reason` : `(status='REJECTED') = (rejection_reason IS NOT NULL)` |

Index : `idx_payments_pending(submitted_at) WHERE status='SUBMITTED'`, **`uq_payments_txref` UNIQUE `(method, transaction_reference)`** (non partiel — une référence ne sert jamais deux ordres).

#### `payment_proofs` (entité `@Immutable`)
`id`, `payment_id` FK→payments ON DELETE CASCADE, `file_name` (assaini), `content_type` CHECK ∈ allowlist (`image/jpeg|png|webp`, `application/pdf`), `storage_key` VARCHAR(500) **UNIQUE**, `storage_provider` DEFAULT `LOCAL` CHECK ∈ (`LOCAL`,`S3`), `size_bytes` CHECK > 0, `checksum_sha256` VARCHAR(64), `uploaded_by` (FK→users), `uploaded_at`. `idx_payment_proofs_payment`.

#### `settlements`  *
| Colonne | Type / contrainte |
|---|---|
| `id` | UUID PK |
| `order_id` | UUID FK→orders, **`uq_settlements_order` UNIQUE** |
| `status` | VARCHAR(16) CHECK ∈ (`PENDING`,`EXECUTED`) |
| `amount_cny` | NUMERIC(19,2) CHECK > 0 |
| `method` | VARCHAR(24) CHECK ∈ (`ALIPAY`,`WECHAT_PAY`,`CHINESE_BANK_ACCOUNT`) (= `BeneficiaryType`) |
| `beneficiary_full_name`, `beneficiary_identifier` | NOT NULL (snapshot) |
| `beneficiary_bank_name`, `beneficiary_bank_branch` | nullable |
| `settlement_reference`, `notes`, `executed_by` (FK→users), `executed_at` | |
| | **`ck_settlements_reference_on_execution`** : `(status='EXECUTED') = (settlement_reference IS NOT NULL AND executed_at IS NOT NULL)` |

Index : `idx_settlements_status_created`.

#### `settlement_proofs` (entité `@Immutable`)
Structure identique à `payment_proofs`, FK→`settlements` ON DELETE CASCADE.

#### `treasury_accounts`  *
`id`, `currency` VARCHAR(3) **UNIQUE** CHECK ∈ (`XOF`,`CNY`), `balance` NUMERIC(21,2) CHECK ≥ 0, `reserved_balance` NUMERIC(21,2) CHECK ≥ 0, `low_threshold`, `updated_at`.
**`ck_treasury_accounts_reserved_le_balance` : `reserved_balance <= balance`** — invariant central, rend une sur-réservation physiquement impossible. `Available = balance − reserved_balance`.

#### `treasury_transactions` (ledger append-only, entité `@Immutable`)
`id`, `account_id` FK→treasury_accounts, `type` CHECK ∈ (`DEPOSIT`,`WITHDRAWAL`,`RESERVATION`,`RELEASE`,`ADJUSTMENT`), `amount` NUMERIC(21,2) CHECK > 0 (signe porté par `type`), `balance_after`, `reserved_after` (CHECK ≥ 0), `order_id` (FK→orders, nullable), `performed_by` (FK→users, NULL = système), `reason`, `created_at`.
Index : `idx_treasury_tx_account_created`, `idx_treasury_tx_order`, **`uq_treasury_tx_reservation_per_order` UNIQUE `(order_id) WHERE type='RESERVATION'`** (≤ 1 réservation par ordre), **`uq_treasury_tx_resolution_per_order` UNIQUE `(order_id) WHERE type IN ('RELEASE','WITHDRAWAL')`** (une réservation d'ordre est résolue *exactement une fois* — libérée **xor** consommée, jamais les deux, jamais deux fois — passe 2 P2-4 ; remplace l'ancien `uq_treasury_tx_order_type` en `V16`).

#### `wallets`  *
`id`, `user_id` UUID **UNIQUE** FK→users, `balance` NUMERIC(19,2) CHECK ≥ 0, `reserved_balance` NUMERIC(19,2) CHECK ≥ 0, `ck_wallets_reserved_within_balance` : `reserved_balance <= balance`, `updated_at`.

#### `wallet_transactions` (ledger append-only, entité `@Immutable`)
`id`, `wallet_id` FK→wallets, `type` CHECK ∈ (`CREDIT`,`DEBIT`,`RESERVE`,`RELEASE`), `amount` CHECK > 0, `balance_after`, `reserved_after`, `reference_id` (UUID libre, ex. `PreferredRateRequest.id`), `reason`, `created_at`. `idx_wallet_transactions_wallet_created`.

#### `preferred_rate_requests`  *
`id`, `user_id` FK→users, `direction` CHECK ∈ (`XOF_TO_CNY`), `amount_xof` CHECK > 0, `target_rate` NUMERIC(18,6) CHECK > 0, `status` CHECK ∈ (`ACTIVE`,`EXECUTED`,`EXPIRED`,`CANCELLED`), `achieved_rate` (nullable), `exchange_id` (FK→exchanges, ajoutée en fin de `V13`), `created_at`, `expires_at` (= created_at + 3 j), `executed_at`, `expired_at`, `cancelled_at`.
Index : `idx_preferred_rate_status_expires(status, expires_at)`, `idx_preferred_rate_user(user_id, created_at DESC)`.

#### `exchanges`  *
`id`, `user_id` FK→users, `preferred_rate_request_id` UUID **UNIQUE** FK→preferred_rate_requests, `amount_xof`, `achieved_rate` NUMERIC(18,6), `amount_cny`, `status` CHECK ∈ (`STARTED`,`COMPLETED`,`CANCELLED`), `started_at`, `progress_45_sent_at`, `progress_90_sent_at`, `completed_at`, `cancelled_at`. `idx_exchanges_status`.

#### `notifications` (entité mutable uniquement sur `read_at`)
`id`, `user_id` FK→users, `type` VARCHAR(32) CHECK ∈ 10 valeurs (voir §17.3, `ORDER_EXPIRED` ajouté en `V16`), `channel` VARCHAR(16) DEFAULT `IN_APP` CHECK ∈ (`IN_APP`), `title` VARCHAR(200), `message` VARCHAR(1000), `created_at`, `read_at` (nullable).
Index : `idx_notifications_user_created`, `idx_notifications_user_unread(user_id) WHERE read_at IS NULL`.

#### `audit_logs`
`id`, `actor_id` (UUID, **pas de FK** — l'audit survit à la disparition de l'acteur), `actor_phone` (dénormalisé), `action` VARCHAR(48) NOT NULL (`ck_audit_logs_action_not_blank`), `entity_type` VARCHAR(48), `entity_id` VARCHAR(64), `metadata JSONB`, `ip_address` VARCHAR(45), `user_agent` VARCHAR(255), `created_at`.
Index : `idx_audit_created(created_at DESC)`, `idx_audit_entity(entity_type, entity_id)`, `idx_audit_actor(actor_id, created_at DESC)`, `idx_audit_action(action, created_at DESC)`.

#### `idempotency_keys` — **présente, non utilisée**
`id`, `idem_key`, `user_id` FK→users ON DELETE CASCADE, `endpoint`, `request_hash CHAR(64)`, `response_status`, `response_body JSONB`, `created_at`, `expires_at`, `uq_idempotency(user_id, endpoint, idem_key)`. Aucune entité ni service ne l'exploite.

---

## 10. Module `rate`

**Responsabilité :** produire une cotation de marché brute (`RateProvider`) et la transformer en tarification client complète (`RateEngine`). Publier / historiser le taux manuel courant (`RateAdminService`).

### 10.1 Domaine

| Type | Détail |
|---|---|
| `RateProviderType` (enum) | `MANUAL` (seul actif), `MARKET`, `P2P` (noms réservés, non implémentés). |
| `MarketRate` (record) | `currencyPair`, `cfaPerCny`, `source: RateProviderType`, `effectiveAt`, `rateSourceId: UUID`. Valeur immuable, jamais persistée telle quelle. |
| `RateSource` (`@Entity @Immutable`) | Voir schéma §9.2. `toMarketRate()`. Cloture de période gérée par requête `UPDATE` explicite (voir repo). |
| `RateSource` (fichier `rate/domain/RateSource.java`) et `MarketRate` (fichier `MarketRate.java`) | ⚠️ il existe aussi un fichier `rate/domain/MarketRate.java` — c'est le record ci-dessus. |

### 10.2 `provider`

- **`RateProvider`** (interface / port) — `MarketRate currentRate(String currencyPair)` (lève `RATE_SOURCE_UNAVAILABLE` si aucune cotation courante), `boolean isAvailable(String currencyPair)` (sonde sans exception), `RateProviderType type()`. Constante `DEFAULT_CURRENCY_PAIR = "XOF/CNY"`.
- **`ManualRateProvider`** (`@Component`, seule implémentation ; dépend de `SettingsService` + `Clock`) — **politique de fraîcheur (passe 2 P2-7)** : une cotation courante d'âge > `RATE_MAX_AGE_MINUTES` (mesuré sur `effective_from`) est **STALE** → traitée comme **UNAVAILABLE** (`currentRate` → `RATE_SOURCE_UNAVAILABLE` + log `WARN` ; `isAvailable` → `false`). `RATE_MAX_AGE_MINUTES=0` désactive (défaut, rétrocompatible). `currentRate` lit `RateSourceRepository.findCurrentForPricing(MANUAL, pair)` (**verrou partagé `PESSIMISTIC_READ`**) ; `isAvailable` lit `findByProviderTypeAndCurrencyPairAndEffectiveToIsNull(...)` (**non verrouillé**, pour ne pas entrer en contention avec un pricing en cours). ⚠️ `isAvailable` **ne doit pas** être appelé juste avant `currentRate` dans la même transaction : `RateSource` étant `@Immutable`, Hibernate refuse d'élever le lock mode d'une entité immuable déjà chargée sans verrou (`UnsupportedLockAttemptException`). C'est pourquoi `PreferredRateService` conserve un `try/catch(BusinessException)` autour de `currentRate` plutôt qu'une sonde `isAvailable` préalable.

### 10.3 `engine` — sans dépendance Spring/JPA

| Type | Détail |
|---|---|
| `AmountBasis` (enum) | `XOF` (montant payé connu), `CNY` (montant cible connu). Concept du moteur, découplé de `QuoteDirection`. |
| `MoneyRounding` | `INTERMEDIATE = MathContext(20, HALF_UP)` (calculs intermédiaires). `roundXofUp(v)` → scale 0, `RoundingMode.UP`. `roundCnyDown(v)` → scale 2, `RoundingMode.DOWN`. `normalizeRate(v)` → scale 6, `HALF_UP` (précision de stockage du taux, appliquée une seule fois). |
| `PricingResult` (record) | `marketRate`, `marginPercentage`, `customerRate`, `feePercentage`, `fixedFeeXof`, `feeXof`, `amountXof`, `netAmountXof`, `amountCny`. **Chaque champ répond à une question d'audit distincte** — jamais fusionnés. C'est ce record entier qui est snapshoté dans un `Quote`. |
| `RateEngine` (`@Component`) | `applyMargin(marketRate, marginPct)` = `normalizeRate(marketRate × (1 + marginPct/100))`. `price(basis, amount, marketRate, marginPct, feePct, fixedFeeXof)` → `PricingResult`. Deux chemins internes : `priceFromXof` et `priceFromTargetCny` (voir §19.3). Lève `VALIDATION_ERROR` si `netAmountXof ≤ 0` (« montant insuffisant pour couvrir les frais »). |

### 10.4 `repository` — `RateSourceRepository`

| Méthode | Verrou | Usage |
|---|---|---|
| `findByProviderTypeAndCurrencyPairAndEffectiveToIsNull(...)` | — | affichage admin |
| `findCurrentForPricing(providerType, pair)` | **`PESSIMISTIC_READ`** (`FOR SHARE`) | tout calcul figeant un montant (création de `Quote`). Bloque une publication concurrente jusqu'au commit du calcul → un `Quote` ne fige jamais un taux venant d'être remplacé. |
| `closeCurrent(providerType, pair, now)` | `@Modifying` `UPDATE` | clôt la cotation courante (prend le verrou de ligne exclusif) |
| `findByProviderTypeAndCurrencyPairOrderByEffectiveFromDesc(...)` | — | historique paginé |

### 10.5 `service` — `RateAdminService`

- `publishManualRate(cfaPerCny, note, currencyPair, actorId)` (`@Transactional`) : `closeCurrent(...)` **puis** `save(new RateSource(...))` dans la même transaction. Sérialisation naturelle via le verrou de ligne ; `uq_rate_source_current` = filet ultime. Audit `RATE_SOURCE_PUBLISHED`. Append-only.
- `history(currencyPair, pageable)`.

### 10.6 `web` — `AdminRateController` (`/api/admin/rates`, `@PreAuthorize ADMIN`)

| Méthode | Chemin | Corps / réponse |
|---|---|---|
| `POST` | `/api/admin/rates` | `PublishRateRequest{ cfaPerCny (DecimalMin 0.000001), note? }` → `RateSourceResponse` |
| `GET` | `/api/admin/rates` | historique paginé de `RateSourceResponse` |

**DTO :** `PublishRateRequest`, `RateSourceResponse{ id, providerType, currencyPair, cfaPerCny, effectiveFrom, effectiveTo, note, createdAt }`.
> Il n'existe **aucun endpoint public** exposant le taux courant au client — le client ne voit un taux qu'à travers un `Quote`.

---

## 11. Module `quote`

**Responsabilité :** parcours devis — intention client → `RateProvider` → `RateEngine` → snapshot immuable → transitions contrôlées.

### 11.1 Domaine

| Type | Détail |
|---|---|
| `QuoteDirection` (enum) | `SEND_XOF` (montant XOF connu → CNY calculé), `RECEIVE_CNY` (montant CNY souhaité → XOF calculé). |
| `QuoteStatus` (enum) | `ACTIVE` → `ACCEPTED` \| `EXPIRED` \| `CANCELLED` (tous terminaux). |
| `Quote` (`@Entity`, `version`) | Voir schéma §9.2. **Aucun mutateur** pour les champs financiers (figés au constructeur depuis `PricingResult`). Trois transitions seulement : `accept(now)`, `cancel(now)`, plus `expire`/`effectiveStatus`. |

**Méthodes de `Quote` :**
- `accept(now)` : exige `ACTIVE` ; si `now ≥ expiresAt` → passe `status=EXPIRED` **et** lève `QUOTE_EXPIRED` ; sinon `status=ACCEPTED`, `acceptedAt=now`.
- `cancel(now)` : idem garde d'expiration → `status=CANCELLED`.
- `effectiveStatus(now)` : lecture seule — renvoie `EXPIRED` si `ACTIVE` et délai dépassé, **sans muter** (expiration paresseuse : `expiresAt` reste la source de vérité tant que personne n'a interagi).
- `requireActive()` : lève `INVALID_QUOTE_STATE` sinon.

### 11.2 `repository` — `QuoteRepository`

- `findById` (standard, lecture).
- `findByIdForUpdate(id)` — **`PESSIMISTIC_WRITE`**, requis avant `accept`/`cancel`.

### 11.3 `service` — `QuoteService` (`Clock` injecté)

| Méthode | Transaction | Comportement |
|---|---|---|
| `create(CreateQuoteRequest, userId)` | `@Transactional` | Traduit `direction`→`AmountBasis`, valide qu'**exactement un** des `amountXof`/`amountCny` est fourni (sinon `VALIDATION_ERROR`). Lit la cotation courante (verrouillée, même transaction), les 3 paramètres `DEFAULT_MARGIN/FEE/FIXED_FEE` via `SettingsService`. Appelle `rateEngine.price(...)`. Crée le `Quote` (`expiresAt = now + RATE_LOCK_DURATION_MINUTES`, défaut 30). Audit `QUOTE_CREATED`. Notification `QUOTE_CREATED`. |
| `get(id, userId)` | readOnly | `OwnershipService` → 404 si autre propriétaire. |
| `accept(id, userId)` | `@Transactional` | `findByIdForUpdate` + ownership + `quote.accept(clock.instant())`. Audit `QUOTE_ACCEPTED`. |
| `cancel(id, userId)` | `@Transactional` | idem → `quote.cancel(...)`. Audit `QUOTE_CANCELLED`. |

**Aucune borne min/max n'est appliquée ici** : elles concernent l'ordre, pas le devis (voir `OrderService`).
`toResponse` calcule `status` via `effectiveStatus(now)` — un devis expiré est vu `EXPIRED` sans écriture.

### 11.4 `web` — `QuoteController` (`/api/v1/quotes`, authentifié)

| Méthode | Chemin | Réponse |
|---|---|---|
| `POST` | `/api/v1/quotes` | 201 `QuoteResponse` |
| `GET` | `/api/v1/quotes/{id}` | `QuoteResponse` (404 si pas propriétaire) |
| `POST` | `/api/v1/quotes/{id}/accept` | `QuoteResponse` (409 `QUOTE_EXPIRED` / `INVALID_QUOTE_STATE`) |
| `POST` | `/api/v1/quotes/{id}/cancel` | `QuoteResponse` |

**DTO :**
- `CreateQuoteRequest{ direction (@NotNull), amountXof? (@Positive), amountCny? (@Positive) }`.
- `QuoteResponse{ id, direction, amountXof, amountCny, customerRate, feeXof, netAmountXof, status, createdAt, expiresAt }` — **ne porte jamais `marketRate` ni `marginPercentage`** (données commercialement sensibles).

---

## 12. Module `order`

**Responsabilité :** cycle de vie d'un ordre créé **exclusivement** à partir d'un `Quote` déjà `ACCEPTED`. Point de passage unique de toute transition de statut.

### 12.1 Domaine

| Type | Détail |
|---|---|
| `BeneficiaryType` (enum) | `ALIPAY`, `WECHAT_PAY`, `CHINESE_BANK_ACCOUNT`. |
| `OrderStatus` (enum) | `AWAITING_PAYMENT`, `PAYMENT_SUBMITTED`, `PAYMENT_VERIFIED`, `PROCESSING`, `COMPLETED`, `CANCELLED`, `REJECTED`, `EXPIRED`. |
| `Order` (`@Entity`, `version`) | Colonnes financières = copie figée du `Quote`. `applyStatus(newStatus, now)` (fixe `completedAt`/`cancelledAt`). `markTreasuryReserved()/markTreasuryReleased()`. `setCancellationReason/​setRejectionReason`. **Aucune décision de transition dans l'entité.** |
| `Beneficiary` (`@Entity @Immutable`) | 1‑1 avec l'ordre, snapshot. Constructeur : rejette `CHINESE_BANK_ACCOUNT` sans `bankName`. |
| `OrderStatusHistory` (`@Entity @Immutable`) | Une ligne par transition, append-only. |

### 12.2 `OrderStateMachine` (`@Component`)

Table de transitions unique (`EnumMap<OrderStatus, Set<OrderStatus>>`) :

| Depuis | Vers autorisés |
|---|---|
| `AWAITING_PAYMENT` | `PAYMENT_SUBMITTED`, `CANCELLED`, `EXPIRED` |
| `PAYMENT_SUBMITTED` | `PAYMENT_VERIFIED`, `REJECTED` |
| `PAYMENT_VERIFIED` | `PROCESSING` |
| `PROCESSING` | `COMPLETED` |
| `COMPLETED` / `CANCELLED` / `REJECTED` / `EXPIRED` | ∅ (terminaux) |

`assertTransition(from, to)` → `INVALID_ORDER_STATE` (409) si illégale.
> **`EXPIRED` est autorisé par la machine mais jamais déclenché** : aucun job d'expiration d'ordre n'existe (voir §20).

### 12.3 `repository`

| Repo | Méthodes clés |
|---|---|
| `OrderRepository` | `existsByQuoteId`, `findByUserIdOrderByCreatedAtDesc`, `findByStatusOrderByCreatedAtDesc`, **`findByIdForUpdate` (`PESSIMISTIC_WRITE`)**, `nextReference()` (`SELECT next_order_reference()` natif), `countOpenOrdersByUser(userId)` (statuts `AWAITING_PAYMENT`/`PAYMENT_SUBMITTED`/`PAYMENT_VERIFIED`/`PROCESSING`). |
| `BeneficiaryRepository` | `findByOrderId`. |
| `OrderStatusHistoryRepository` | `findByOrderIdOrderByCreatedAtAsc`. |

### 12.4 `service` — `OrderService`

**Création — `create(CreateOrderRequest, userId)` (`@Transactional`) :**
1. Charge le `Quote`, vérifie ownership (404), `status == ACCEPTED` (sinon `QUOTE_NOT_ACCEPTED`).
2. `existsByQuoteId` → `QUOTE_ALREADY_USED` (défense en profondeur ; `uq_orders_quote` = filet SQL).
3. `assertWithinAmountBounds(quote.amountXof)` : `MIN_ORDER_AMOUNT_CFA` ≤ montant ≤ `MAX_ORDER_AMOUNT_CFA`, sinon `ORDER_AMOUNT_OUT_OF_RANGE`.
4. `assertOpenOrderLimitNotReached(userId)` : `countOpenOrdersByUser ≥ MAX_OPEN_ORDERS_PER_USER` → `TOO_MANY_OPEN_ORDERS`.
5. `reference = nextReference()`, crée `Order` (statut `AWAITING_PAYMENT`, montants copiés du `Quote`), crée `Beneficiary`.
6. `recordHistory(null → AWAITING_PAYMENT)`.
7. Si `TREASURY_RESERVE_ON_ORDER = true` : `treasuryService.reserve(CNY, quote.amountCny, orderId, userId)` **dans la même transaction**, puis `order.markTreasuryReserved()`.
8. Audit `ORDER_CREATED`.

> Le `Quote` **n'est pas** repassé à `ACCEPTED→(consommé)` explicitement : la garde est `uq_orders_quote` + `existsByQuoteId`.

**Consultation :** `get(orderId, userId)` (ownership 404), `listMine(userId, pageable)`, `adminGet(orderId)`, `adminList(status?, pageable)`.

**Annulation client — `cancel(orderId, userId, reason)` :** `findByIdForUpdate` + ownership + `transition(→ CANCELLED)` + `setCancellationReason` + `releaseReservationIfNeeded` + audit `ORDER_CANCELLED`.

**Transitions déclenchées par d'autres modules** (chacune : `findByIdForUpdate` → `transition(...)` → `save` → audit) :

| Méthode | Cible | Effet latéral | Audit |
|---|---|---|---|
| `transitionToPaymentSubmitted` | `PAYMENT_SUBMITTED` | — | `ORDER_PAYMENT_SUBMITTED` |
| `transitionToPaymentVerified` | `PAYMENT_VERIFIED` | — | `ORDER_PAYMENT_VERIFIED` |
| `transitionToRejected(reason)` | `REJECTED` | `setRejectionReason` + `releaseReservationIfNeeded` | `ORDER_REJECTED` |
| `transitionToProcessing` | `PROCESSING` | — | `ORDER_PROCESSING_STARTED` |
| `transitionToCompleted` | `COMPLETED` | — | `ORDER_COMPLETED` |

`transition(order, target, actor, reason)` : `stateMachine.assertTransition` → `order.applyStatus` → `recordHistory(previous → target)`.
`releaseReservationIfNeeded` : si `order.isTreasuryReserved()` → `treasuryService.release(CNY, amountCny, orderId, actor, reason)` + `order.markTreasuryReleased()`.
`getEntityOrThrow(orderId)` / `getBeneficiaryOrThrow(orderId)` : lecture pour `payment`/`settlement`.

### 12.5 `web`

**`OrderController`** (`/api/v1/orders`, authentifié) :

| Méthode | Chemin | Réponse |
|---|---|---|
| `POST` | `/api/v1/orders` | 201 `OrderDetailResponse` (corps `CreateOrderRequest{ quoteId, beneficiary, note? }`) |
| `GET` | `/api/v1/orders` | page de `OrderSummaryResponse` |
| `GET` | `/api/v1/orders/{id}` | `OrderDetailResponse` (404 si pas propriétaire) |
| `POST` | `/api/v1/orders/{id}/cancel` | `OrderDetailResponse` (corps `CancelOrderRequest{ reason }`) |

**`AdminOrderController`** (`/api/admin/orders`, `@PreAuthorize ADMIN`) : `GET /` (filtre `status?`), `GET /{id}`.

**DTO :** `BeneficiaryRequest{ type, fullName, identifier, bankName?, bankBranch? }`, `BeneficiaryResponse`, `CreateOrderRequest`, `CancelOrderRequest`, `OrderDetailResponse{ id, reference, quoteId, status, amountXof, amountCny, customerRate, feeXof, netAmountXof, note, cancellationReason, rejectionReason, beneficiary, statusHistory[], createdAt, updatedAt, completedAt, cancelledAt }`, `OrderStatusHistoryResponse{ fromStatus, toStatus, changedBy, reason, createdAt }`, `OrderSummaryResponse{ id, reference, status, amountXof, amountCny, createdAt }`. **`OrderDetailResponse` porte aussi `paymentDeadlineAt`** (passe 2). `OrderService.expireIfOverdue(orderId, asOf)` / `findOverdueOrderIds(asOf)` : primitives d'expiration appelées par `OrderExpirationService` — la transition `AWAITING_PAYMENT → EXPIRED` reste dans `OrderService`/`OrderStateMachine`.

> **Aucun `Idempotency-Key` n'est exigé** sur `POST /api/v1/orders` (l'en-tête est accepté par CORS mais non traité) — la protection contre la double création repose sur `uq_orders_quote`.

---

## 13. Module `payment`

**Responsabilité :** déclaration client d'un paiement XOF effectué **hors plateforme** (MVP entièrement manuel) et revue admin (confirmation / rejet).

### 13.1 Domaine

| Type | Détail |
|---|---|
| `PaymentMethod` (enum) | `MOBILE_MONEY` (seul activable via `ENABLED_PAYMENT_METHODS`), `WAVE`, `BANK_TRANSFER` (réservés). |
| `PaymentStatus` (enum) | `SUBMITTED` → `CONFIRMED` \| `REJECTED` (terminaux). Un ordre porte **au plus un** paiement (`uq_payments_order`) — un rejet termine l'ordre, pas de resoumission. |
| `Payment` (`@Entity`, `version`) | `expectedAmountXof` (copié de l'ordre), `receivedAmountXof` (déclaré). `confirm(reviewerId, now)` / `reject(reviewerId, reason, now)` — gardés par `requireSubmitted()` → `INVALID_PAYMENT_STATE` sinon. |
| `PaymentProof` (`@Entity @Immutable`) | Métadonnées uniquement, aucun binaire en base. |

### 13.2 `repository` — `PaymentRepository`

`findByOrderId`, `existsByOrderId`, `findByStatusOrderBySubmittedAtAsc`, **`findByIdForUpdate` (`PESSIMISTIC_WRITE`)**.
`PaymentProofRepository` : `findByPaymentIdOrderByUploadedAtAsc`, `countByPaymentId`.

### 13.3 `service` — `PaymentService`

**Client :**

| Méthode | Comportement |
|---|---|
| `submit(orderId, SubmitPaymentRequest, userId)` | Ownership 404. Exige `order.status == AWAITING_PAYMENT` (sinon `INVALID_ORDER_STATE`). `existsByOrderId` → `INVALID_PAYMENT_STATE`. `assertMethodEnabled` : la méthode doit figurer dans `ENABLED_PAYMENT_METHODS` (sinon `VALIDATION_ERROR`). Crée le `Payment` (`SUBMITTED`), puis `orderService.transitionToPaymentSubmitted(orderId, userId)` **même transaction**. Audit `PAYMENT_SUBMITTED`, notif `PAYMENT_SUBMITTED`. |
| `uploadProof(paymentId, fileName, contentType, bytes, userId)` | Ownership 404. Exige `payment.status == SUBMITTED`. Plafond `MAX_PROOFS_PER_PAYMENT` (sinon `InvalidFileException`). `fileValidator.validate(contentType, bytes, MAX_PROOF_FILE_SIZE_BYTES)` (magic bytes). `fileStorageService.store("payment-proofs", ...)`. Persiste `PaymentProof`. Audit `PAYMENT_PROOF_UPLOADED`. |
| `get(paymentId, userId)` | Ownership 404 (via l'ordre). |
| `downloadProof(paymentId, proofId, userId)` | Ownership 404 ; `fileStorageService.load(storageKey)`. |

**Admin :**

| Méthode | Comportement |
|---|---|
| `pending(pageable)` | Paiements `SUBMITTED`, plus ancien d'abord. |
| `adminGet(paymentId)` / `adminDownloadProof(paymentId, proofId)` | — |
| `confirm(paymentId, actorId)` | `findByIdForUpdate`. Si `REQUIRE_PAYMENT_PROOF` et 0 preuve → `INVALID_PAYMENT_PROOF`. `payment.confirm(...)`. `orderService.transitionToPaymentVerified(...)`. **`treasuryService.deposit(XOF, receivedAmountXof, actor, ...)`**. Audit `PAYMENT_CONFIRMED`, notif `PAYMENT_CONFIRMED`. |
| `reject(paymentId, reason, actorId)` | `findByIdForUpdate`. `payment.reject(...)`. `orderService.transitionToRejected(orderId, actor, reason)` (⇒ libère la réservation CNY). Audit `PAYMENT_REJECTED`. |

### 13.4 `web`

**`PaymentController`** (`@RequestMapping("/api/v1")`, authentifié) :

| Méthode | Chemin | Note |
|---|---|---|
| `POST` | `/api/v1/orders/{orderId}/payments` | 201 `PaymentResponse` |
| `POST` | `/api/v1/payments/{paymentId}/proofs` | `multipart/form-data`, param `file`, 201 `PaymentProofResponse` |
| `GET` | `/api/v1/payments/{paymentId}` | `PaymentResponse` |
| `GET` | `/api/v1/payments/{paymentId}/proofs/{proofId}` | binaire — `Content-Disposition: attachment`, `X-Content-Type-Options: nosniff`, `application/octet-stream` |

**`AdminPaymentController`** (`/api/admin/payments`, `@PreAuthorize ADMIN`) : `GET /pending`, `GET /{id}`, `GET /{id}/proofs/{proofId}`, `POST /{id}/confirm`, `POST /{id}/reject` (corps `RejectPaymentRequest{ reason }`).

**DTO :** `SubmitPaymentRequest{ method, receivedAmountXof (@Positive), transactionReference, payerPhone? }`, `RejectPaymentRequest{ reason }`, `PaymentResponse{ id, orderId, method, status, expectedAmountXof, receivedAmountXof, transactionReference, payerPhone, rejectionReason, proofs[], submittedAt, confirmedAt, rejectedAt }`, `PaymentProofResponse{ id, fileName, contentType, sizeBytes, uploadedAt }`.

---

## 14. Module `settlement`

**Responsabilité :** exécution manuelle du décaissement CNY au bénéficiaire. `Payment` = le client a payé en XOF ; `Settlement` = l'entreprise a réglé en Chine.

### 14.1 Domaine

| Type | Détail |
|---|---|
| `SettlementStatus` (enum) | `PENDING` → `EXECUTED` (terminal). |
| `Settlement` (`@Entity`, `version`) | Snapshot bénéficiaire (dupliqué, lisible sans jointure). `method: BeneficiaryType`. `execute(reference, notes, actorId, now)` — garde `PENDING` sinon `INVALID_SETTLEMENT_STATE`. |
| `SettlementProof` (`@Entity @Immutable`) | Métadonnées, comme `PaymentProof`. |

### 14.2 `repository` — `SettlementRepository`

`findByOrderId`, `existsByOrderId`, `findByStatusOrderByCreatedAtAsc`, **`findByIdForUpdate` (`PESSIMISTIC_WRITE`)**.
`SettlementProofRepository` : `findBySettlementIdOrderByUploadedAtAsc`, `countBySettlementId`.

### 14.3 `service` — `SettlementService`

| Méthode | Comportement |
|---|---|
| `create(orderId, actorId)` | Exige `order.status == PAYMENT_VERIFIED` (sinon `INVALID_ORDER_STATE`). `existsByOrderId` → `INVALID_SETTLEMENT_STATE`. Snapshot depuis `Beneficiary`. `settlement.status = PENDING`. **`orderService.transitionToProcessing(orderId, actorId)`** même transaction (« Payment CONFIRMED → Settlement créé » atomique). Audit `SETTLEMENT_CREATED`. |
| `execute(settlementId, reference, notes, actorId)` | `findByIdForUpdate`. Si `REQUIRE_PAYMENT_PROOF` et 0 preuve → `INVALID_PAYMENT_PROOF`. `settlement.execute(...)`. **`treasuryService.consume(CNY, amountCny, orderId, actor)` uniquement si `order.isTreasuryReserved()`** (miroir de `OrderService.releaseReservationIfNeeded` — ne jamais consommer le pool réservé partagé pour un ordre jamais réservé ; sinon `WARN` + saut). `orderService.transitionToCompleted(orderId, actor)`. Audit `SETTLEMENT_EXECUTED`. En-tête `Idempotency-Key` géré au contrôleur. |
| `uploadProof(...)` | Exige `PENDING`. `fileValidator.validate(...)`, `fileStorageService.store("settlement-proofs", ...)`. |
| `downloadProof`, `get`, `getByOrder`, `pending(pageable)` | — |

### 14.4 `web` — `AdminSettlementController` (`/api/admin`, `@PreAuthorize ADMIN`)

| Méthode | Chemin |
|---|---|
| `POST` | `/api/admin/orders/{orderId}/settlement` → 201 |
| `GET` | `/api/admin/settlements/pending` |
| `GET` | `/api/admin/settlements/{id}` |
| `POST` | `/api/admin/settlements/{id}/proofs` (multipart) → 201 |
| `GET` | `/api/admin/settlements/{id}/proofs/{proofId}` (binaire, attachment) |
| `POST` | `/api/admin/settlements/{id}/execute` (corps `ExecuteSettlementRequest{ settlementReference, notes? }`) |

**DTO :** `ExecuteSettlementRequest`, `SettlementResponse{ id, orderId, status, amountCny, method, beneficiary*, settlementReference, notes, executedBy, proofs[], createdAt, executedAt }`, `SettlementProofResponse`.

---

## 15. Module `treasury`

**Responsabilité :** point d'entrée **unique** de toute mutation de solde de trésorerie. Aucune autre classe n'écrit sur `TreasuryAccount`.

### 15.1 Domaine

| Type | Détail |
|---|---|
| `Currency` (enum) | `XOF`, `CNY`. |
| `TreasuryTransactionType` (enum) | `DEPOSIT`, `WITHDRAWAL`, `RESERVATION`, `RELEASE`, `ADJUSTMENT`. |
| `TreasuryAccount` (`@Entity`, `version`) | `available() = balance − reservedBalance`. Méthodes : `deposit`, `reserve`, `release`, `consume` (balance ET reserved), `withdrawUnreserved`, `adjust(delta)`. **`consume`/`release` gardent l'invariant `amount ≤ reservedBalance`** : sinon `BusinessException(INSUFFICIENT_TREASURY)` levée **avant** toute mutation (défense en profondeur en amont des `CHECK` SQL — évite un `409 DUPLICATE_RESOURCE` trompeur et une décrémentation silencieuse du pool réservé partagé). |
| `TreasuryTransaction` (`@Entity @Immutable`) | Ledger ; `balanceAfter`/`reservedAfter` capturent le solde résultant. |

**Vocabulaire ↔ types :** `Reserved` = `RESERVATION` ; `Released` = `RELEASE` ; `Consumed` = `WITHDRAWAL` **avec `order_id`** ; alimentation/correction = `DEPOSIT`/`ADJUSTMENT` (pas de type `CONSUMPTION` distinct).

### 15.2 `repository`

- `TreasuryAccountRepository` : `findByCurrency`, **`findByCurrencyForUpdate` (`PESSIMISTIC_WRITE`, `FOR UPDATE`)** — requis avant toute mutation.
- `TreasuryTransactionRepository` : `findByAccountIdOrderByCreatedAtDesc`, `findByOrderId`, `countByOrderIdAndType`.

### 15.3 `service` — `TreasuryService`

Toutes les mutations suivent : (1) `loadForUpdate(currency)`, (2) vérifie invariants, (3) mute, (4) écrit une `TreasuryTransaction`, (5) audit — **même transaction**.

| Méthode | Effet | Garde | Audit |
|---|---|---|---|
| `reserve(currency, amount, orderId, by)` | `reservedBalance += amount` | `available() ≥ amount` sinon **`INSUFFICIENT_TREASURY` (409)** | `TREASURY_RESERVED` |
| `release(currency, amount, orderId, by, reason)` | `reservedBalance -= amount` | — | `TREASURY_RELEASED` |
| `consume(currency, amount, orderId, by)` | `balance -= amount` ET `reservedBalance -= amount` (type `WITHDRAWAL`) | — | `TREASURY_CONSUMED` |
| `deposit(currency, amount, by, reason)` | `balance += amount` | — | `TREASURY_DEPOSIT` |
| `adjust(currency, delta, by, reason)` | `balance += delta` (delta signé) ; `amount` du ledger = `delta.abs()` | `balance + delta ≥ 0` sinon `VALIDATION_ERROR` | `TREASURY_ADJUSTMENT` |
| `snapshot(currency)` | lecture | `RESOURCE_NOT_FOUND` si compte absent | — |

### 15.4 `web` — `AdminTreasuryController` (`/api/admin/treasury`, `@PreAuthorize ADMIN`)

| Méthode | Chemin | Note |
|---|---|---|
| `GET` | `/api/admin/treasury/accounts/{currency}` | `TreasuryAccountResponse` |
| `GET` | `/api/admin/treasury/accounts/{currency}/transactions` | ledger paginé (plus récent d'abord) |
| `POST` | `/api/admin/treasury/deposit` | corps `TreasuryAdjustmentRequest`, `amount` pris en `.abs()` |
| `POST` | `/api/admin/treasury/adjust` | corps `TreasuryAdjustmentRequest`, `amount` signé, motif obligatoire |

Les mouvements liés aux ordres (`RESERVATION`/`RELEASE`/`WITHDRAWAL`) **ne transitent jamais** par ces endpoints — déclenchés par `order`/`settlement`.

**DTO :** `TreasuryAdjustmentRequest{ currency, amount, reason }`, `TreasuryAccountResponse{ id, currency, balance, reservedBalance, available, lowThreshold, updatedAt }`, `TreasuryTransactionResponse{ id, accountId, type, amount, balanceAfter, reservedAfter, orderId, performedBy, reason, createdAt }`.

---

## 16. Module `storage`

**Responsabilité :** port de stockage de fichiers + validation stricte.

| Classe | Rôle |
|---|---|
| `FileStorageService` (interface) | `store(directory, originalFileName, contentType, byte[])` → `StoredFile` ; `load(storageKey)` → `Resource` ; `delete(storageKey)`. |
| `LocalFileStorageService` (`@Component`) | Disque local sous `app.storage.root-dir` (créé au démarrage, hors racine web). **`storageKey` entièrement généré** : `{directory}/{yyyy}/{MM}/{uuid}.{ext}` — jamais dérivé du nom client ⇒ path traversal structurellement impossible. `resolveWithinRoot` refuse toute résolution hors racine (filet supplémentaire). Calcule le SHA-256 du contenu. |
| `FileValidator` (`@Component`) | `validate(declaredContentType, content, maxSizeBytes)` : rejette vide / trop gros / type hors allowlist (`image/jpeg|png|webp`, `application/pdf`) ; **inspecte les magic bytes** (JPEG `FF D8 FF`, PNG, PDF `%PDF`, WEBP `RIFF....WEBP`) et rejette si le contenu réel ≠ type déclaré. |
| `FileNameSanitizer` (package-private) | `extensionFor(contentType)` (map fermée, `bin` par défaut) ; `sanitizeDisplayName(name)` : retire séparateurs de chemin et caractères hors `[a-zA-Z0-9._-]`, tronque à 200. |
| `StoredFile` (record) | `storageKey, fileName, contentType, sizeBytes, checksumSha256`. |
| `InvalidFileException` | `BusinessException(INVALID_PAYMENT_PROOF)` (400). |
| `StorageException` | `RuntimeException` (échec technique). |

---

## 17. Modules `wallet`, `preferredrate`, `notification`

### 17.1 `wallet` — solde interne XOF par utilisateur

**Ce n'est pas un compte bancaire.** Même modèle que `TreasuryAccount` (`Available = balance − reserved_balance`), scopé par utilisateur.

| Élément | Détail |
|---|---|
| `Wallet` (`@Entity`, `version`) | `deposit`, `reserve`, `release`, `consume`. Un wallet par utilisateur (`uq_wallets_user`). `consume`/`release` gardent `amount ≤ reservedBalance` → `BusinessException(INSUFFICIENT_WALLET_BALANCE)` sinon (symétrique de `TreasuryAccount`). |
| `WalletTransaction` (`@Entity @Immutable`) | Ledger ; types `CREDIT`/`DEBIT`/`RESERVE`/`RELEASE` ; `referenceId` libre. |
| `WalletRepository` | `findByUserId`, **`findByUserIdForUpdate` (`PESSIMISTIC_WRITE`)**. |
| `WalletService` | `deposit(userId, amount, reason)` (**aucun fournisseur de paiement réel branché** — brique de service), `reserve(userId, amount, refId, reason)` (→ `INSUFFICIENT_WALLET_BALANCE` si `available < amount`), `release(...)`, `debit(...)` (consomme une réservation), `snapshot(userId)` (crée le wallet au 1ᵉʳ accès, protégé par `uq_wallets_user`), `transactions(userId, pageable)`. |
| `WalletController` (`/api/v1/wallet`, authentifié) | `GET /` (crée si absent, solde 0), `GET /transactions`. **Pas d'endpoint de dépôt** : `deposit` n'existe qu'en méthode de service (appelée par les tests / une future phase). Ownership implicite : toujours `currentUser`. |

### 17.2 `preferredrate` — échange automatique au taux cible

| Élément | Détail |
|---|---|
| `PreferredRateDirection` (enum) | `XOF_TO_CNY` uniquement. |
| `PreferredRateStatus` (enum) | `ACTIVE` → `EXECUTED` \| `EXPIRED` \| `CANCELLED`. |
| `ExchangeStatus` (enum) | `STARTED` → `COMPLETED` \| `CANCELLED`. |
| `PreferredRateRequest` (`@Entity`, `version`) | `amountXof`, `targetRate` (1 CNY = X XOF). Validité **3 jours** (`VALIDITY = Duration.ofDays(3)`). `execute(now, achievedRate, exchangeId)`, `expire(now)`, `cancel(now)` — gardées `requireActive()` → `INVALID_PREFERRED_RATE_STATE`. `isPastDeadline(now)`. |
| `Exchange` (`@Entity`, `version`) | Cycle de progression **simulé** : `PROGRESS_INTERVAL = 45 min`, `MAX_DURATION = 2 h`. Marqueurs `progress45SentAt`/`progress90SentAt`/`completedAt` garantissent aucune double notification. `isProgress45Due`/`isProgress90Due`/`isCompletionDue`. |
| `PreferredRateRequestRepository` | `findByUserIdOrderByCreatedAtDesc`, **`findByIdForUpdate`**, `findIdsByStatus(status)` (candidats scheduler). |
| `ExchangeRepository` | `findByPreferredRateRequestId`, `findIdsByStatus`, **`findByIdForUpdate`**. |
| `PreferredRateService` | `create(...)` : persiste la demande **puis `walletService.reserve(userId, amountXof, requestId, ...)`** (immobilisation immédiate, jamais deux demandes sur le même solde). `get`/`listMine` (**`@Transactional` non-readOnly** : `toResponse` peut prendre le verrou `FOR SHARE` sur la cotation courante). `cancel(...)` : `request.cancel` + `walletService.release(...)`. `processOne(requestId)` (piloté par scheduler, 1 transaction, verrou ligne) : si `isPastDeadline` → `expire` (release + notif `PREFERRED_RATE_EXPIRED`) ; sinon si `currentPricing.customerRate() ≥ targetRate` → `trigger` (crée `Exchange`, `walletService.debit(...)`, `request.execute(...)`, notifs `PREFERRED_RATE_REACHED` + `EXCHANGE_STARTED`). `progressOne(exchangeId)` : notifs `EXCHANGE_PROGRESS` à T+45/T+90, `EXCHANGE_COMPLETED` à T+2 h. |
| `PreferredRateController` (`/api/v1/preferred-rates`, authentifié) | `POST /` (201), `GET /`, `GET /{id}`, `POST /{id}/cancel`. Le déclenchement est **exclusivement** piloté par le scheduler. |

**DTO :** `CreatePreferredRateRequest{ direction, amountXof (@DecimalMin 0.01), targetRate (@DecimalMin 0.000001) }`, `PreferredRatePhase` (enum : `WAITING`, `EXCHANGE_IN_PROGRESS`, `EXCHANGE_COMPLETED`, `EXPIRED`, `CANCELLED`), `PreferredRateRequestResponse{ ..., currentRate (recalculé, null hors WAITING), gap = currentRate − targetRate, phase, exchange: ExchangeSummaryResponse? }`, `ExchangeSummaryResponse{ ..., stage (STARTED/PROGRESS_45/PROGRESS_90/COMPLETED), deadlineAt, nextUpdateAt }`. **Rien n'est calculé côté frontend.**

### 17.3 `notification` — messages internes (`IN_APP` uniquement)

| Élément | Détail |
|---|---|
| `NotificationChannel` (enum) | `IN_APP`. |
| `NotificationType` (enum) | `QUOTE_CREATED`, `PAYMENT_SUBMITTED`, `PAYMENT_CONFIRMED`, `EXCHANGE_STARTED`, `EXCHANGE_PROGRESS`, `EXCHANGE_COMPLETED`, `PREFERRED_RATE_REACHED`, `PREFERRED_RATE_EXPIRED`, `EXCHANGE_CANCELLED`. |
| `Notification` (`@Entity`) | Immuable sauf `readAt` (transition unique `null → instant` via `markRead`). |
| `NotificationService` | `create(userId, type, title, message)` — **`@Transactional(REQUIRES_NEW)` + exception d'écriture absorbée** (log `ERROR`, retourne `null`) : une notification est un effet de bord, jamais une condition de succès ; un incident sur la table `notifications` ne fait plus échouer ni rollback la transaction financière appelante (`quote`/`payment`/`preferredrate`). `listMine`, `unreadCount`, `markRead(id, userId)` (ownership 404 → `NOTIFICATION_NOT_FOUND`). |
| `NotificationController` (`/api/v1/notifications`, authentifié) | `GET /`, `GET /unread-count`, `POST /{id}/read`. |

**DTO :** `NotificationResponse{ id, type, title, message, createdAt, readAt }`, `UnreadCountResponse{ unreadCount }`.

---

## 18. Modules transverses

### 18.1 `user`

| Élément | Détail |
|---|---|
| `RoleCode` (enum) | `USER`, `ADMIN`. `authority()` → `"ROLE_" + name()`. |
| `UserStatus` (enum) | `ACTIVE`, `BLOCKED`. |
| `Role` (`@Entity`, table `roles` en lecture seule) | référentiel fermé. |
| `User` (`@Entity extends AuditableEntity`) | `roles` en `@ManyToMany(EAGER)` (référentiel de 2 lignes). Comportement : `isActive`, `isBlocked`, `hasRole`, `block(reason, actorId, at)`, `unblock`, `addRole`, `recordLogin`, `fullName`. `passwordHash` jamais exposé (aucun DTO ne le porte). |
| `UserRepository` | `findByPhone`, `existsByPhone`, `existsByEmail`, `search(status?, pattern, pageable)` (motif LIKE **jamais `null`** — `"%"` = tout, pour éviter un échec JDBC de préparation), `countByRole(code)`. |
| `RoleRepository` | `findByCode`. |
| `UserService` | `search(...)`, `findById(...)` (→ `AdminUserDetail`), `block(id, req, actorId)` (interdit l'auto-blocage → `VALIDATION_ERROR` ; idempotent si déjà bloqué ; audit `USER_BLOCKED`), `unblock(id, actorId)` (idempotent ; audit `USER_UNBLOCKED`). |
| `AdminAccountSeeder` | voir §4. |
| `UserMapper` (MapStruct) | `toResponse(User)` → `UserResponse`. **Pas de sens inverse** (jamais construire une entité par recopie d'entrée client). |

**DTO :** `UserResponse{ id, phone, firstName, lastName, email, status, roles, createdAt, lastLoginAt }`, `AdminUserSummary{ id, phone, fullName, status, createdAt, orderCount, totalAmountCfa }`, `AdminUserDetail{ ..., blockedAt, blockedReason, orderCount, totalAmountCfa }`, `BlockUserRequest{ reason }`.
> ⚠️ `orderCount` et `totalAmountCfa` sont **codés en dur à `0L` / `BigDecimal.ZERO`** dans `UserService.toSummary`/`toDetail` — jamais calculés (voir §20).

### 18.2 `auth`

| Élément | Détail |
|---|---|
| `AuthController` (`/api/auth`) | `POST /register` → 201 `AuthResponse` ; `POST /login` → `AuthResponse` ; `GET /me` (authentifié) → `UserResponse`. |
| `AuthService` (`@Transactional`) | `register` : normalise le téléphone, `existsByPhone` → `DuplicateResourceException` (message explicite assumé pour l'inscription, contrairement au login), hash BCrypt, rôle `USER`, audit `USER_REGISTERED`, renvoie un JWT. `login` : **message générique identique** (numéro inexistant ou mauvais mot de passe) → `INVALID_CREDENTIALS` (anti-énumération) ; compte bloqué → `UserBlockedException` (403) ; sinon `recordLogin`, audit `USER_LOGIN_SUCCESS`/`USER_LOGIN_FAILED`. `me(userId)`. |

**DTO :** `RegisterRequest{ phone (@PhoneNumber), password (@Size 8..72), firstName (@Size ≤80), lastName }` (pas de règle de « complexité » — choix NIST SP 800-63B), `LoginRequest{ phone, password }`, `AuthResponse{ accessToken, tokenType="Bearer", expiresInSeconds, user }`.

### 18.3 `settings`

| Élément | Détail |
|---|---|
| `SettingType` (enum) | `STRING`, `INTEGER`, `DECIMAL`, `BOOLEAN`. |
| `SettingKey` (enum, **catalogue fermé**) | Le nom de la constante = la clé en base. 16 valeurs (voir table ci-dessous). |
| `SystemSetting` (`@Entity`, PK = `setting_key`, `version`, `@LastModifiedDate`) | `updateValue(newValue, actorId)`. |
| `SystemSettingRepository` | `findAllByPublicSettingTrue`, `findAllByOrderBySettingKeyAsc`. |
| `SettingsService` | Dépend de `AuditService`. Cache Caffeine `Cache<SettingKey,String>`, **TTL 5 min**, invalidé à l'écriture. Lecture typée : `getString`, `getInt`, `getLong`, `getDecimal`, `getBoolean` (**parse strict** — `true`/`false` uniquement, sinon `INTERNAL_ERROR`), `getList` (split virgule), `getMinutes`. Clé absente en base → `INTERNAL_ERROR` (jamais de défaut implicite sur un paramètre financier). `update(key, rawValue, actorId)` : valide le format selon `SettingType`, refuse un `DECIMAL` négatif, `saveAndFlush`, invalide le cache, puis **`auditService.record(..., SETTING_UPDATED, "SystemSetting", key, {previousValue, newValue})`**. |
| `AdminSettingsController` (`/api/admin/settings`, `@PreAuthorize ADMIN`) | `GET /` → `List<SettingResponse>` ; `PUT /{key}` (corps `UpdateSettingRequest{ value }`). |
| `PublicSettingsController` (`/api/settings`) | `GET /public` → `PublicSettingsResponse` (les clés `is_public = true`). |

**Clés `system_settings` (V4 + V8) :**

| Clé | Type | Valeur initiale | Public | Consommée par |
|---|---|---|---|---|
| `MIN_ORDER_AMOUNT_CFA` | DECIMAL | `10000` | ✅ | `OrderService` |
| `MAX_ORDER_AMOUNT_CFA` | DECIMAL | `2000000` | ✅ | `OrderService` |
| `RATE_LOCK_DURATION_MINUTES` | INTEGER | `30` | ✅ | `QuoteService` (validité du devis) |
| `ORDER_AUTO_EXPIRE_ENABLED` | BOOLEAN | `true` | ❌ | *non lue* (pas de job d'expiration) |
| `REQUIRE_PAYMENT_PROOF` | BOOLEAN | `true` | ✅ | `PaymentService.confirm`, `SettlementService.execute` |
| `TREASURY_RESERVE_ON_ORDER` | BOOLEAN | `true` | ❌ | `OrderService.create` |
| `MAX_PROOF_FILE_SIZE_BYTES` | INTEGER | `5242880` | ✅ | `PaymentService`, `SettlementService` |
| `MAX_PROOFS_PER_PAYMENT` | INTEGER | `3` | ✅ | `PaymentService.uploadProof` |
| `ENABLED_PAYMENT_METHODS` | STRING | `MOBILE_MONEY` | ✅ | `PaymentService.submit` |
| `MAX_OPEN_ORDERS_PER_USER` | INTEGER | `3` | ❌ | `OrderService.create` |
| `DEFAULT_MARGIN_PERCENTAGE` | DECIMAL | `1.5000` | ❌ | `QuoteService`, `PreferredRateService` |
| `DEFAULT_FEE_PERCENTAGE` | DECIMAL | `0.0000` | ❌ | idem |
| `DEFAULT_FIXED_FEE_XOF` | DECIMAL | `0` | ❌ | idem |
| `ORDER_PAYMENT_WINDOW_MINUTES` | INTEGER | `720` | ✅ | `OrderService.create` (fige `payment_deadline_at`) |
| `PAYMENT_AMOUNT_TOLERANCE_XOF` | DECIMAL | `0` | ✅ | `PaymentService.submit` (écart toléré reçu/attendu) |
| `RATE_MAX_AGE_MINUTES` | INTEGER | `0` (désactivé) | ❌ | `ManualRateProvider` (fraîcheur du taux) |

**`PublicSettingsResponse`** : `minOrderAmountCfa`, `maxOrderAmountCfa`, `rateLockDurationMinutes`, `maxProofFileSizeBytes`, `maxProofsPerPayment`, `enabledPaymentMethods[]`, `requirePaymentProof`.

### 18.4 `audit`

| Élément | Détail |
|---|---|
| `AuditAction` (enum, catalogue fermé) | Comptes : `USER_REGISTERED`, `USER_LOGIN_SUCCESS`, `USER_LOGIN_FAILED`, `USER_BLOCKED`, `USER_UNBLOCKED`. Taux : `EXCHANGE_RATE_CREATED` (hérité, non déclenché), `RATE_SOURCE_PUBLISHED`. Devis : `QUOTE_CREATED`, `QUOTE_ACCEPTED`, `QUOTE_CANCELLED`, `QUOTE_EXPIRED`. Ordres : `ORDER_CREATED`, `ORDER_CANCELLED`, `ORDER_REJECTED`, `ORDER_EXPIRED`, `ORDER_PAYMENT_SUBMITTED`, `ORDER_PAYMENT_VERIFIED`, `ORDER_PROCESSING_STARTED`, `ORDER_COMPLETED`. Paiements : `PAYMENT_SUBMITTED`, `PAYMENT_PROOF_UPLOADED`, `PAYMENT_VERIFIED`, `PAYMENT_CONFIRMED`, `PAYMENT_REJECTED`. Règlement : `SETTLEMENT_CREATED`, `SETTLEMENT_EXECUTED`. Trésorerie : `TREASURY_DEPOSIT`, `TREASURY_WITHDRAWAL`, `TREASURY_ADJUSTMENT`, `TREASURY_RESERVED`, `TREASURY_RELEASED`, `TREASURY_CONSUMED`. Config : `SETTING_UPDATED`. |
| `AuditLog` (`@Entity extends BaseEntity`) | `createdAt` fixé **explicitement** par le service (pas d'auditing JPA). `metadata` en `JSONB` (`@JdbcTypeCode(SqlTypes.JSON)`). Aucune FK vers `users`. |
| `AuditLogRepository` | `search(actorId?, action?, entityType?, entityId?, from?, to?, pageable)` — filtres combinables, tri `createdAt DESC`. |
| `AuditService` | **`record(...)` et `recordSystem(...)` en `@Transactional(REQUIRES_NEW)`** : un échec d'audit ne fait jamais échouer l'opération métier, et un rollback métier n'efface pas la trace de la tentative. Exception d'écriture **absorbée** (log local). Pas d'auto-invocation entre les deux méthodes (le proxy Spring). `search(...)` en readOnly. |
| `AdminAuditLogController` (`/api/admin/audit-logs`, `@PreAuthorize ADMIN`) | `GET /` — **lecture seule**, aucun endpoint d'écriture/suppression. Page par défaut 50. |

**DTO :** `AuditLogResponse{ id, actorId, actorPhone, action, entityType, entityId, metadata, ipAddress, createdAt }`.

### 18.5 `admin`

`AdminUserController` (`/api/admin/users`) et `AdminAuditLogController` (`/api/admin/audit-logs`). Les autres contrôleurs `/api/admin/**` (rates, orders, payments, settlements, treasury) vivent dans leur module respectif. Aucune logique métier ici. **Pas de `AdminDashboardController` ni de module `dashboard`/`risk`** (prévus dans ARCHITECTURE.md, non implémentés).

---

## 19. Planificateurs, concurrence, calcul monétaire

### 19.1 Planificateurs

`@EnableScheduling` sur `ConverterApplication`. **Deux composants planifiés** : `PreferredRateScheduler` (`preferredrate/scheduler/`) et `OrderExpirationScheduler` (`order/scheduler/`, passe 2).

| Tâche | Déclenchement | Rôle |
|---|---|---|
| `evaluateActiveRequests()` | `fixedDelay = ${preferred-rate.scheduler.fixed-delay-ms:30000}`, `initialDelay = ${...initial-delay-ms:30000}` | `activeRequestIds()` → boucle → `processOne(id)` (1 transaction / demande, verrou ligne). Exception isolée par élément. |
| `progressActiveExchanges()` | mêmes délais | `startedExchangeIds()` → boucle → `progressOne(id)`. |

`fixedDelay` (pas `fixedRate`) : le passage suivant ne démarre qu'après la fin du précédent — pas de chevauchement au sein d'une instance. Le verrou pessimiste couvre le multi-instance et les actions utilisateur concurrentes.
> Les clés `preferred-rate.scheduler.*` **ne figurent dans aucun `application.yml`** : les défauts (30 000 ms) s'appliquent.

**Expiration d'ordre (passe 2)** : `OrderExpirationScheduler` (`fixedDelay` 60 s, config `order.expiration.scheduler.*`) → `OrderExpirationService.expireOverdue(asOf)` → `OrderService.expireIfOverdue(orderId, asOf)` : par ordre, `findByIdForUpdate` + re-vérif `AWAITING_PAYMENT` + `asOf ≥ payment_deadline_at` → `EXPIRED` + `releaseReservationIfNeeded` + audit `ORDER_EXPIRED` + notification. Idempotent, rejouable, piloté par `ORDER_AUTO_EXPIRE_ENABLED`. `payment_deadline_at` = `created_at + ORDER_PAYMENT_WINDOW_MINUTES` (720 min), **stockée** (immuable par ordre). **Le `Quote`** garde son expiration paresseuse (aucune ressource réservée).

### 19.2 Concurrence & intégrité — inventaire

**Verrous pessimistes (`SELECT ... FOR UPDATE` / `FOR SHARE`) :**

| Repository | Méthode | Mode |
|---|---|---|
| `RateSourceRepository` | `findCurrentForPricing` | `PESSIMISTIC_READ` (`FOR SHARE`) |
| `QuoteRepository` | `findByIdForUpdate` | `PESSIMISTIC_WRITE` |
| `OrderRepository` | `findByIdForUpdate` | `PESSIMISTIC_WRITE` |
| `PaymentRepository` | `findByIdForUpdate` | `PESSIMISTIC_WRITE` |
| `SettlementRepository` | `findByIdForUpdate` | `PESSIMISTIC_WRITE` |
| `TreasuryAccountRepository` | `findByCurrencyForUpdate` | `PESSIMISTIC_WRITE` |
| `WalletRepository` | `findByUserIdForUpdate` | `PESSIMISTIC_WRITE` |
| `PreferredRateRequestRepository` | `findByIdForUpdate` | `PESSIMISTIC_WRITE` |
| `ExchangeRepository` | `findByIdForUpdate` | `PESSIMISTIC_WRITE` |

**Verrou optimiste (`@Version`) :** `User`, `SystemSetting`, `RateSource`(non — `@Immutable`), `Quote`, `Order`, `Payment`, `Settlement`, `TreasuryAccount`, `Wallet`, `PreferredRateRequest`, `Exchange`. `OptimisticLockingFailureException` → `409 CONCURRENT_MODIFICATION`.

**Index uniques partiels = invariants métier SQL :**

| Index | Table | Garantit |
|---|---|---|
| `uq_users_phone` / `uq_users_email` | `users` | unicité identifiants |
| `uq_rate_source_current` | `rate_sources` | ≤ 1 cotation courante / source / paire |
| `uq_orders_reference` / `uq_orders_quote` | `orders` | référence unique ; 1 devis ⇒ ≤ 1 ordre |
| `uq_beneficiaries_order` | `beneficiaries` | 1‑1 avec l'ordre |
| `uq_payments_order` | `payments` | 1 paiement / ordre |
| `uq_payments_txref` | `payments` | une référence de transaction ne sert jamais 2 ordres |
| `uq_settlements_order` | `settlements` | 1 règlement / ordre |
| `uq_treasury_tx_reservation_per_order` / `uq_treasury_tx_resolution_per_order` | `treasury_transactions` | ≤ 1 réservation par ordre ; réservation résolue *exactement une fois* (release **xor** withdrawal) |
| `uq_wallets_user` | `wallets` | 1 wallet / utilisateur |
| `uq_exchanges_preferred_rate_request` | `exchanges` | 1 échange / demande |

**Ledgers append-only** (`@Immutable`, aucun `UPDATE`/`DELETE`) : `rate_sources`, `treasury_transactions`, `wallet_transactions`, `order_status_history`, `payment_proofs`, `settlement_proofs`, `beneficiaries`. Correction = écriture compensatoire.

**Audit isolé :** `REQUIRES_NEW` (voir §18.4).

### 19.3 Calcul monétaire (`RateEngine`)

**Marge :** `customerRate = normalizeRate( marketRate × (1 + marginPercentage / 100) )`. Une marge positive augmente le nombre de XOF pour 1 CNY.

**Sens `SEND_XOF` (`priceFromXof`) — le montant XOF brut est connu :**
```
feeXof       = roundXofUp( amountXof × feePercentage/100 + fixedFeeXof )
netAmountXof = amountXof − feeXof                     (doit être > 0)
amountCny    = roundCnyDown( netAmountXof / customerRate )
```

**Sens `RECEIVE_CNY` (`priceFromTargetCny`) — le montant CNY cible est connu :**
```
requiredNetXof = roundXofUp( targetAmountCny × customerRate )
grossAmountXof = roundXofUp( (requiredNetXof + fixedFeeXof) / (1 − feePercentage/100) )
feeXof         = roundXofUp( grossAmountXof × feePercentage/100 + fixedFeeXof )   [recalculé]
netAmountXof   = grossAmountXof − feeXof
amountCny      = roundCnyDown( netAmountXof / customerRate )                      [recalculé]
```
Tout est **recalculé à partir du montant XOF brut final** ⇒ dans les deux sens, le devis est une application exacte de la même formule `SEND_XOF`. Conséquence assumée : `amountCny` obtenu peut différer du `targetAmountCny` visé d'au plus l'epsilon d'arrondi, **jamais en défaveur du client**.

**Règle d'arrondi (dissymétrie prudente) :** frais et montants XOF engageants → **au supérieur, scale 0** ; montant CNY final → **à l'inférieur, scale 2**. Le résidu d'arrondi reste toujours du côté de la trésorerie — la plateforme ne peut jamais devoir plus qu'elle n'a encaissé.

**Exemple** (marge 1.5 %, frais 0, marché 85) : `customerRate = 85 × 1.015 = 86.275`. `amountXof = 100 000` → `feeXof = 0` → `netAmountXof = 100 000` → `amountCny = ⌊100000 / 86.275⌋ = 1159.08 CNY`.

---

## 20. Écarts connus & dette technique

| # | Constat | Impact |
|---|---|---|
| 1 | ~~**`idempotency_keys`** jamais exploitée.~~ **RÉSOLU** (durcissement métier) : `common/idempotency/` active la table ; `Idempotency-Key` (optionnel) est honoré sur `POST /api/v1/orders`, `POST /api/v1/orders/{id}/payments`, `POST /api/admin/settlements/{id}/execute`, `POST /api/admin/treasury/{deposit,adjust}`. Migration `V15` (`request_hash` → `VARCHAR(64)`). Voir [§7.5](#75-idempotence-http-commonidempotency). |
| 2 | ~~**Aucun job d'expiration** d'`Order`.~~ **RÉSOLU (passe 2, P2-1)** : `OrderExpirationScheduler`/`OrderExpirationService` expirent les ordres `AWAITING_PAYMENT` échus (`payment_deadline_at`) et **libèrent leur réservation CNY** ; `ORDER_AUTO_EXPIRE_ENABLED` est enfin branché. Le `Quote` garde son expiration paresseuse (rien de réservé). |
| 3 | **`AdminUserSummary.orderCount` / `totalAmountCfa`** codés en dur à `0` / `ZERO` dans `UserService` — jamais agrégés depuis `orders`. |
| 4 | ~~**`SettingsService.update` n'écrit pas d'`AuditAction.SETTING_UPDATED`**.~~ **RÉSOLU** : `SettingsService` dépend désormais d'`AuditService` et émet `SETTING_UPDATED` (`metadata = {previousValue, newValue}`) à chaque modification. |
| 5 | **Incohérence de préfixe d'URL** : `auth`/`settings`/`admin` sur `/api/...` ; `quote`/`order`/`payment`/`wallet`/`preferred-rate`/`notification` sur `/api/v1/...`. Le frontend doit gérer les deux. |
| 6 | **`WalletController` n'expose pas `deposit`** — la seule voie d'alimentation d'un wallet est la méthode de service (tests) ; aucun canal réel branché. |
| 7 | **Valeurs `enum` réservées non implémentées** : `RateProviderType.MARKET/P2P`, `PaymentMethod.WAVE/BANK_TRANSFER`. Toute activation devra passer par une validation réglementaire (`REGULATORY_VALIDATION_REQUIRED`, voir ARCHITECTURE.md §J.3). |
| 8 | **Modules `dashboard` et `risk`** (KPI admin, `RiskFlag`) : prévus dans ARCHITECTURE.md, **non construits**. `AuditAction` ne contient d'ailleurs aucune valeur `RISK_*`. |
| 9 | **`exchange_rates`** : table toujours créée par `V1` puis **supprimée** par `V9`. Aucune entité Java correspondante. |
| 10 | **`RateEngine.priceFromTargetCny`** : `amountCny` retourné peut être strictement inférieur au `targetAmountCny` demandé (arrondi). Comportement documenté et testé, mais à afficher clairement côté client. |
| 11 | Le commentaire de classe de `UserService` (« le module `order` n'existe pas encore ») et plusieurs Javadoc datent d'avant la construction des modules métier — **documentation interne partiellement obsolète**. |
| 12 | **Doublon de fichier** dans `rate/domain/` : `RateSource.java` et un `MarketRate.java` séparé — cohérent mais à surveiller (le `MarketRate` du record vs. le nom de fichier). |
| 13 | `checksum_sha256` : `CHAR(64)` en `V1`, `VARCHAR(64)` en `V10`/`V11` — divergence cosmétique. (`idempotency_keys.request_hash` a été aligné sur `VARCHAR(64)` en `V15`.) |
| 14 | `README.md` (racine) décrit encore « Phase 2 livrée, Phase 3 en pause » — **fortement obsolète**. |

---

## 21. Référence complète des endpoints REST

Préfixe : `/api` (modules historiques) ou `/api/v1` (modules client récents). Tout succès est enveloppé dans `{ data, message }`, toute erreur dans `ErrorResponse`.

**En-tête `Idempotency-Key` (optionnel)** honoré sur : `POST /api/v1/orders`, `POST /api/v1/orders/{orderId}/payments`, `POST /api/admin/settlements/{id}/execute`, `POST /api/admin/treasury/deposit`, `POST /api/admin/treasury/adjust` — voir [§7.5](#75-idempotence-http-commonidempotency).

### 21.1 Public (`permitAll`)

| Méthode | Chemin | Description |
|---|---|---|
| `POST` | `/api/auth/register` | Inscription → **201** + JWT |
| `POST` | `/api/auth/login` | Connexion → JWT (rate-limité par IP) |
| `GET` | `/api/settings/public` | Bornes, durée de verrouillage, méthodes de paiement actives |
| `GET` | `/actuator/health` | Sonde |
| `GET` | `/swagger-ui.html`, `/v3/api-docs/**` | Documentation |

### 21.2 Client authentifié (rôle `USER` ou `ADMIN`)

| Méthode | Chemin | Description |
|---|---|---|
| `GET` | `/api/auth/me` | Profil courant |
| `POST` | `/api/v1/quotes` | Créer un devis → 201 |
| `GET` | `/api/v1/quotes/{id}` | Détail (404 si pas propriétaire) |
| `POST` | `/api/v1/quotes/{id}/accept` | `ACTIVE → ACCEPTED` |
| `POST` | `/api/v1/quotes/{id}/cancel` | `ACTIVE → CANCELLED` |
| `POST` | `/api/v1/orders` | Créer un ordre depuis un devis accepté → 201 |
| `GET` | `/api/v1/orders` | Mes ordres (paginé) |
| `GET` | `/api/v1/orders/{id}` | Détail (bénéficiaire + historique) |
| `POST` | `/api/v1/orders/{id}/cancel` | Annulation (depuis `AWAITING_PAYMENT`) |
| `POST` | `/api/v1/orders/{orderId}/payments` | Déclarer le paiement XOF → 201 |
| `POST` | `/api/v1/payments/{paymentId}/proofs` | Téléverser une preuve (multipart) → 201 |
| `GET` | `/api/v1/payments/{paymentId}` | Détail du paiement |
| `GET` | `/api/v1/payments/{paymentId}/proofs/{proofId}` | Télécharger une preuve (attachment) |
| `GET` | `/api/v1/wallet` | Mon solde (créé au 1ᵉʳ accès) |
| `GET` | `/api/v1/wallet/transactions` | Mouvements du wallet |
| `POST` | `/api/v1/preferred-rates` | Créer une demande de taux préférentiel → 201 (réserve le wallet) |
| `GET` | `/api/v1/preferred-rates` | Mes demandes |
| `GET` | `/api/v1/preferred-rates/{id}` | Détail (+ `currentRate`, `gap`, `phase`, `exchange`) |
| `POST` | `/api/v1/preferred-rates/{id}/cancel` | Annuler (libère le wallet) |
| `GET` | `/api/v1/notifications` | Mes notifications |
| `GET` | `/api/v1/notifications/unread-count` | Nombre de non-lues |
| `POST` | `/api/v1/notifications/{id}/read` | Marquer comme lue |

### 21.3 Administration (`/api/admin/**`, rôle `ADMIN`, double barrière)

| Méthode | Chemin | Description |
|---|---|---|
| `GET` | `/api/admin/users` | Liste paginée (filtre `status`, `search`) |
| `GET` | `/api/admin/users/{id}` | Détail |
| `POST` | `/api/admin/users/{id}/block` | Blocage (motif obligatoire) |
| `POST` | `/api/admin/users/{id}/unblock` | Déblocage |
| `GET` | `/api/admin/audit-logs` | Journal (filtres acteur/action/entité/période) |
| `POST` | `/api/admin/rates` | Publier un taux manuel courant |
| `GET` | `/api/admin/rates` | Historique des taux |
| `GET` | `/api/admin/orders` | Liste (filtre `status`) |
| `GET` | `/api/admin/orders/{id}` | Détail complet |
| `GET` | `/api/admin/payments/pending` | File de vérification |
| `GET` | `/api/admin/payments/{id}` | Détail + preuves |
| `GET` | `/api/admin/payments/{id}/proofs/{proofId}` | Télécharger une preuve |
| `POST` | `/api/admin/payments/{id}/confirm` | Confirmer (→ `PAYMENT_VERIFIED`, dépôt XOF) |
| `POST` | `/api/admin/payments/{id}/reject` | Rejeter (motif ; → `REJECTED`, libère la réservation) |
| `POST` | `/api/admin/orders/{orderId}/settlement` | Créer le règlement (→ `PROCESSING`) → 201 |
| `GET` | `/api/admin/settlements/pending` | File des règlements |
| `GET` | `/api/admin/settlements/{id}` | Détail |
| `POST` | `/api/admin/settlements/{id}/proofs` | Téléverser une preuve (multipart) → 201 |
| `GET` | `/api/admin/settlements/{id}/proofs/{proofId}` | Télécharger |
| `POST` | `/api/admin/settlements/{id}/execute` | Exécuter (référence obligatoire ; consomme CNY ; → `COMPLETED`) |
| `GET` | `/api/admin/treasury/accounts/{currency}` | Solde |
| `GET` | `/api/admin/treasury/accounts/{currency}/transactions` | Ledger paginé |
| `POST` | `/api/admin/treasury/deposit` | Alimentation manuelle |
| `POST` | `/api/admin/treasury/adjust` | Correction comptable (motif obligatoire) |
| `GET` | `/api/admin/settings` | Liste des paramètres |
| `PUT` | `/api/admin/settings/{key}` | Modifier un paramètre (effet immédiat) |

### 21.4 Flux métier de bout en bout

**Ordre XOF → CNY (bénéficiaire en Chine) :**
```
POST /api/v1/quotes                        → Quote ACTIVE (verrou 30 min)
POST /api/v1/quotes/{id}/accept            → Quote ACCEPTED
POST /api/v1/orders {quoteId, beneficiary} → Order AWAITING_PAYMENT + RESERVATION CNY
POST /api/v1/orders/{id}/payments          → Order PAYMENT_SUBMITTED + Payment SUBMITTED
POST /api/v1/payments/{pid}/proofs         → PaymentProof
── admin ──
POST /api/admin/payments/{pid}/confirm     → Payment CONFIRMED + Order PAYMENT_VERIFIED + DEPOSIT XOF
POST /api/admin/orders/{oid}/settlement    → Settlement PENDING + Order PROCESSING
POST /api/admin/settlements/{sid}/proofs   → SettlementProof
POST /api/admin/settlements/{sid}/execute  → Settlement EXECUTED + WITHDRAWAL CNY (consumed) + Order COMPLETED
```
Chemins alternatifs : `POST /orders/{id}/cancel` ou `POST /admin/payments/{id}/reject` → `RELEASE` CNF.

**Taux préférentiel (solde Wallet, pas de bénéficiaire) :**
```
POST /api/v1/preferred-rates {amountXof, targetRate}  → PreferredRateRequest ACTIVE + RESERVE wallet
── scheduler (toutes les ~30 s) ──
si currentRate ≥ targetRate  → EXECUTED + Exchange STARTED + DEBIT wallet + notifs
sinon à J+3                   → EXPIRED + RELEASE wallet
── scheduler ──
Exchange : notif à T+45 min, T+90 min, COMPLETED à T+2 h max
```

---

## 22. Tests

**183 méthodes `@Test`** réparties sur ~32 classes. `./mvnw test` exécute unitaires (`*Test`) + intégration (`*IT`).

**Socle intégration — `support/AbstractIntegrationTest`** : `@SpringBootTest(webEnvironment = RANDOM_PORT)`, `@ActiveProfiles("test")`, **un seul conteneur PostgreSQL `postgres:16-alpine`** démarré via bloc `static` (pattern « conteneur singleton », partagé par toutes les classes de la JVM de test), configuré automatiquement via `@ServiceConnection`. Arrêt délégué à Ryuk. `uniquePhone()` génère un E.164 unique par appel (contrainte `uq_users_phone`). Sous-socles : `AbstractRateQuoteIT`, `AbstractOrderPipelineIT`.

| Classe | Type | Portée |
|---|---|---|
| `PhoneNumberValidatorTest` | unitaire | E.164, normalisation |
| `JwtServiceTest` (5) | unitaire | émission/vérif, jeton falsifié/expiré/mal signé, secret < 32 octets |
| `SettingsServiceTest` (7) | unitaire (Mockito) | conversion typée, booléen ambigu, invalidation du cache |
| `MoneyRoundingTest` (3), `RateEngineTest` (11), `ManualRateProviderTest` (8) | unitaire | arrondis, formule marge/frais dans les 2 sens, source absente |
| `OrderStateMachineTest` (4), `QuoteTest` (8), `ExchangeTest` (7), `PreferredRateRequestTest` (9) | unitaire (domaine) | machines d'état, gardes d'expiration |
| `ApplicationStartupIT` (2) | intégration | 16 migrations Flyway s'appliquent **et** correspondent aux entités (`ddl-auto=validate`) ; seed rôles/paramètres |
| `AuthFlowIT` (6) | intégration | register → login → me ; doublons, mauvais mot de passe, absence de jeton |
| `AdminEndpointSecurityIT` (4), `BlockedUserIT` (1) | sécurité | `/api/admin/**` : 401 anonyme / 403 `USER` / 200 `ADMIN` ; blocage invalide un JWT non expiré immédiatement |
| `RateAndQuoteFlowIT` (5), `RateSourceUniquenessIT` (2), `QuoteConcurrencyIT` (2), `QuoteSecurityIT` (3) | intégration | publication/unicité du taux courant, cycle devis, concurrence `accept`, ownership 404 |
| `OrderConcurrencyIT` (1) | intégration (concurrence) | **8 threads créent un ordre du même devis → exactement 1 `201`, 7× `409 QUOTE_ALREADY_USED`** (passe 2) |
| `OrderExpirationServiceIT` (3) | intégration | **ordre échu → `EXPIRED` + réservation CNY libérée, idempotent ; drapeau `ORDER_AUTO_EXPIRE_ENABLED` respecté ; ordre dans sa fenêtre non touché** (passe 2) |
| `OrderFlowIT` (8), `PaymentFlowIT` (9), `SettlementFlowIT` (7) | intégration | pipeline complet par module + effets trésorerie (dont **paiement montant ≠ attendu rejeté**, passe 2) |
| `TreasuryServiceIT` (12), `WalletServiceIT` (10) | intégration | reserve/release/consume/deposit/adjust, invariants de solde (garde « consume/release > pool réservé » ; **`adjust` sous le réservé → `INSUFFICIENT_TREASURY`**, passe 2), concurrence |
| `PreferredRateServiceIT` (11), `NotificationServiceIT` (5) | intégration | déclenchement/expiration, progression d'échange, notifications |
| `IdempotencyGuardIT` (6) | intégration | rejeu même clé (pas de 2ᵉ ordre / 3ᵉ crédit), clé réutilisée corps différent → 409, partitionnement par utilisateur, sans en-tête = inchangé, **échec métier puis rejeu réussi** (passe 2) |
| `SettlementFlowIT` (7) | intégration | régression pool partagé (passe 1) + **`execute` concurrent → un seul EXECUTED, trésorerie consommée une fois** (passe 2) |
| `FullPipelineIT` (1), `WalletPreferredRateExchangeE2EIT` (1) | end-to-end | quote→order→payment→settlement ; wallet→preferred-rate→exchange |

---

## 23. Conventions de code

- **Constructeur d'injection** partout (pas de `@Autowired` sur champ).
- **Records** pour tous les DTO et les valeurs immuables (`PricingResult`, `MarketRate`, `StoredFile`, `ParsedToken`).
- **`@Transactional`** au niveau service ; `readOnly = true` pour les lectures pures (sauf quand un `FOR SHARE` est pris — voir `PreferredRateService.listMine`).
- **Entités JPA** : constructeur `protected` sans argument pour JPA + constructeur métier ; accesseurs explicites ; mutation métier via méthodes nommées (`accept`, `execute`, `block`…), jamais de setter générique de statut.
- **Mapping DTO** : manuel dans le service (méthodes `toResponse` privées statiques), sauf `UserMapper` (MapStruct).
- **Audit** : chaque action sensible → `auditService.record(actorId, actorPhone, action, entityType, entityId, metadataJson)` ; `metadata` = JSON construit à la main (échappement via `jsonString(...)`).
- **Messages d'erreur** : humains, en français, jamais parsés par le frontend (qui teste `code`).
- **Commentaires** : longs, explicatifs, en français, focalisés sur le *pourquoi* (choix de conception, pièges de concurrence). Densité élevée — à conserver.
- **Aucune valeur métier en dur** : bornes, durées, marges, frais → `SettingsService` uniquement.

---

*Document généré par inspection exhaustive de `backend/` au 2026-09-01. En cas de divergence avec le code, le code fait foi — signaler l'écart.*
