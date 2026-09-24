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

**Connexion Google (mobile) — facultative.** Sans `GOOGLE_OAUTH_CLIENT_ID`,
`POST /api/auth/google[/complete]` répond `503` proprement (l'app mobile masque
alors le bouton) : ça ne bloque **jamais** le reste du déploiement. Pour
l'activer :

1. [Google Cloud Console](https://console.cloud.google.com/) → *API et
   services* → *Identifiants* → *Créer des identifiants* → *ID client OAuth*.
2. Créer un client **Web application** : c'est son Client ID qu'on met dans
   `GOOGLE_OAUTH_CLIENT_ID` (backend) **et** dans
   `--dart-define=GOOGLE_SERVER_CLIENT_ID=...` (mobile, § 5) — le même des
   deux côtés, c'est ce qui permet au backend de vérifier le jeton émis pour
   le mobile.
3. Créer un client **Android** : nécessite le nom de package réel de l'app
   (`applicationId` dans `mobile/android/app/build.gradle.kts` — actuellement
   `com.example.mobile`, un placeholder à changer avant publication) et son
   empreinte SHA-1 (`cd mobile/android && ./gradlew signingReport`).
4. Créer un client **iOS** : renseigner son Client ID et son schéma d'URL
   inversé dans `mobile/ios/Runner/Info.plist` (`GIDClientID` /
   `CFBundleURLSchemes`, marqués `CHANGE_ME` dans le dépôt — impossible à
   vérifier sans Xcode/Mac, à faire sur une machine qui en dispose).

**Notifications push (PWA) — facultatives.** Sans `VAPID_PUBLIC_KEY`/
`VAPID_PRIVATE_KEY`, `GET /api/v1/push/config` répond `available: false` (le
site masque le bouton "Activer les alertes") : ça ne bloque **jamais** le
reste du déploiement. Pour l'activer :

```bash
npx web-push generate-vapid-keys
```

Renseigner `VAPID_PUBLIC_KEY` et `VAPID_PRIVATE_KEY` dans `.env` (une seule
paire par déploiement — ne jamais régénérer sans raison : les abonnements déjà
enregistrés en base deviendraient invalides). `VAPID_PUBLIC_KEY` n'est pas un
secret (envoyée telle quelle au navigateur) ; `VAPID_PRIVATE_KEY` signe les
envois au nom de ce serveur et doit rester secrète.

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
flutter pub get     # requis : open_filex + google_sign_in ont ete ajoutes aux dependances
flutter build apk --release \
  --dart-define=APP_ENV=production \
  --dart-define=GOOGLE_SERVER_CLIENT_ID=xxxxxxxx.apps.googleusercontent.com
```

`API_BASE_URL` est l'**origine seule** (schéma + hôte), sans `/api` — `ApiClient`
l'ajoute. En profil `production`, la valeur par défaut est déjà
`https://api.yuanpay.space` (`AppConfig._defaultBaseUrlFor` — sous-domaine
dédié qui pointe directement sur le backend, voir §7) : pas besoin de
`--dart-define=API_BASE_URL=...` sauf pour pointer exceptionnellement ailleurs
(ex. un environnement de recette). iOS : `flutter build ipa` avec les mêmes
`--dart-define`.

`GOOGLE_SERVER_CLIENT_ID` est **facultatif** : omis, `AppConfig.googleSignInAvailable`
vaut `false` et le bouton "Continuer avec Google" ne s'affiche pas (pas d'écran
mort). Voir §2 pour la création des Client ID Google (Web/Android/iOS) — le
Web Client ID est le même ici et côté backend (`GOOGLE_OAUTH_CLIENT_ID`).

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

`docker compose` expose du HTTP en clair. Devant les conteneurs, Caddy (process
**hôte**, pas conteneurisé — voir `Caddyfile.example`) termine TLS avec **deux
blocs de domaine distincts** :

| Domaine | Cible | Pour qui |
|---|---|---|
| `yourdomain.com` (web) | `frontend:4300` | Navigateur web — le SPA **et** son nginx relaient déjà `/api` vers `backend` en interne (même origine, pas de CORS) |
| `api.yourdomain.com` | `127.0.0.1:8080` (backend direct) | App **mobile** (Flutter) — une app native a besoin d'une origine HTTP directement joignable (`AppConfig.apiBaseUrl`, voir §5), elle ne passe jamais par le relai nginx du frontend |

