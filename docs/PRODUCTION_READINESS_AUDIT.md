# Audit final de preparation a la production — Converter Backend

Date : 2026-09-03. Perimetre : `backend/` (Spring Boot 3.5.16 / Java 21 / PostgreSQL 16,
monolithe modulaire). Realise en 48 phases couvrant configuration, securite,
fiabilite, integrite financiere, exploitation, base de donnees et dependances. Cette
mission s'appuie sur six missions d'audit/durcissement anterieures (pricing, pipeline
Quote→Order→Payment→Treasury→Settlement, refund/reversal, idempotency recovery) deja
livrees et testees ; elle ne les reaudite pas integralement mais verifie leur
coherence avec les nouveaux constats.

**Regle de lecture** : chaque constat est classe BUG / GAP DE SECURITE / GAP DE
FIABILITE / GAP OPERATIONNEL / DECISION PRODUIT / AMELIORATION FUTURE. Un desaccord
d'implementation avec une preference technique n'est jamais qualifie de bug.

---

## 1. Executive summary

Le coeur transactionnel (Quote, Order, Payment, Treasury, Settlement, Refund) a ete
audite et durci sur six missions anterieures : verrouillage pessimiste systematique,
contraintes SQL CHECK comme filet de securite independant du code applicatif, index
uniques partiels pour les invariants "un actif, plusieurs historiques", separation
stricte REQUIRES_NEW/REQUIRED pour l'idempotence. Cette septieme mission a verifie la
configuration reelle de production, la securite du perimetre HTTP, le
build/deploiement Docker, et l'etat des dependances.

**Constats structurants de cette mission** :
- Documentation Swagger/OpenAPI desormais desactivee en production (gap de securite
  corrige).
- Identifiant de correlation (`X-Request-ID`/`traceId`) desormais coherent sur les
  deux chemins d'erreur possibles (controleur et filtre de securite) — corrige dans
  cette meme mission.
- CI minimale ajoutee (compile + tests complets a chaque push/PR).
- Build et demarrage Docker verifies reellement (conteneur `healthy` contre une
  vraie base PostgreSQL conteneurisee).
- Validation d'entree renforcee sur les montants monetaires (borne superieure
  alignee sur la precision des colonnes NUMERIC, corrige dans cette mission).
- **Spring Boot 3.5.16 a atteint sa fin de support open-source le 2026-06-30** (fait
  verifie via deux sources independantes) — aucun correctif de securite gratuit ne
  sera plus publie pour cette ligne. C'est le constat le plus significatif de cette
  mission.
