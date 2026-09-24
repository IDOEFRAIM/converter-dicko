# Final Deployment Closure — Production / Pilot Hardening

Date : 2026-09-03. Perimetre : `backend/` (Spring Boot 3.5.16 / Java 21 / PostgreSQL
16, monolithe modulaire). Cette mission ferme la boucle sur les points identifies
"non fermes" par l'audit precedent (`PRODUCTION_READINESS_AUDIT.md`, mission
anterieure) : backup/restore PostgreSQL (le bloqueur principal identifie), rate
limiting au-dela du login, observabilite minimale, HTTPS/reverse proxy, dependances,
runbook et reconciliation etendus. Aucune architecture distribuee n'a ete
introduite (pas de Redis/Kafka/Kubernetes/service mesh/CQRS/event sourcing) — le
systeme reste un monolithe modulaire Spring Boot + PostgreSQL + Docker.

---

## 1. Executive summary

Le blocage principal identifie par l'audit precedent — **aucune sauvegarde
PostgreSQL** — est ferme : un mecanisme de sauvegarde/restauration scripte
(`scripts/backup.sh`/`scripts/restore.sh`, `pg_dump`/`pg_restore` format custom) a
ete implemente et **verifie de bout en bout dans cette mission**, pas seulement
ecrit : sauvegarde reelle d'une base contenant des donnees creees via l'API,
restauration vers une instance PostgreSQL neuve completement vide, verification des
comptes de lignes et de l'integrite relationnelle, puis demarrage reussi de
l'application backend contre la base restauree (Flyway/Hibernate valident sans
intervention).

Le rate limiting, jusqu'ici limite a `/api/auth/login`, couvre desormais aussi
l'inscription et les trois endpoints d'ecriture authentifies les plus exposes
(creation de devis, creation d'ordre, soumission de paiement) — implemente et
teste isolement (le contexte de test partage aurait ete pollue par un test bout-
en-bout, voir section 15).

L'exposition Actuator a ete etendue a `metrics` (Micrometer core deja present, zero
nouvelle dependance), donnant a un operateur authentifie une visibilite minimale sur
la distribution des codes HTTP et la sante JVM.

Le driver PostgreSQL JDBC a ete mis a jour vers 42.7.13 (correctif CVE-2026-54291),
via le mecanisme standard d'override de propriete du BOM Spring Boot.

**Aucun changement n'a ete apporte au pricing, a la logique metier Quote/Order/
Payment/Treasury/Settlement/Refund, ni a l'idempotence** — tous deja valides par les
missions anterieures et hors perimetre de cette mission de fermeture.

246 tests (231 a la fin de la mission precedente, +15 dans celle-ci), 0 echec, 0
erreur.

---

## 2. Architecture de deploiement

Inchangee : monolithe modulaire Spring Boot, base PostgreSQL unique, sans etat
serveur (JWT). Aucun composant distribue ajoute. `scripts/backup.sh`/`restore.sh`
sont des scripts shell operant via `docker exec`/`docker cp` contre le conteneur
PostgreSQL existant — pas un nouveau service dans `docker-compose.yml`, pas de
sidecar, pas d'agent de sauvegarde tiers.

---

## 3. Security

