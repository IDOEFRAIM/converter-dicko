# Converter — Infrastructure de paiement Burkina Faso ↔ Chine (XOF ↔ RMB)

Plateforme destinée aux étudiants, commerçants/importateurs et PME ayant besoin de payer et suivre leurs transferts XOF ↔ RMB vers/depuis la Chine.

> **Statut** : Phase 2 (Fondations backend) **livrée** — auth, user, settings, audit, sécurité JWT, Docker, 39/39 tests verts. La Phase 3 est **en pause** : une révision architecturale et fonctionnelle (**Phase 2.5**) vient d'être produite suite à l'étude de marché, avant tout code des modules `exchange`/`order`/`payment`/`treasury`. Voir [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — Partie I (révision en vigueur : modèle `Rate`/`Quote`/`Order`/`Payment`/`Settlement`, trésorerie, risque/conformité, KPI, roadmap) et Partie II (conception initiale, conservée comme référence historique). Le code applicatif de cette page (arborescence, commandes, endpoints ci-dessous) reflète l'état **réellement livré** de la Phase 2 et n'est pas affecté par la révision.

## Ce que contient cette phase

- Squelette Maven Spring Boot 3.5 / **Java 21**, avec Maven Wrapper (`mvnw` / `mvnw.cmd`) — aucune installation locale de Maven requise.
- PostgreSQL 16 + migrations Flyway `V1` à `V6` (schéma, index/contraintes métier, rôles, paramètres, comptes de trésorerie, séquence de référence d'ordre).
- Socle commun : `ApiResponse`/`ErrorResponse` uniformes, `GlobalExceptionHandler`, exceptions métier typées.
- Sécurité : Spring Security + JWT (HS256, 2 h), BCrypt (coût 12), RBAC (`USER` / `ADMIN`), limiteur anti force-brute sur `/api/auth/login`, CORS strict par variable d'environnement.
- Modules `auth`, `user`, `settings` (paramètres métier administrables : bornes de montant, durée de verrouillage du taux, etc.).
- Amorçage d'un compte administrateur unique via variables d'environnement — **aucun mot de passe en dur**.
- Documentation OpenAPI / Swagger.
- Tests unitaires (Mockito) et d'intégration (Testcontainers PostgreSQL) — aucune dépendance à un PostgreSQL installé localement.
- Docker Compose (PostgreSQL + backend) avec health checks.

## Prérequis

| Outil | Version | Obligatoire pour |
|---|---|---|
| **Docker** | récent, avec Docker Compose v2 | Tout — c'est le chemin recommandé |
| JDK | 21 LTS (Temurin recommandé) | Lancer le backend **hors** Docker |
| Docker | — | Exécuter `./mvnw test` (Testcontainers y démarre un PostgreSQL jetable) |

Vous n'avez **pas besoin** d'installer Maven : le Maven Wrapper (`./mvnw`) télécharge et pilote la bonne version de Maven (3.9.16) tout seul, à condition qu'un JDK 21 soit sur le `PATH`.

## Démarrage rapide (Docker Compose — recommandé)

```bash
cp .env.example .env
docker compose up --build
```

Sans **aucune** variable renseignée dans `.env`, l'application démarre quand même : le profil `dev` fournit des valeurs de développement sûres (base locale, clé JWT de dev, CORS vers `http://localhost:4200`). C'est une garantie volontaire de la Phase 2.

Au premier démarrage, un compte administrateur est créé automatiquement. Si `ADMIN_PASSWORD` n'est pas renseigné dans `.env`, un mot de passe aléatoire est généré et affiché **une seule fois** dans les journaux :

```bash
docker compose logs backend | grep -A6 "COMPTE ADMINISTRATEUR"
```

Une fois démarré :

| Ressource | URL |
|---|---|
| API | http://localhost:8080/api |
| Documentation Swagger | http://localhost:8080/swagger-ui.html |
| Santé de l'application | http://localhost:8080/actuator/health |
| PostgreSQL | `localhost:5432` (base `converter`) |

Arrêter :

```bash
docker compose down
```

Arrêter et supprimer les données PostgreSQL (repart de zéro) :

```bash
docker compose down -v
```

## Démarrage en développement local (backend hors Docker)

1. Démarrer uniquement PostgreSQL :

   ```bash
   docker compose up -d postgres
   ```

2. Lancer le backend avec le Maven Wrapper :

   ```bash
   cd backend
   ./mvnw spring-boot:run
   ```

   Sous Windows (PowerShell) : `.\mvnw.cmd spring-boot:run`

   Le profil `dev` est actif par défaut (`spring.profiles.default: dev` dans `application.yml`) : aucune variable d'environnement n'est requise pour démarrer.

## Tests

```bash
cd backend
./mvnw test
```

Cette seule commande exécute :

- les **tests unitaires** (`*Test.java`) — logique pure, sans base de données (`PhoneNumberValidatorTest`, `JwtServiceTest`, `SettingsServiceTest`) ;
- les **tests d'intégration** (`*IT.java`) — via [Testcontainers](https://testcontainers.com/), qui démarre un conteneur PostgreSQL jetable automatiquement. **Docker doit être lancé**, mais aucun PostgreSQL local n'est nécessaire.

Un seul conteneur PostgreSQL est partagé par toute la suite (pattern "conteneur singleton"), démarré une fois par JVM de test via un bloc d'initialisation statique dans `AbstractIntegrationTest`.

### Couverture actuelle

| Classe | Type | Vérifie |
|---|---|---|
| `PhoneNumberValidatorTest` | Unitaire | Format E.164, normalisation des séparateurs |
| `JwtServiceTest` | Unitaire | Génération/vérification de jeton, rejet d'un jeton falsifié/expiré/mal signé, refus d'un secret < 32 octets |
| `SettingsServiceTest` | Unitaire (Mockito) | Conversion typée des paramètres, rejet d'un booléen ambigu, invalidation du cache à l'écriture |
| `ApplicationStartupIT` | Intégration | Les 6 migrations Flyway s'appliquent sans erreur **et** correspondent exactement aux entités JPA (`ddl-auto=validate`) ; amorçage des rôles et paramètres |
| `AuthFlowIT` | Intégration | Inscription → connexion → profil ; rejet des doublons, mauvais mot de passe, absence de jeton |
| `AdminEndpointSecurityIT` | Intégration (sécurité) | `/api/admin/**` refuse un appel anonyme (401) et un rôle `USER` (403) ; un `ADMIN` passe (200) |
| `BlockedUserIT` | Intégration (sécurité) | Un blocage administratif invalide **immédiatement** un jeton déjà émis et non expiré (décision V5) |

## Variables d'environnement

Voir [.env.example](.env.example) pour la liste complète et commentée. Aucune valeur sensible n'est présente dans le dépôt.

| Variable | Obligatoire | Défaut (profil `dev`) |
|---|---|---|
| `JWT_SECRET` | En **profil `prod`** uniquement | Clé de développement fournie par `application-dev.yml` |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Recommandé partout | Base locale `converter` / `converter_local_dev` |
| `ADMIN_SEED_ENABLED` | Non | `true` |
| `ADMIN_PHONE` | Si le seed est actif | `+22500000000` |
| `ADMIN_PASSWORD` | En **profil `prod`** si le seed est actif | Génère un mot de passe aléatoire, affiché une fois dans les journaux |
| `CORS_ALLOWED_ORIGINS` | Non | `http://localhost:4200` |

En profil `prod`, l'application **refuse de démarrer** si `JWT_SECRET` est absent, ou si `ADMIN_SEED_ENABLED=true` sans `ADMIN_PASSWORD` — jamais de génération silencieuse de secret en production.

## Structure du dépôt

```
converter/
├── docs/
│   └── ARCHITECTURE.md       # Conception complète (Phase 1)
├── backend/                  # Spring Boot 3.5 / Java 21 (cette phase)
│   ├── src/main/java/com/converter/
│   │   ├── common/            # ApiResponse, exceptions, entités de base
│   │   ├── config/             # OpenAPI, JPA, MVC, propriétés typées
│   │   ├── security/            # JWT, RBAC, filtres, anti brute-force
│   │   ├── auth/                 # Inscription, connexion, profil
│   │   ├── user/                  # Comptes, rôles, amorçage admin
│   │   ├── settings/               # Paramètres métier administrables
│   │   ├── audit/                   # Journal d'audit (écriture isolée)
│   │   └── admin/                    # Contrôleurs d'administration
│   ├── src/main/resources/
│   │   ├── db/migration/       # V1 à V6
│   │   ├── application.yml
│   │   ├── application-dev.yml
│   │   └── application-prod.yml
│   ├── src/test/java/com/converter/
│   ├── Dockerfile
│   └── pom.xml
├── docker-compose.yml
├── .env.example
└── README.md
```

Les modules `exchange/`, `order/`, `payment/`, `treasury/` et le frontend `frontend/` (Angular) apparaîtront lors des phases suivantes, selon le plan d'implémentation décrit dans [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Endpoints disponibles à ce stade

| Méthode | Chemin | Accès |
|---|---|---|
| `POST` | `/api/auth/register` | Public |
| `POST` | `/api/auth/login` | Public |
| `GET` | `/api/auth/me` | Authentifié |
| `GET` | `/api/settings/public` | Public |
| `GET` | `/api/admin/settings` | `ADMIN` |
| `PUT` | `/api/admin/settings/{key}` | `ADMIN` |
| `GET` | `/api/admin/users` | `ADMIN` |
| `GET` | `/api/admin/users/{id}` | `ADMIN` |
| `POST` | `/api/admin/users/{id}/block` | `ADMIN` |
| `POST` | `/api/admin/users/{id}/unblock` | `ADMIN` |
| `GET` | `/api/admin/audit-logs` | `ADMIN` |

Documentation interactive complète (schémas, exemples, codes d'erreur) : http://localhost:8080/swagger-ui.html une fois l'application démarrée.