- Aucune sauvegarde automatisee de la base de donnees n'existe.
- Aucune metrique financiere (volume, solde, taux d'echec) n'est exposee.

236 tests (unitaires + integration Testcontainers) passent, 0 echec, 0 erreur.

---

## 2. Architecture de deploiement

Monolithe modulaire Spring Boot, une seule base PostgreSQL, sans etat serveur (JWT).
`docker-compose.yml` orchestre `postgres` (image officielle, volume nomme persistant,
healthcheck `pg_isready`) et `backend` (build multi-stage, utilisateur non-root,
healthcheck sur `/actuator/health`, demarrage conditionne a la sante de `postgres`).
Aucun composant additionnel (pas de cache distribue, pas de message broker, pas de
service mesh) — conforme a la contrainte explicite de cette mission de rester sur
l'architecture monolithe modulaire + PostgreSQL + ACID, aucune preuve de besoin d'
architecture distribuee n'ayant ete rencontree pendant l'audit.

---

## 3. Security findings

| # | Constat | Classification | Statut |
|---|---|---|---|
| S1 | Swagger/OpenAPI expose sans authentification en profil de base, non desactive en prod | GAP DE SECURITE | **Corrige** (`application-prod.yml`) |
| S2 | Reponses d'erreur filtre-level (401/403) sans `traceId`, incoherentes avec les reponses controleur | GAP OPERATIONNEL (observabilite) | **Corrige** (`SecurityResponseWriter`) |
| S3 | JWT : secret sans defaut en prod, `@NotBlank` fail-fast au demarrage | — | Deja conforme (verifie, non modifie) |
| S4 | CORS : origines listees explicitement par variable d'environnement, pas de wildcard, `allowCredentials(true)` coherent avec la specification Fetch | — | Deja conforme (verifie, non modifie) |
| S5 | CSRF desactive | DECISION PRODUIT deja prise et justifiee (API sans session cookie, tokens portes) | Conforme, justification en commentaire dans le code |
| S6 | Rate limiting uniquement sur `/api/auth/login`, par IP | DECISION PRODUIT | Voir section 16 (DECISION REQUIRED : etendre a d'autres endpoints ?) |
| S7 | Actuator n'expose que `health`+`info` | — | Deja conforme |
| S8 | Aucune limite explicite de taille/profondeur JSON (`spring.jackson.*`, `server.max-http-request-header-size`) au-dela des defauts Tomcat/Jackson | GAP DE SECURITE (potentiel, faible severite) | Non corrige — voir section 15 |
| S9 | Upload de fichier : validation par magic bytes, cle de stockage generee serveur (jamais derivee du client) | — | Deja conforme |
| S10 | Mot de passe admin genere logue en clair au demarrage en profil **dev uniquement**, avec commentaire explicite "affiche une seule fois" | DECISION PRODUIT deja prise et justifiee | Conforme en dev ; confirmer `ADMIN_SEED_ENABLED=false` en prod (voir checklist) |
| S11 | Postgresql JDBC 42.7.11 : CVE-2026-54291 (downgrade silencieux du channel binding), corrige en 42.7.12 | GAP DE SECURITE (severite faible ici) | Non exploitable dans la configuration actuelle (`channelBinding=require` non positionne nulle part) — mise a jour recommandee par prudence, voir section 16 |
| S12 | Spring Boot 3.5.16 : fin de support OSS atteinte le 2026-06-30 | GAP DE SECURITE (structurel, pas un CVE ponctuel) | Non corrige — DECISION REQUIRED, voir section 16 |

---

## 4. Reliability findings

- **Idempotence** (Mission 5-6) : mecanisme `tryInsert`/`findExisting` (REQUIRES_NEW)
  + `complete` (REQUIRED, meme transaction que l'operation metier) +
  `releasePending` (REQUIRES_NEW) prouve atomiquement couple au commit metier. Limite
  residuelle **connue et acceptee** : une cle peut rester `PENDING` jusqu'a 5 minutes
  apres un crash serveur, reclamee par `IdempotencyPendingCleanupScheduler`. Un
  correctif par verrouillage a ete etudie et rejete (provoquerait un deadlock avec le
  chemin de liberation fail-fast existant) — verdict maintenu : READY WITH ACCEPTED
  LIMITATION, aucune nouvelle preuve ne remet en cause cette conclusion.
- **Concurrence** : verrouillage pessimiste systematique sur `Order`/`Payment`/
  `Settlement`/`Refund`/`Quote`/`TreasuryAccount`. Test de concurrence reelle sur
  `Payment.confirm()` (Mission 3) et sur la creation de devis (`QuoteConcurrencyIT`,
  verifie vert dans cette mission).
- **Schedulers** (verifie par grep exhaustif dans cette mission) :
  - `OrderExpirationScheduler.expireOverdueOrders` — 60s, expire les ordres
    `AWAITING_PAYMENT` depasses et libere la reservation CNY.
  - `IdempotencyPendingCleanupScheduler.reclaimStalePendingKeys` — 300s.
  - `PreferredRateScheduler.evaluateActiveRequests` / `progressActiveExchanges` —
    30s chacun, evaluent/avancent les demandes de taux preferentiel.
  Aucun de ces schedulers ne presente de risque de N+1 accidentel : chaque iteration
  ouvre volontairement sa propre transaction/verrou par element (isolation
  intentionnelle, documentee dans le code).
- **Base de donnees indisponible** : `HikariCP connection-timeout=10000ms`, pas de
  cache ni de mode degrade — comportement attendu (echec propre plutot que blocage
  indefini), documente dans le runbook.

---

## 5. Financial integrity findings

- Pricing base sur `breakEvenRate` (Mission 2), remplace `MarketRate` comme base de
  `RateEngine` tout en conservant `RateSource` pour `PreferredRateService`.
- Pipeline Quote→Order→Payment→Treasury→Settlement audite (Mission 3), un gap reel
  ferme (test de concurrence manquant sur `Payment.confirm()`).
- Refund/reversal implemente (Mission 5) : `Refund` PENDING→PROCESSED|REJECTED,
  mouvement de ledger tresorerie `REFUND` dedie, `Settlement.execute()` bloque si un
  refund a deja ete traite sur le meme paiement (empeche un double paiement client).
  Une nouvelle tentative apres un rejet est autorisee (decision justifiee en Mission
  6 : contrairement a `Payment`, un refund rejete n'est pas objectivement terminal),
  via l'index unique partiel `uq_refunds_payment_active`.
- Ledger `treasury_transactions` append-only avec `balance_after`/`reserved_after`
  par ligne : permet une reconciliation sans rejouer tout l'historique (voir
  `docs/TREASURY_RECONCILIATION.md`, nouvellement documente dans cette mission).
- Invariants SQL independants du code applicatif : `reserved_balance <= balance`,
  `balance >= 0`, montants de mouvement de ledger toujours positifs (le sens est
  porte par `type`).
- **Gap corrige dans cette mission** : les champs de montant (`CreateQuoteRequest.
  amountXof/amountCny`, `TreasuryAdjustmentRequest.amount`, `SubmitPaymentRequest.
  receivedAmountXof`, `CreatePreferredRateRequest.amountXof`) n'avaient qu'une borne
  inferieure (`@Positive`/`@DecimalMin`), aucune borne superieure : un montant a 18+
  chiffres passait la validation Bean Validation et n'echouait qu'au moment de la
  persistance, avec une erreur de depassement numerique PostgreSQL opaque plutot
  qu'un 400 clair. Corrige par ajout de `@Digits` aligne sur la precision reelle des
  colonnes (`NUMERIC(19,2)` → `@Digits(integer=17, fraction=2)`,
  `NUMERIC(21,2)` → `@Digits(integer=19, fraction=2)`), verifie par un nouveau test
  d'integration (`QuoteSecurityIT.create_withAmountExceedingColumnPrecision_
  returns400NotA500`).

---

## 6. Operational findings

- Ajout du filtre `RequestIdFilter` (identifiant `X-Request-ID`/`requestId` MDC,
  genere si absent ou non conforme a l'allowlist `[a-zA-Z0-9-]+`, echo sinon) —
  chaque ligne de log applicatif porte desormais cet identifiant
  (`logging.pattern.level`), et chaque reponse d'erreur (via `GlobalExceptionHandler`
  OU `SecurityResponseWriter`) porte le meme identifiant comme `traceId`, verifie par
  4 tests d'integration.
- Aucun outillage de metriques financieres (volume de transactions, solde de
  tresorerie, taux d'echec de paiement) n'est expose — DECISION REQUIRED, voir
  section 16.
- Runbook operationnel redige (`docs/RUNBOOK.md`) : 8 scenarios (paiement bloque,
  reglement bloque, ordre expire, refund rejete, divergence de tresorerie, cle
  d'idempotence bloquee, base de donnees indisponible, redemarrage applicatif),
  chacun avec symptome/diagnostic/action sure/action interdite.

---

## 7. Database findings

- `ddl-auto: validate` en base (tous profils) — Hibernate ne modifie jamais le
  schema silencieusement, tout ecart entite/schema fait echouer le demarrage.
- 20 migrations Flyway revues, toutes additives a ce jour (aucune suppression de
  colonne/table) — un rollback applicatif sans rollback de schema reste possible en
  l'etat, a revalider migration par migration a chaque nouvelle version.
- Index presents sur toutes les colonnes de cle etrangere utilisees par les requetes
  courantes (`idx_orders_user_created`, `idx_payments_order`,
  `idx_treasury_tx_account_created`, `idx_treasury_tx_order`,
  `idx_wallet_transactions_wallet_created`, `idx_refunds_order`,
  `idx_osh_order_created`) — aucun index manquant identifie.
- Aucun N+1 accidentel identifie ; les seules boucles de traitement par element sont
  dans les schedulers, avec isolation transactionnelle intentionnelle et documentee.
- Pagination : la quasi-totalite des endpoints de liste utilise deja
  `Pageable`/`Page`. Un seul endpoint non pagine identifie :
  `AdminSettingsController.list()` — risque pratique faible (jeu de parametres
  systeme, taille fixe et petite), classe AMELIORATION FUTURE plutot que gap reel.

---

## 8. Secrets et configuration

- Aucun defaut de secret en profil `prod` (`JWT_SECRET`, `DB_PASSWORD`,
  `ADMIN_PASSWORD`) — `@NotBlank` sur les `@ConfigurationProperties` correspondantes,
  demarrage en echec explicite si absent.
- `.env.example` ne contient aucun secret reel ; `.gitignore` exclut `.env`, `.env.*`
  (sauf `.env.example`), `*.pem`, `*.key`, `secrets/`.
- `docker-compose.yml` : `JWT_SECRET`/`ADMIN_PASSWORD` passes sans valeur par defaut
  (forme nue `VARIABLE:` sans `${VAR:-defaut}`) — une variable absente reste absente
  cote conteneur plutot que de devenir une chaine vide presente, distinction
  documentee en commentaire dans le fichier et confirmee correcte.
- Ajout dans cette mission : `logging.pattern.level: "%5p [%X{requestId}]"`, seul
  point d'extension officiellement documente pour enrichir le pattern de log par
  defaut de Spring Boot sans le redefinir integralement.

---

## 9. Docker / deploiement

- `Dockerfile` : build multi-stage (JDK pour la compilation, JRE pour l'execution),
  utilisateur non-root (`converter`), `HEALTHCHECK` reel sur `/actuator/health`.
- **Verifie dans cette mission** : `docker build` reussi (image finale 234MB), puis
  demarrage complet via une copie isolee de `docker-compose.yml` (conteneurs et
  volume nommes distinctement pour ne pas interferer avec un environnement Docker
  local prealable) contre une vraie instance PostgreSQL conteneurisee — le conteneur
  backend atteint l'etat `healthy` apres migration Flyway (20 migrations),
  initialisation Hibernate et amorcage du compte administrateur de developpement.
  Pile de verification demontee proprement apres validation (conteneurs, reseau et
  volume temporaires supprimes).

---

## 10. CI/CD

- **Ajoute dans cette mission** : `.github/workflows/backend-ci.yml` — checkout,
  JDK 21 (Temurin), `./mvnw -B clean package` (execute build + tests unitaires ET
  d'integration Testcontainers, aucun plugin Failsafe distinct dans ce projet — une
  seule commande couvre tout, meme convention que `mvnw test` en local), publication
  des rapports Surefire en artefact.
- Declenchement sur push/PR touchant `backend/**` ou le workflow lui-meme, sur les
  branches `master`/`main`.
- Aucun pipeline de deploiement continu (CD) — DECISION REQUIRED, voir section 16.

---

## 11. Backup / recovery

**GAP OPERATIONNEL non corrige** : aucun mecanisme de sauvegarde automatisee de la
base PostgreSQL n'existe dans ce depot (ni job planifie, ni script, ni sauvegarde
managee documentee). Le volume Docker nomme (`converter-postgres-data`) assure la
persistance face a un redemarrage de conteneur, mais pas face a une perte de disque,
une corruption, ou une erreur operationnelle (suppression accidentelle de donnees).
DECISION REQUIRED, voir section 16.

---

## 12. Observabilite

- Identifiant de correlation par requete desormais coherent sur tous les chemins
  d'erreur (section 6).
- Aucune journalisation de secret constatee dans le code applicatif (verifie par
  recherche exhaustive des appels `log.*`) — seule exception deja connue et
  deliberee : mot de passe admin genere logue une fois au demarrage en **profil dev
  uniquement**.
- Aucune metrique applicative/financiere exposee (pas de Micrometer/Prometheus
  configure) — DECISION REQUIRED, voir section 16.

---

## 13. Tests

| Etape | Tests | Echecs | Erreurs |
|---|---|---|---|
| Avant cette mission (fin Mission 6) | 231 | 0 | 0 |
| Ajoutes dans cette mission | +5 (`RequestIdFilterIT` x4, `QuoteSecurityIT.create_withAmountExceedingColumnPrecision_returns400NotA500` x1) | — | — |
| Apres cette mission (suite complete) | **236** | **0** | **0** |

Deux tests `RequestIdFilterIT` ont initialement echoue lors de leur premiere
execution dans cette mission (voir section 14) et ont ete corriges avant ce compte
final.

---

## 14. Fichiers modifies

| Fichier | Changement | Raison |
|---|---|---|
| `backend/src/main/resources/application.yml` | Ajout `logging.pattern.level` | Injecter `requestId` dans chaque ligne de log (observabilite) |
| `backend/src/main/resources/application-prod.yml` | Ajout `springdoc.api-docs.enabled=false` / `springdoc.swagger-ui.enabled=false` | Fermer l'exposition anonyme du schema API complet en production |
| `backend/src/main/java/com/converter/security/RequestIdFilter.java` | Nouveau fichier | Filtre generant/validant `X-Request-ID`, alimente le MDC |
| `backend/src/main/java/com/converter/security/SecurityConfig.java` | Enregistrement de `RequestIdFilter` avant `UsernamePasswordAuthenticationFilter` | Toute requete, meme rejetee par le rate limiter ou l'auth JWT, porte un identifiant de correlation |
| `backend/src/main/java/com/converter/common/exception/GlobalExceptionHandler.java` | `traceId` derive de `MDC.get(RequestIdFilter.MDC_KEY)` au lieu d'un UUID genere au coup par coup | `traceId` == `X-Request-ID`, retrouvable dans les journaux |
| `backend/src/main/java/com/converter/security/SecurityResponseWriter.java` | Idem, pour les reponses 401/403 construites au niveau filtre | Coherence : ce chemin ne passe jamais par `GlobalExceptionHandler`, il en avait ete oublie |
| `backend/src/test/java/com/converter/security/RequestIdFilterIT.java` | Nouveau fichier (4 tests) | Prouve le contrat HTTP et la coherence `traceId`/`X-Request-ID` sur les deux chemins d'erreur |
| `.github/workflows/backend-ci.yml` | Nouveau fichier | CI minimale : compile + suite de tests complete a chaque push/PR |
| `backend/src/main/java/com/converter/quote/dto/CreateQuoteRequest.java` | Ajout `@Digits(integer=17, fraction=2)` sur `amountXof`/`amountCny` | Empecher un montant hors precision colonne d'atteindre la persistance |
| `backend/src/main/java/com/converter/treasury/dto/TreasuryAdjustmentRequest.java` | Ajout `@Digits(integer=19, fraction=2)` sur `amount` | Idem, aligne sur `NUMERIC(21,2)` |
| `backend/src/main/java/com/converter/payment/dto/SubmitPaymentRequest.java` | Ajout `@Digits(integer=17, fraction=2)` sur `receivedAmountXof` | Idem |
| `backend/src/main/java/com/converter/preferredrate/dto/CreatePreferredRateRequest.java` | Ajout `@Digits(integer=17, fraction=2)` sur `amountXof` | Idem |
| `backend/src/test/java/com/converter/quote/service/QuoteSecurityIT.java` | +1 test | Prouve le 400 propre au lieu d'un depassement numerique opaque |
| `docs/TREASURY_RECONCILIATION.md` | Nouveau fichier | Procedure de reconciliation quotidienne (Phase 26) |
| `docs/RUNBOOK.md` | Nouveau fichier | Runbook operationnel, 8 scenarios (Phase 42) |
| `docs/PRODUCTION_CHECKLIST.md` | Nouveau fichier | Checklist de mise en production (Phase 43) |
| `docs/PRODUCTION_READINESS_AUDIT.md` | Nouveau fichier | Ce rapport |

Aucune modification du pricing deja valide, aucune introduction de composant
d'architecture distribuee, aucune mise a jour de dependance majeure.

---

## 15. Risques restants

**CRITICAL** : aucun identifie.

**HIGH** :
- Spring Boot 3.5.16 a atteint sa fin de support open-source (2026-06-30) — aucun
  correctif de securite gratuit futur pour cette ligne. Risque structurel croissant
  avec le temps, pas une vulnerabilite ponctuelle exploitable aujourd'hui.
- Aucune sauvegarde automatisee de la base de donnees — risque de perte de donnees
  irreversible en cas d'incident disque/operationnel, pour un systeme financier.

**MEDIUM** :
- Etat CVE non confirme de maniere fiable pour `jjwt 0.12.7`, `springdoc-openapi
  2.8.17`, `mapstruct 1.6.3`, `flyway-core 11.7.2` — les recherches web menees dans
  cette mission ont produit des resultats trop peu fiables pour etre cites comme des
  constats confirmes (voir section 16, epistemic caution).
- Aucune metrique financiere/operationnelle exposee (observabilite au-dela des
  journaux).
- Aucun pipeline de deploiement continu.
- Absence de limite explicite de taille/profondeur JSON au-dela des defauts
  Tomcat/Jackson (Jackson recent integre deja des protections par defaut contre les
  attaques de type "billion laughs" — le risque reel residuel n'a pas ete quantifie
  precisement dans cette mission, d'ou le classement MEDIUM plutot que HIGH).

**LOW** :
- CVE-2026-54291 (pgJDBC 42.7.11, downgrade channel binding) — non exploitable dans
  la configuration actuelle, correctif 42.7.12 disponible sans risque de rupture.
- `AdminSettingsController.list()` non pagine — surface de risque pratique faible.
- Rate limiting uniquement sur le login.

---

## 16. Decisions necessitant arbitrage — DECISION REQUIRED

Je ne choisis pas a la place du responsable d'architecture pour les points suivants :

1. **Ligne Spring Boot** : rester sur 3.5.16 (EOL open-source atteint) en acceptant
   le risque documente, souscrire un support commercial (VMware Tanzu Spring), ou
   planifier une migration vers Spring Boot 4.x. Une migration majeure n'est pas
   entreprise unilateralement dans cette mission (regle explicite : ne pas mettre a
   jour une dependance majeure "pour etre moderne" — mais l'EOL est un fait different
   d'une preference de modernite, a arbitrer en connaissance de cause).
2. **Sauvegarde de base de donnees** : quel mecanisme (pg_dump planifie, snapshot du
   fournisseur d'hebergement, replication), quelle frequence, quelle retention, qui
   en est responsable. Non implemente dans cette mission (aucune infrastructure de
   sauvegarde n'existait a auditer ni a etendre).
3. **Rate limiting au-dela du login** : etendre a d'autres endpoints sensibles
   (creation de devis, soumission de paiement) ou laisser en l'etat (l'authentification
   requise sur ces endpoints limite deja l'abus anonyme).
4. **Metriques financieres/observabilite** : construire un tableau de bord minimal
   (Micrometer + export) avant la mise en production, ou s'appuyer sur la procedure
   de reconciliation manuelle quotidienne nouvellement documentee pour un demarrage
   en pilote.
5. **Mise a jour du driver PostgreSQL JDBC** vers 42.7.12+ : correctif mineur sans
   risque de rupture connu, mais reste une decision de gestion de version a valider
   plutot qu'appliquee unilateralement dans cette mission (regle : pas de mise a jour
   de dependance non demandee, meme mineure, sans validation explicite).
6. **Outillage SCA** (OWASP Dependency-Check/Snyk) : a integrer au pipeline CI pour
   remplacer la recherche web manuelle, dont la fiabilite s'est averee limitee dans
   cette mission pour plusieurs dependances.
7. **Pipeline de deploiement continu** : construire un CD, ou garder un deploiement
   manuel encadre par la checklist nouvellement redigee.

---

## 17. Verdict — Phase 48

**Financierement sur ?** OUI AVEC LIMITATIONS. Le pipeline transactionnel complet
(Quote→Order→Payment→Treasury→Settlement→Refund) est verrouille, teste en
concurrence reelle, et protege par des contraintes SQL independantes du code
applicatif. La seule limitation connue (fenetre de cle d'idempotence bloquee jusqu'a
5 minutes apres un crash) est documentee, bornee, et deliberement acceptee plutot que
masquee.

**Securise ?** AVEC LIMITATIONS. Le perimetre applicatif (authentification,
autorisation, CORS, CSRF, secrets, upload de fichiers, exposition Actuator/Swagger)
est solide et verifie. La limitation structurelle est externe au code : la ligne
Spring Boot utilisee a depasse sa fin de support open-source, ce qui n'est pas une
vulnerabilite active aujourd'hui mais un risque croissant sans arbitrage.

**Deployable operationnellement ?** AVEC LIMITATIONS. Le build et le demarrage Docker
sont verifies fonctionnels de bout en bout, une CI minimale existe, un runbook et une
checklist de production ont ete rediges. Il manque une sauvegarde automatisee de la
base de donnees et une observabilite financiere au-dela des journaux — deux lacunes
operationnelles reelles pour un systeme financier a l'echelle.

**Action recommandee : MVP PILOT ONLY.**

Justification : aucun constat de cette mission ne remet en cause l'integrite
financiere du systeme lui-meme — le coeur transactionnel a ete prouve, pas seulement
teste. Mais deployer a pleine echelle sans sauvegarde de base de donnees ni
arbitrage sur la ligne Spring Boot EOL serait premature pour un systeme financier.
Un pilote a perimetre reduit (volume limite, surveillance rapprochee, reconciliation
manuelle quotidienne suivant la procedure documentee) est compatible avec l'etat
actuel du systeme et permet de traiter les points de la section 16 sans bloquer
totalement le lancement.