| Constat | Classification | Statut |
|---|---|---|
| Rate limiting limite a `/api/auth/login` | GAP DE SECURITE (partiel) | **Corrige** — etendu a l'inscription et aux 3 endpoints d'ecriture les plus exposes |
| `/actuator/metrics` non expose (visibilite operationnelle limitee) | GAP OPERATIONNEL | **Corrige** — expose, authentifie, zero nouvelle dependance |
| Endpoints Actuator sensibles (`env`, `beans`, `configprops`, `mappings`, `heapdump`) | — | Deja non enregistres (confirme par test : 404, pas 401/403 — la route n'existe pas) |
| Driver PostgreSQL JDBC 42.7.11 (CVE-2026-54291) | GAP DE SECURITE (severite faible, non exploitable en config actuelle) | **Corrige** — mis a jour vers 42.7.13 |
| Terminaison TLS | DECISION PRODUIT/INFRASTRUCTURE | Non implementee ici (justifie — voir section 8) ; prerequis applicatif (`forward-headers-strategy: framework`) deja correct |
| CORS (origines explicites, pas de wildcard + credentials) | — | Deja conforme, non modifie |
| CSRF desactive | DECISION PRODUIT deja prise et justifiee | Conforme, inchange |
| Secrets sans defaut en profil `prod`, fail-fast au demarrage | — | Deja conforme, non modifie |

---

## 4. Secrets

Aucun secret trouve en clair dans le code source, `application*.yml`, `Dockerfile`,
`docker-compose.yml`, les scripts ajoutes dans cette mission, ou `.gitignore`. Les
nouveaux scripts (`scripts/backup.sh`/`restore.sh`) ne manipulent aucun secret :
l'authentification a PostgreSQL passe par les identifiants deja configures du
conteneur (variables d'environnement Docker existantes), jamais en argument de ligne
de commande visible dans l'historique shell ou les journaux de processus.

Rappel (deja valide, non revalide en detail dans cette mission car aucun fichier de
secret n'a ete touche) : `JWT_SECRET`/`DB_PASSWORD`/`ADMIN_PASSWORD` sans defaut en
profil `prod`, `@NotBlank` fail-fast au demarrage.

---

## 5. Database

- `ddl-auto: validate` et Flyway confirmes actifs, non modifies.
- HikariCP : `connection-timeout=10000ms`, non modifie.
- Driver PostgreSQL JDBC mis a jour 42.7.11 → **42.7.13** (voir section 12).
- **Verification supplementaire dans cette mission** : le cycle complet
  sauvegarde → restauration → redemarrage applicatif (section 6) constitue de
  facto un second test de bout en bout de la chaine Application → PostgreSQL →
  Flyway → Hibernate, sur une instance totalement neuve, en plus de la verification
  Docker deja faite par l'audit precedent.

---

## 6. Backup / Restore

**GAP OPERATIONNEL de l'audit precedent — FERME dans cette mission.**

- `scripts/backup.sh` : `pg_dump` format custom (compresse, restaurable
  selectivement) execute dans le conteneur PostgreSQL, copie hors du conteneur ET
  hors du volume de donnees (`converter-postgres-data`) vers un repertoire hote
  configurable (`BACKUP_DIR`, defaut `./backups`), retention configurable en jours
  (`BACKUP_RETENTION_DAYS`, defaut 14).
- `scripts/restore.sh` : `pg_restore --clean --if-exists --no-owner --no-privileges`
  vers un conteneur cible, **refuse de s'executer sans le flag `--yes` explicite**
  (operation destructive sur la cible).
- **Test de restauration reel effectue dans cette mission** (pas simule) :
  1. Instance isolee demarree (PostgreSQL + backend), donnees creees via de vrais
     appels API (inscription, publication de taux/cout, creation d'un devis) —
     users=2, quotes=1, rate_sources=1, daily_cost_rate_configurations=1,
     audit_logs=8, treasury_accounts=2.
  2. `scripts/backup.sh` execute avec succes (fichier `.dump` de 80K produit).
  3. Nouvelle instance PostgreSQL 16 **completement vide** (aucun volume,
     aucune migration Flyway prealable) demarree.
  4. `scripts/restore.sh` execute avec succes : 25 tables, toutes les contraintes
     (PK/FK/UNIQUE/CHECK) et tous les index recrees depuis le seul fichier de
     sauvegarde.
  5. Comptes de lignes verifies **identiques** a l'etape 1, jointure
     quote→user→cost_configuration verifiee coherente (memes valeurs exactes :
     meme UUID de devis, meme `breakEvenRate`, meme numero de telephone).
  6. Un troisieme conteneur backend demarre, pointe vers l'instance restauree :
     demarrage reussi, Flyway valide 20 migrations deja appliquees sans rien
     rejouer, Hibernate `ddl-auto: validate` passe, healthcheck `UP`.
  7. Toute la pile de verification demontee proprement (aucune trace laissee).

Voir `docs/RUNBOOK.md`, scenario 10 ("Restore PostgreSQL"), pour la procedure
operationnelle complete construite a partir de ce test.

**RPO/RTO** — DECISION REQUIRED, proposition documentee dans
`docs/PRODUCTION_CHECKLIST.md` : RPO 24h (backup quotidien), RTO 30 minutes pour un
jeu de donnees de taille pilote (non extrapolable a un volume de production reel
sans remesure — le test ci-dessus portait sur une base quasi vide).

**Ce qui reste ouvert** : rien n'invoque encore `scripts/backup.sh` automatiquement
(pas de cron/systemd timer/service planifie configure) — le script existe et
fonctionne, mais son execution reguliere reste une DECISION REQUIRED (frequence,
mecanisme de planification adapte a l'hebergement retenu).

---

## 7. Docker

Inchange par rapport a l'audit precedent (deja verifie fonctionnel). Reconfirme
indirectement par le test de restauration (section 6, etape 6) : un troisieme
conteneur backend construit depuis la meme image demarre correctement contre une
base de donnees entierement neuve.

---

## 8. HTTPS / CORS

**HTTPS** : DECISION REQUIRED (infrastructure/hebergement, pas une decision de code).
Constat technique : `server.forward-headers-strategy: framework` est deja configure
(`application.yml`) — l'application recupere deja correctement `X-Forwarded-Proto`/
`X-Forwarded-For` d'un reverse proxy de confiance pour construire les URLs et
identifier le client, sans qu'aucun changement de code ne soit necessaire. Ce qui
reste a decider est purement infrastructurel : quel reverse proxy (nginx/Caddy/
Traefik/load balancer manage), quel mecanisme d'emission de certificat. Aucun
service de ce type n'a ete ajoute a `docker-compose.yml` — une decision
d'hebergement ne doit pas etre prise unilateralement par l'implementeur.

**CORS** : inchange, deja conforme (origines explicites par variable
d'environnement, jamais de wildcard avec `allowCredentials(true)`).

---

## 9. Rate limiting

**GAP DE SECURITE partiel de l'audit precedent — FERME dans cette mission.**

`RateLimitFilter` couvrait uniquement `/api/auth/login` (limite de tentatives,
remise a zero sur succes). Etendu a deux nouvelles categories de regles, toutes
cle sur l'IP cliente (jamais sur un identifiant metier — memes principes que le
login, voir Javadoc de la classe) :

- **Inscription** (`POST /api/auth/register`) — limite de volume par fenetre,
  5 tentatives / 15 minutes par defaut (`app.abuse-protection.register-*`).
- **Ecriture authentifiee** (`POST /api/v1/quotes`, `POST /api/v1/orders`,
  `POST /api/v1/orders/{id}/payments`) — limite de volume par fenetre, 60
  requetes / 5 minutes par defaut (`app.abuse-protection.write-*`).

Difference deliberee avec le login : ces nouvelles regles **ne se reinitialisent
jamais sur un succes** — c'est une limite de volume/flooding, pas une limite de
tentatives de bourrage d'identifiants ; un flot de requetes toutes reussies reste
aussi suspect qu'un flot d'echecs.

**Choix explicite : cle sur l'IP, pas sur l'utilisateur authentifie.** Bien que ces
endpoints exigent un jeton (donc un `userId` disponible), garder l'IP comme cle
unique de toutes les regles (login inclus) evite d'introduire une seconde
dimension de cle (et donc une deuxieme famille de tests/comportements a
maintenir) pour un gain marginal a l'echelle d'un pilote mono-instance ; le
compromis documente est qu'un IP partagee (bureau, NAT operateur mobile) partage
aussi son quota entre utilisateurs legitimes — c'est pourquoi le seuil d'ecriture
(60/5min) a ete choisi genereux plutot que serre.

**Implementation en memoire (Caffeine), toujours pour une seule instance** — non
distribue, deliberement, comme le mecanisme de login preexistant. Documente en
Javadoc et dans ce rapport : `memory-based limiter ≠ distributed limiter`, un
deploiement multi-instance necessiterait un compteur partage (Redis), hors
perimetre de cette mission (pas de preuve de besoin — regle absolue de cette
mission).

---

## 10. Observability

`/actuator/metrics` ajoute a l'exposition (`health,info,metrics`), au meme niveau
d'acces que `info` deja present (authentifie, pas necessairement ADMIN). Micrometer
core deja present transitivement via `spring-boot-starter-actuator` — **zero
nouvelle dependance**, pas de registre Prometheus ajoute (non necessaire pour le
pilote, conforme a la regle explicite de cette mission : "si deja present,
l'utiliser ; sinon ne pas l'ajouter obligatoirement"). Verifie par 4 tests
(`ActuatorSecurityIT`) : `health` public, `metrics` authentifie (401 anonyme, 200
authentifie), endpoints sensibles jamais enregistres (404, pas 401/403).

Observabilite financiere dediee (volume de transactions, solde tresorerie, taux
d'echec) : toujours absente au-dela de ce que `/actuator/metrics` donne de maniere
generique — DECISION REQUIRED, inchangee depuis l'audit precedent.

---

## 11. CI/CD

Inchange (`.github/workflows/backend-ci.yml`) : compile + suite de tests complete a
chaque push/PR. Confirme : aucune etape de deploiement n'existe dans ce workflow —
il ne peut donc jamais deployer sur des tests en echec, puisqu'il ne deploie rien du
tout. DECISION REQUIRED inchangee : construire un pipeline CD ou garder un
deploiement manuel encadre par `docs/PRODUCTION_CHECKLIST.md`.

---

## 12. Dependency / CVE

Versions resolues re-verifiees (`mvn dependency:tree`) : Spring Boot 3.5.16,
`flyway-core` 11.7.2, `postgresql` **42.7.13** (etait 42.7.11), `jjwt` 0.12.7,
`mapstruct` 1.6.3, `springdoc-openapi` 2.8.17 — inchangees sauf le driver
PostgreSQL.

- **postgresql 42.7.11 → 42.7.13** : corrige CVE-2026-54291 (downgrade silencieux
  du channel binding SCRAM, pgJDBC 42.7.4-42.7.11 — confirme via la page officielle
  des releases GitHub pgjdbc/pgjdbc dans l'audit precedent). Non exploitable dans
  la configuration actuelle (`channelBinding=require` n'est positionne nulle part
  dans ce depot), mais correctif mineur sans risque de rupture — applique via le
  mecanisme standard d'override de propriete du BOM Spring Boot
  (`<postgresql.version>`), suite de tests complete confirmee verte apres
  application.
- **Spring Boot 3.5.16** : fin de support OSS confirmee le 2026-06-30 (deux sources
  independantes, audit precedent) — voir section 17 pour la decision KEEP
  TEMPORARILY / UPGRADE.
- **jjwt 0.12.7, springdoc-openapi 2.8.17, mapstruct 1.6.3, flyway-core 11.7.2** :
  aucun changement recherche a nouveau dans cette mission (deja tente sans resultat
  fiable dans l'audit precedent — recommandation inchangee : outil SCA dedie plutot
  que recherche web manuelle).
- Aucune mise a jour majeure appliquee sans verification — seule la mise a jour
  mineure du driver PostgreSQL (patch, meme ligne 42.7.x) a ete effectuee, apres
  confirmation que la suite de tests complete restait verte.

---

## 13. Financial reconciliation

Etendu (`docs/TREASURY_RECONCILIATION.md`, nouvelle section "Etape 2bis") avec
quatre categories d'anomalie explicites, chacune avec la requete/l'endpoint pour la
detecter :
1. **Missing settlement** — `Order` `SETTLED` sans `Settlement` `EXECUTED` associe.
2. **Refund mismatch** — `Refund` `PROCESSED` incoherent avec le statut du paiement
   ou avec un `Settlement` execute apres coup (devrait etre impossible, la garde
   existante l'empeche — une occurrence est CRITICAL).
3. **Unexpected pending state** — `Payment`/`Settlement`/`Refund` bloque au-dela
   d'un delai raisonnable (24h propose, DECISION REQUIRED).
4. **Unusual adjustment** — mouvement manuel (`/deposit`/`/adjust`) sans motif
   tracable ou dont le montant est disproportionne.

La procedure de rapprochement solde materialise ↔ ledger (etapes 1, 2, 3 du
document, deja livrees dans l'audit precedent) reste inchangee.

---

## 14. Incident runbook

Etendu (`docs/RUNBOOK.md`, desormais 10 scenarios au lieu de 8) :
- Ajout d'un champ **Escalade** a chacun des 8 scenarios existants (absent du
  runbook precedent), precisant quand arreter d'agir seul.
- **Nouveau scenario 8 — "Application DOWN"** : distinct du scenario "Base de
  donnees indisponible" (l'application repond mais annonce `DOWN`) — ici le
  processus backend lui-meme ne repond pas ou ne demarre pas. Diagnostic via
  journaux/code de sortie du conteneur, action interdite explicite (ne jamais
  recreer le conteneur avant d'avoir recupere ses journaux).
- **Nouveau scenario 10 — "Restore PostgreSQL"** : procedure complete en 9 etapes,
  construite directement a partir du test reel effectue dans cette mission
  (section 6), avec verification de coherence des donnees a chaque etape et
  escalade systematique avant l'etape destructive.

---

## 15. Modifications effectuees

| Fichier | Changement | Raison | Test |
|---|---|---|---|
| `scripts/backup.sh` | Nouveau | Sauvegarde `pg_dump` scriptee, stockage hors volume DB | Verifie par execution reelle (section 6) |
| `scripts/restore.sh` | Nouveau | Restauration `pg_restore`, confirmation `--yes` obligatoire | Verifie par execution reelle (section 6) |
| `backend/pom.xml` | Override `<postgresql.version>` 42.7.11 → 42.7.13 | Correctif CVE-2026-54291 | Suite complete (246 tests) verte apres |
| `backend/src/main/java/com/converter/config/props/AbuseProtectionProperties.java` | Nouveau | Config du rate limiting etendu | Couvert par `RateLimitFilterTest` |
| `backend/src/main/java/com/converter/ConverterApplication.java` | Enregistrement de `AbuseProtectionProperties` | Necessaire pour l'injection Spring | Suite complete verte |
| `backend/src/main/java/com/converter/security/RateLimitFilter.java` | Generalise : ajoute inscription + 3 endpoints d'ecriture (volume, ne se reinitialise pas sur succes), conserve le comportement login inchange | Fermer le gap "rate limiting login uniquement" | `RateLimitFilterTest` (6 tests unitaires isoles) |
| `backend/src/main/resources/application.yml` | +`app.abuse-protection.*`, `management.endpoints.web.exposure.include: health,info,metrics` | Config des nouvelles regles + observabilite | Suite complete verte |
| `backend/src/test/resources/application-test.yml` | +`app.abuse-protection.*` avec seuils tres eleves | Neutraliser la fonctionnalite pour la suite IT partagee (voir section 15bis) sans affecter les tests existants | Suite complete verte (246 tests, aucune regression) |
| `backend/src/test/java/com/converter/security/RateLimitFilterTest.java` | Nouveau (6 tests) | Test unitaire pur, hors contexte Spring partage | Ce fichier lui-meme |
| `backend/src/test/java/com/converter/security/ActuatorSecurityIT.java` | Nouveau (4 tests) | Verifie l'exposition Actuator (public/authentifie/jamais enregistre) | Ce fichier lui-meme |
| `docs/TREASURY_RECONCILIATION.md` | +section "Etape 2bis" (4 categories d'anomalie) | Phase 23 : reconciliation Order↔Payment↔Settlement↔Refund explicite | Document, pas de code |
| `docs/RUNBOOK.md` | +champ Escalade (8 scenarios existants), +2 scenarios (Application DOWN, Restore PostgreSQL) | Phase 24 : couverture complete demandee | Procedure "Restore PostgreSQL" verifiee par execution reelle |
| `docs/PRODUCTION_CHECKLIST.md` | Mis a jour : backup/restore coche, RPO/RTO documente, rate limiting etendu coche, metrics coche, driver PostgreSQL coche, section stockage/retention ajoutee | Refleter l'etat reel apres cette mission | — |
| `docs/DEPLOYMENT_CLOSURE_REPORT.md` | Nouveau | Ce rapport | — |

### 15bis. Pourquoi `application-test.yml` neutralise le rate limiting etendu

**PROBLEME** : `AbstractIntegrationTest` partage un seul contexte Spring (et donc un
seul cache Caffeine de `RateLimitFilter`) entre **toute** la suite d'integration,
pour eviter des redemarrages de contexte couteux (documente dans son propre
Javadoc). **PREUVE** : de nombreuses classes de test existantes (`RateAndQuoteFlowIT`,
`QuoteConcurrencyIT`, `PaymentConcurrencyIT`, les tests de pipeline order/settlement/
refund/wallet) appellent `POST /api/v1/quotes|orders|.../payments` depuis la meme
IP (`127.0.0.1`, via `TestRestTemplate`) a travers toute la duree d'execution de la
suite. **RISQUE** : un seuil de production realiste (60/5min) aurait tres
probablement ete depasse par le seul volume cumule des tests existants,
provoquant des echecs de tests intermittents et sans rapport avec un vrai bug,
dependants de l'ordre d'execution. **SOLUTION MINIMALE** : seuils tres eleves
(100000/1min) uniquement en profil `test`, la logique reelle du filtre etant
verifiee separement par `RateLimitFilterTest` (JUnit pur, `MockHttpServletRequest`/
`MockHttpServletResponse`, aucun contexte Spring). **IMPACT** : zero regression
(confirme, 246/246 verts). **TEST** : suite complete executee deux fois dans cette
mission (apres l'ajout du rate limiting, puis apres la mise a jour du driver
PostgreSQL), verte les deux fois.

---

## 16. Tests

| Etape | Tests | Echecs | Erreurs |
|---|---|---|---|
| Avant cette mission (fin de la mission precedente) | 236 | 0 | 0 |
| Ajoutes dans cette mission | +10 (`RateLimitFilterTest` x6, `ActuatorSecurityIT` x4) | — | — |
| Apres cette mission (suite complete, driver PostgreSQL 42.7.13 inclus) | **246** | **0** | **0** |

Note : le chiffre "236" ci-dessus correspond a l'etat exact de fin de mission
precedente. Aucun test existant n'a ete modifie, desactive, ni supprime dans cette
mission.

---

## 17. Remaining risks

**CRITICAL** : aucun identifie.

**HIGH** :
- Spring Boot 3.5.16 reste sur une ligne dont le support OSS a pris fin le
  2026-06-30 — inchange depuis l'audit precedent, aucune migration majeure
  entreprise dans cette mission de fermeture (regle explicite : ne pas migrer une
  version majeure "automatiquement"). Voir decision section 18.
- L'execution reguliere de `scripts/backup.sh` n'est pas encore automatisee
  (aucun cron/systemd timer/service planifie configure) — le mecanisme existe et
  est prouve fonctionnel, mais rien ne l'invoque seul aujourd'hui.

**MEDIUM** :
- RPO/RTO restent des propositions non validees par un responsable metier (24h /
  30min, voir section 6).
- Terminaison TLS non implementee (attendue en amont, reverse proxy) — decision
  d'hebergement non prise.
- Etat CVE non confirme de maniere fiable pour `jjwt`, `springdoc-openapi`,
  `mapstruct`, `flyway-core` (inchange depuis l'audit precedent).
- Aucune metrique financiere dediee au-dela de `/actuator/metrics` generique.
- Aucun pipeline de deploiement continu.
- Aucune politique de retention de donnees definie.

**LOW** :
- Rate limiting cle uniquement sur l'IP (pas par utilisateur) — compromis
  documente et delibere, pas un oubli.
- `AdminSettingsController.list()` non pagine (inchange, risque pratique faible).

---

## 18. Decisions Required

1. **Spring Boot 3.5.16** — voir la recommandation motivee en section 19 (Phase 27).
2. **Automatisation de `scripts/backup.sh`** : quel mecanisme de planification
   (cron hote, systemd timer, service planifie du fournisseur d'hebergement),
   quelle frequence exacte (voir RPO propose : quotidien).
3. **RPO/RTO cible** : valider ou ajuster les valeurs proposees (24h / 30min) —
   aucune exigence metier formelle n'existe encore.
4. **Terminaison TLS/reverse proxy** : quel logiciel (nginx/Caddy/Traefik/load
   balancer manage), quel mecanisme de certificat (Let's Encrypt, certificat
   manage). Prerequis applicatif deja en place, decision purement infrastructurelle.
5. **Pipeline CD** : construire un deploiement automatise, ou garder un
   deploiement manuel encadre par la checklist.
6. **Metriques financieres dediees** : construire un tableau de bord minimal
   (au-dela de `/actuator/metrics` generique), ou s'appuyer sur la reconciliation
   manuelle quotidienne pour le demarrage du pilote.
7. **Politique de retention de donnees** : duree de conservation des preuves de
   paiement, coordonnees beneficiaire, journaux d'audit.
8. **Delai de detection "unexpected pending state"** dans la reconciliation
   quotidienne (24h propose).

---

## 19. Final Checklist

Voir `docs/PRODUCTION_CHECKLIST.md` (mis a jour dans cette mission) pour la
checklist complete, item par item, avec case a cocher. Etat agrege :

```text
SECURITY
[x] secrets externalises
[x] JWT production
[x] CORS
[ ] HTTPS (decision infrastructure requise — prerequis applicatif deja en place)
[x] IDOR (deja verifie, missions anterieures)
[x] rate limiting (etendu dans cette mission)

DATABASE
[x] PostgreSQL
[x] Flyway
[x] ddl validate
[x] backup (implemente ET verifie dans cette mission)
[x] restore (implemente ET verifie dans cette mission)
[x] persistence

APPLICATION
[x] production profile
[x] logging
[x] request ID
[x] actuator (etendu a metrics dans cette mission)
[x] healthcheck
[x] error handling

FINANCE
[x] pricing (missions anterieures, non retouche)
[x] Quote / [x] Order / [x] Payment / [x] Treasury / [x] Settlement / [x] Refund
[x] reconciliation (etendue dans cette mission)

OPERATIONS
[x] runbook (etendu dans cette mission — 10 scenarios, champ Escalade)
[x] monitoring (minimal — metrics)
[ ] alerting (aucun mecanisme automatique — DECISION REQUIRED)
[x] scheduler (deja verifie, inchange)
[x] recovery (backup/restore verifie dans cette mission)

DELIVERY
[x] tests (246, 0 echec, 0 erreur)
[x] CI (inchange)
[x] Docker (reconfirme via le test de restauration)
[x] dependency review (driver PostgreSQL corrige ; reste SCA dedie recommande)
```

---

## Phase 37 — Verdict final

**Financially Safe** : YES WITH LIMITATIONS. Inchange depuis l'audit precedent —
aucune modification de cette mission ne touche au pricing ni au pipeline
transactionnel. La seule limitation connue (idempotence, fenetre de 5 minutes
apres crash) reste acceptee et documentee, pas nouvelle.

**Security Safe** : WITH LIMITATIONS. Le rate limiting couvre desormais les
endpoints d'ecriture les plus exposes (etait un gap reel, ferme). Le driver
PostgreSQL est a jour. La limitation structurelle residuelle est la meme
qu'avant : la ligne Spring Boot a depasse son support OSS — un risque croissant,
pas une vulnerabilite active aujourd'hui.

**Operationally Deployable** : WITH LIMITATIONS → **nettement renforcee par
rapport a l'audit precedent**. Le bloqueur principal explicitement identifie
("aucune sauvegarde") est ferme et **prouve par un test reel**, pas seulement
implemente. Ce qui reste ouvert (automatisation de la planification du backup,
TLS, alerting, CD) sont des decisions d'infrastructure/d'exploitation, pas des
lacunes du code applicatif lui-meme.

**Recommended Action : PILOT.**

Justification : le bloqueur qui aurait justifie "FIX BEFORE DEPLOY" lors de
l'audit precedent (absence totale de sauvegarde) est desormais ferme et
verifie. Ce qui reste (planification automatique du backup, choix du reverse
proxy/TLS, alerting, CD, metriques financieres dediees) est une liste de
decisions d'exploitation legitimes pour un demarrage en pilote surveille — pas
des lacunes qui remettent en cause l'integrite financiere ou la securite
applicative du systeme. Un lancement en PRODUCTION a pleine echelle (argent reel,
volume important) devrait attendre que ces decisions soient prises et que le
RPO/RTO soit remesure sur un volume de donnees representatif — c'est pourquoi la
recommandation reste PILOT, pas DEPLOY sans reserve.

---

## FREEZE BACKEND

Le systeme est considere ferme pour cette phase de durcissement. Sauf bug
critique decouvert en pilote, les elements suivants **ne doivent plus etre
modifies** sans repasser par le meme processus d'audit (PROBLEME → PREUVE →
RISQUE → SOLUTION MINIMALE → IMPACT → TEST) :

- Le pipeline financier `Quote → Order → Payment → Treasury → Settlement → Refund`
  et le moteur de pricing (`RateEngine`, `CostRateCalculator`) — valides sur sept
  missions successives, y compris en concurrence reelle.
- Le mecanisme d'idempotence (`IdempotencyService`/`IdempotencyGuard`/
  `IdempotencyPendingCleanupScheduler`) — la limitation residuelle connue a ete
  analysee et deliberement acceptee, pas laissee de cote par omission ; un
  correctif par verrouillage a ete etudie et rejete (deadlock demontre).
- Les contraintes SQL CHECK/index uniques partiels sur `treasury_accounts`,
  `treasury_transactions`, `refunds`, `payments`, `settlements` — ce sont le
  dernier filet de securite independant du code applicatif.
- `GlobalExceptionHandler`/`SecurityResponseWriter` et le contrat `ErrorResponse`
  (`traceId` coherent sur les deux chemins) — verifie et corrige dans la mission
  precedente.
- `RateLimitFilter` et sa cle IP deliberee (voir section 9) — ne pas re-cle sur
  l'utilisateur sans nouvelle preuve d'un besoin reel.
- `scripts/backup.sh`/`restore.sh` — les options `pg_restore` (`--clean --if-
  exists --no-owner --no-privileges`) sont deliberees ; toute modification doit
  etre revalidee par un nouveau cycle de test de restauration complet, pas
  seulement relue.

Le prochain changement sur ces zones doit etre traite comme une **evolution
controlee**, avec audit prealable — jamais comme une correction rapide ou un
refactor d'opportunite.