Le second bloc exige `docker-compose.prod.yml` (voir §3), qui publie le
backend sur `127.0.0.1:8080` — **jamais** `0.0.0.0:8080` : seul un processus
tournant sur cette même machine (donc Caddy) peut l'atteindre, le port reste
injoignable depuis Internet même si un pare-feu externe est mal configuré.
Vérifier malgré tout qu'aucune règle de pare-feu (UFW, groupe de sécurité
cloud) ne route `8080` publiquement, en couche redondante.

Dans les deux cas, transmettre `X-Forwarded-Proto https` (le backend a
`server.forward-headers-strategy=framework`). PostgreSQL (`5432`) reste
**non publié** dans tous les cas — aucun bloc Caddy n'en a besoin.

---

## 8. Rotation d'un secret compromis

À exécuter dès qu'un secret (`JWT_SECRET`, `DB_PASSWORD`/`POSTGRES_PASSWORD`,
`ADMIN_PASSWORD`) a pu fuiter (conversation, capture d'écran, log partagé...).
Aucun de ces secrets n'a jamais été commité dans ce dépôt (`.env` est
gitignoré, jamais suivi) — l'exposition est toujours un canal externe
(chat, terminal partagé...), jamais l'historique git.

**Générer les nouvelles valeurs directement sur le serveur** (jamais dans un
outil tiers qui les journaliserait à son tour) :

```bash
openssl rand -base64 48   # nouveau JWT_SECRET
openssl rand -base64 24   # nouveau POSTGRES_PASSWORD / DB_PASSWORD
```

1. **`POSTGRES_PASSWORD` / `DB_PASSWORD`** — le mot de passe est fixé au
   tout premier démarrage du conteneur `postgres` (volume déjà initialisé) :
   modifier `.env` seul ne suffit pas, il faut changer le rôle en base :
   ```bash
   docker exec -it converter-postgres psql -U converter -d converter \
     -c "ALTER ROLE converter WITH PASSWORD 'NOUVELLE_VALEUR';"
   ```
   Puis mettre à jour `POSTGRES_PASSWORD` **et** `DB_PASSWORD` dans `.env`
   (même valeur) et redémarrer `backend` (`docker compose restart backend`).

2. **`JWT_SECRET`** — mettre à jour `.env` puis redémarrer `backend`.
   **Effet de bord attendu et acceptable** : tous les jetons déjà émis
   (web + mobile) sont instantanément invalidés, chaque utilisateur connecté
   devra se reconnecter à son prochain appel API (401). C'est le but : un
   jeton signé avec l'ancien secret ne doit plus jamais être accepté.

3. **`ADMIN_PASSWORD`** — ce champ ne pilote que l'amorçage **initial** de
   l'admin (`AdminAccountSeeder` : "le seed ne s'exécute que si aucun
   administrateur n'existe déjà"). Un admin existe déjà en prod, donc changer
   `.env` seul n'a aucun effet. Il n'existe pas encore d'endpoint
   self-service de changement de mot de passe (voir code) : la rotation se
   fait par mise à jour directe du condensat BCrypt (coût 12, voir
   `SecurityConfig.passwordEncoder`) :
   ```bash
   # Sur une machine avec Python + bcrypt (pip install bcrypt), SANS
   # jamais coller le mot de passe choisi dans un outil tiers/chat :
   python3 -c "import bcrypt,getpass; print(bcrypt.hashpw(getpass.getpass().encode(), bcrypt.gensalt(12)).decode())"
   ```
   Puis appliquer le condensat obtenu :
   ```bash
   docker exec -it converter-postgres psql -U converter -d converter \
     -c "UPDATE users SET password_hash = '<condensat obtenu ci-dessus>' WHERE phone = '<ADMIN_PHONE>';"
   ```
   Vérifier ensuite une connexion réussie avec le nouveau mot de passe avant
   de considérer la rotation terminée.

Dans tous les cas : ne jamais republier les nouvelles valeurs dans le canal
qui a causé la fuite initiale (même conversation, même capture d'écran...).

---

## 9. État des tiers (au dernier commit)

| Tier | Build | Tests |
|---|---|---|
| backend | `./mvnw package` ✅ · image Docker ✅ | 210 tests unitaires ✅ · ITs Testcontainers (CI) |
| frontend | `npm run build` ✅ · image Docker ✅ | — |
| mobile | non compilable dans cet environnement (revue manuelle) | — |
