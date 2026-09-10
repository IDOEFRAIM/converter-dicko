# Déploiement — Converter

Stack : **PostgreSQL 16 + backend Spring Boot (Java 21) + frontend Angular (nginx)**,
orchestrée par `docker compose`. Monolithe modulaire — pas de Redis/Kafka/K8s.

L'application mobile (Flutter) se compile à part : voir [§5](#5-application-mobile).

---

## 1. Pré-requis

- Docker Engine ≥ 24 avec le plugin **`docker compose` v2** (`docker compose version`).
- ~2 Go de RAM libres, ~2 Go de disque pour les images.
- Ports hôte libres : `8080` (API), `4300` (frontend), `5432` (PostgreSQL) —
  ajustables via `.env`.

---

## 2. Configuration (`.env`)

```bash
cp .env.example .env
```

Puis renseigner `.env`. Variables **obligatoires en profil `prod`** (l'application
refuse de démarrer sinon) :

| Variable | Rôle | Générer / choisir |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` en production | `prod` |
| `POSTGRES_PASSWORD` | mot de passe PostgreSQL | `openssl rand -base64 24` |
| `DB_PASSWORD` | idem, côté backend (même valeur) | = `POSTGRES_PASSWORD` |
| `JWT_SECRET` | signature des jetons, ≥ 32 octets | `openssl rand -base64 48` |
| `CORS_ALLOWED_ORIGINS` | origine(s) réelle(s) du frontend | `https://app.mondomaine.com` |
| `ADMIN_PASSWORD` | requis **si** `ADMIN_SEED_ENABLED=true` | mot de passe fort |

> En profil `dev` (défaut), tout est optionnel : identifiants locaux, clé JWT de
> dev, mot de passe admin aléatoire affiché **une fois** dans les logs.

Le frontend appelle l'API en **même origine** (`/api`) : nginx relaie `/api/*`
vers le conteneur `backend`. Aucun `API_BASE_URL` à configurer côté web.

---

## 3. Déploiement

### Voie rapide

```bash
./scripts/deploy.sh
```

Le script : vérifie Docker + `.env`, bloque si un secret `prod` manque, construit
les images, démarre la stack, attend que `postgres` / `backend` / `frontend`
soient *healthy*, puis affiche les URLs. `--pull` rafraîchit les images de base,
`--no-build` fait un simple redémarrage.

### Voie manuelle

```bash
docker compose -f docker-compose.yml up -d --build
docker compose -f docker-compose.yml ps
```

> `docker-compose.override.yml` (remap du port PostgreSQL en `55432`) est une
> commodité de dev, chargée automatiquement par `docker compose` sans `-f`.
> `deploy.sh` l'ignore volontairement.

Au premier démarrage, **Flyway applique les migrations V1 → V33** automatiquement
(`spring.jpa.hibernate.ddl-auto=validate` : le schéma n'est jamais deviné).

### Vérification

```bash
curl -fsS http://localhost:8080/actuator/health          # {"status":"UP"}
curl -fsS -o /dev/null -w '%{http_code}\n' http://localhost:4300/   # 200
curl -fsS -o /dev/null -w '%{http_code}\n' http://localhost:4300/api/v1/rates/history  # 200/401
```

### Compte administrateur

- **dev** : `docker compose logs backend | grep -i "mot de passe"` (généré au 1ᵉʳ démarrage).
- **prod** : mettre `ADMIN_SEED_ENABLED=true` + `ADMIN_PASSWORD=…` pour le premier
  démarrage, puis **repasser `ADMIN_SEED_ENABLED=false`** et redéployer. Le seed
  ne s'exécute de toute façon que si aucun admin n'existe.

---

## 4. Mise à jour

```bash
git pull
./scripts/deploy.sh            # rebuild + up -d ; Flyway applique les nouvelles migrations
```

Les migrations ne sont **jamais** modifiées rétroactivement : chaque changement de
schéma est une nouvelle `V{n+1}__…sql`. Un rollback applicatif se fait en
redéployant l'image précédente **et** (si des migrations avaient été appliquées)
en restaurant la base — voir §6.

---

## 5. Application mobile

Non conteneurisable : livrable = un APK / IPA.

```bash
cd mobile
flutter pub get     # requis : open_filex a été ajouté aux dépendances
flutter build apk --release \
  --dart-define=APP_ENV=production \
  --dart-define=API_BASE_URL=https://api.mondomaine.com
```

`API_BASE_URL` est l'**origine seule** (schéma + hôte), sans `/api` — `ApiClient`
l'ajoute. Sans ce define, la valeur de production est volontairement invalide
(`https://CHANGE_ME.production.invalid`) pour ne jamais pointer par erreur vers un
mauvais backend. iOS : `flutter build ipa` avec les mêmes `--dart-define`.

`CORS_ALLOWED_ORIGINS` (backend) n'a pas à lister l'app mobile : une app native
n'est pas soumise à la politique CORS du navigateur.

---

## 6. Sauvegarde / restauration

```bash
./scripts/backup.sh                       # pg_dump format custom -> ./backups/, hors volume
./scripts/restore.sh ./backups/<f>.dump --yes    # DESTRUCTIF sur la base cible
```

Planifier `backup.sh` en cron (quotidien). Détail et test de restauration :
`docs/RUNBOOK.md`, `docs/DEPLOYMENT_CLOSURE_REPORT.md`.

---

## 7. Mise en production réelle (TLS)

`docker compose` expose du HTTP en clair. Devant le conteneur `frontend`, placer
un terminateur TLS (Caddy, Traefik, nginx hôte, ou l'LB du cloud) qui :

1. termine HTTPS pour le domaine du frontend ;
2. relaie tout vers `frontend:4300` (le conteneur `frontend` sert le SPA **et**
   relaie déjà `/api` vers `backend`) ;
3. transmet `X-Forwarded-Proto https` (le backend a `server.forward-headers-strategy=framework`).

Garder `8080` (API) et `5432` (PostgreSQL) **non exposés** publiquement : retirer
leurs `ports:` de `docker-compose.yml` en production si un reverse proxy externe
gère l'entrée, ou les lier à `127.0.0.1`.

---

## 8. État des tiers (au dernier commit)

| Tier | Build | Tests |
|---|---|---|
| backend | `./mvnw package` ✅ · image Docker ✅ | 203 tests unitaires ✅ · ITs Testcontainers (CI) |
| frontend | `npm run build` ✅ · image Docker ✅ | — |
| mobile | non compilable dans cet environnement (revue manuelle) | — |
