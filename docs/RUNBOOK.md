# Runbook operationnel — incidents courants

Statut : document operationnel (Phase 42, audit de fermeture 2026-09). Destine a un
operateur ADMIN ou a l'astreinte technique. Chaque scenario suit le meme format :
symptome observable, diagnostic (ou verifier), action sure, action interdite.

Convention : "action sure" ne modifie jamais l'etat metier sans passer par l'API
existante (jamais de `UPDATE` SQL direct sur `payments`, `orders`, `treasury_accounts`,
`refunds`, `settlements` — ces tables portent des invariants CHECK et des lignes de
ledger/historique qu'une ecriture directe romprait silencieusement). "Escalade"
indique quand arreter d'agir seul et remonter (astreinte technique / responsable
d'architecture) plutot que de continuer a essayer des actions correctives.

---

## 1. Paiement bloque (`Payment` reste en `PENDING`)

**Symptome** : un client a soumis un paiement (`POST /api/orders/{id}/payments`) mais
son ordre reste en `AWAITING_CONFIRMATION` au-dela du delai habituel.

**Diagnostic** :
- Consulter `GET /api/admin/payments/{id}` (ou la liste paginee des paiements en
  attente) pour verifier le statut reel et l'horodatage de soumission.
- Verifier que ce n'est pas simplement une preuve de paiement en attente de revue
  humaine (flux normal, pas un incident) — voir `PaymentService`.
- Verifier les logs applicatifs autour de l'horodatage de soumission (rechercher le
  `requestId` de la requete correspondante, present dans chaque ligne de log, cf.
  `logging.pattern.level` dans `application.yml`) pour une exception silencieuse.

**Action sure** :
- Si la preuve de paiement est valide : confirmer via l'endpoint admin dedie
  (`POST /api/admin/payments/{id}/confirm`), jamais par ecriture SQL directe — cet
  endpoint ecrit aussi la ligne de ledger tresorerie correspondante dans la meme
  transaction.
- Si la preuve est invalide/frauduleuse : rejeter via l'endpoint admin dedie, avec
  motif.

**Action interdite** :
- Ne jamais faire passer un `Payment` de `PENDING` a `CONFIRMED` par une requete SQL
  directe : cela ne genererait ni ligne de ledger, ni evenement de settlement, et
  romprait la coherence tresorerie/ordre.

**Escalade** : si plusieurs paiements de clients differents restent bloques
simultanement (pas un cas isole), ou si les journaux revelent une exception
recurrente lors de la confirmation — remonter avant de confirmer/rejeter en serie ;
un bug systemique traite au coup par coup masquerait le probleme reel.

---

## 2. Reglement bloque (`Settlement` reste en `PENDING`)

**Symptome** : le paiement est confirme mais le beneficiaire n'a pas recu ses fonds
CNY ; l'ordre reste au-dela du delai habituel sans passer `SETTLED`.

**Diagnostic** :
- `GET /api/admin/settlements/{id}` pour verifier le statut.
- Verifier si un `Refund` a deja ete traite pour cet ordre : `Settlement.execute()`
  est bloque explicitement si un remboursement a ete deja `PROCESSED` sur le meme
  paiement (regle ajoutee en Phase FINAL HARDENING) — dans ce cas le blocage est
  **normal**, pas un incident.
- Sinon, verifier le solde CNY disponible (`GET /api/admin/treasury/accounts/CNY`) :
  un `available()` insuffisant empeche la consommation de la reservation.

**Action sure** :
- Si fonds insuffisants : alimenter la tresorerie CNY via
  `POST /api/admin/treasury/deposit` (avec `Idempotency-Key`), puis reessayer
  l'execution du reglement via l'endpoint admin dedie.
- Si un remboursement a deja ete traite : ne rien faire, c'est le comportement
  attendu — le client a ete rembourse, pas paye deux fois.

**Action interdite** :
- Ne jamais executer un `Settlement` en contournant la garde "refund deja traite" —
  elle existe precisement pour empecher un double paiement au client.

**Escalade** : si le solde CNY est insuffisant de maniere recurrente (pas un
incident isole) — remonter au responsable tresorerie plutot que de multiplier les
depots manuels ; cela indique un probleme de dimensionnement/reapprovisionnement,
pas un incident technique ponctuel.

---

## 3. Ordre expire de maniere inattendue

**Symptome** : un client signale qu'un ordre est passe `EXPIRED` alors qu'il pensait
avoir paye a temps.

**Diagnostic** :
- `GET /api/admin/orders/{id}` puis l'historique de statuts
  (`order_status_history`, expose via l'API admin) pour voir l'horodatage exact de
  l'expiration vs. celui de la soumission du paiement.
- `OrderExpirationScheduler` tourne toutes les 60s (`order.expiration.scheduler.
  fixed-delay-ms`) et expire tout ordre `AWAITING_PAYMENT` dont le delai est depasse,
  en liberant la reservation CNY associee — comportement normal si le paiement a
  reellement ete soumis apres l'expiration.

**Action sure** :
- Si le client a une preuve de paiement horodatee avant l'expiration : traiter au cas
  par cas manuellement (pas de flux "reouvrir un ordre expire" existant — c'est une
  DECISION PRODUIT a arbitrer : creer un nouvel ordre au meme taux, ou un autre
  traitement). Ne pas inventer de mecanisme de reouverture non demande.

**Action interdite** :
- Ne jamais repasser un ordre `EXPIRED` a `AWAITING_PAYMENT` par ecriture directe : la
  reservation CNY associee a deja ete liberee et pourrait avoir ete reallouee ailleurs.

**Escalade** : au responsable produit pour toute demande de "reouverture" repetee
(plus qu'un cas isole) — c'est le signal qu'une DECISION PRODUIT explicite est
necessaire (delai d'expiration trop court ? flux de reouverture a concevoir ?),
pas quelque chose a traiter indefiniment au cas par cas.

---

## 4. Remboursement rejete (`Refund` en `REJECTED`)

**Symptome** : un remboursement a ete rejete, le client insiste.

**Diagnostic** :
- `GET /api/admin/refunds/{id}` pour le motif de rejet enregistre.

**Action sure** :
- Une nouvelle tentative de remboursement APRES un rejet est un cas gere
  explicitement (contrairement au paiement, objectivement terminal) : un nouvel appel
  `POST /api/admin/refunds` sur le meme paiement est accepte grace a l'index unique
  partiel `uq_refunds_payment_active` (n'exclut que les refunds `PENDING`/
  `PROCESSED`, pas `REJECTED`) — voir migration `V20__refund_retry_after_rejection.sql`.

**Action interdite** :
- Ne jamais forcer un `Refund` de `REJECTED` a `PROCESSED` par ecriture directe :
  cela ecrirait un mouvement de tresorerie sans passer par `RefundService`, donc sans
  ligne de ledger coherente.

**Escalade** : si le motif de rejet est conteste par le client au-dela d'un simple
malentendu (ex. suspicion de fraude, litige) — remonter au responsable produit/
conformite ; ce runbook couvre le mecanisme technique, pas l'arbitrage du litige.

---

## 5. Divergence de tresorerie (treasury discrepancy)

**Symptome** : la procedure de reconciliation quotidienne (voir
`TREASURY_RECONCILIATION.md`) releve un ecart entre le solde materialise et le ledger,
ou entre le ledger et les ordres/paiements/remboursements attendus.

**Diagnostic** :
- Isoler la premiere transaction du ledger ou `balance_after` ne correspond plus a la
  somme cumulee attendue.
- Verifier si une intervention hors API a eu lieu (migration manuelle, script
  ponctuel, acces direct a la base) — c'est la cause la plus probable, le code
  applicatif garantissant lui-meme la coherence solde/ledger (meme transaction).

**Action sure** :
- Documenter l'ecart (montant, devise, horodatage estime) avant toute correction.
- Corriger UNIQUEMENT via `POST /api/admin/treasury/adjust` avec un motif renvoyant
  explicitement au ticket d'investigation, pour que la correction elle-meme laisse
  une trace dans le ledger.

**Action interdite** :
- Ne jamais executer un `UPDATE treasury_accounts SET balance = ...` directement : la
  correction doit elle-meme etre tracee comme un mouvement de ledger, sinon le
  probleme se reproduit a la prochaine reconciliation.

**Escalade** : systematique pour toute divergence non expliquee par une cause
identifiee avec certitude (voir Diagnostic) — un ecart de tresorerie non compris ne
doit jamais etre "corrige" a l'aveugle avec `/adjust` ; remonter au responsable
d'architecture avant toute correction si la cause n'est pas etablie.

---

## 6. Cle d'idempotence bloquee ("stuck pending")

**Symptome** : un client reessaie une requete avec la meme `Idempotency-Key` et
recoit systematiquement une erreur de conflit, sans que l'operation d'origine
n'ait jamais abouti (ex. crash serveur entre l'enregistrement de la cle et la
finalisation).

**Diagnostic** :
- C'est une limitation residuelle **connue et acceptee** (voir Mission 6 — audit de
  fermeture, "READY WITH ACCEPTED LIMITATION") : une cle peut rester `PENDING`
  jusqu'a ce que `IdempotencyPendingCleanupScheduler.reclaimStalePendingKeys`
  (toutes les 300s) la reclame. Verifier l'age de la cle bloquee (`createdAt`) : si
  elle a moins de 5 minutes, c'est une attente normale du scheduler, pas un incident.
- Un correctif par verrouillage a ete explicitement etudie et REJETE (il provoquerait
  un deadlock avec le chemin de liberation "fail-fast" existant) — ne pas retenter
  cette piste sans nouvelle preuve.

**Action sure** :
- Attendre le prochain passage du scheduler (au plus 5 minutes).
- Si le blocage persiste au-dela de 2 cycles de scheduler (>10 min), verifier que le
  scheduler tourne bien (logs applicatifs, `@Scheduled` actif) avant d'envisager
  toute action manuelle.

**Action interdite** :
- Ne jamais supprimer une ligne `idempotency_keys` a la main pour "debloquer" un
  client sans avoir confirme que l'operation d'origine n'a bel et bien PAS abouti
  (sinon le client pourrait rejouer une operation financiere deja executee).

**Escalade** : si le scheduler `IdempotencyPendingCleanupScheduler` semble arrete
(aucune reclamation apres plusieurs cycles attendus) — remonter immediatement, c'est
un dysfonctionnement du mecanisme de recuperation lui-meme, pas une simple attente.

---

## 7. Base de donnees indisponible

**Symptome** : `/actuator/health` renvoie `DOWN`, les requetes echouent en 503/500.

**Diagnostic** :
- Le pool Hikari a un `connection-timeout` de 10s (`application.yml`) : au-dela, les
  requetes echouent plutot que de bloquer indefiniment — comportement attendu, pas un
  bug.
- Verifier la disponibilite reseau/processus PostgreSQL independamment de
  l'application (l'application ne peut rien faire de plus qu'echouer proprement tant
  que la base est injoignable — il n'existe pas de cache ou de mode degrade).

**Action sure** :
- Restaurer la disponibilite de PostgreSQL (redemarrage du service, verification
  disque/reseau). Aucune action cote application n'est necessaire : au retour de la
  base, le pool Hikari se reconnecte automatiquement.

**Action interdite** :
- Ne pas redemarrer l'application backend en boucle en esperant "relancer" la
  connexion : le probleme est cote base, un redemarrage applicatif n'apporte rien
  tant que PostgreSQL reste injoignable, et ajoute du bruit dans les journaux.

**Escalade** : immediate si l'indisponibilite depasse quelques minutes — chaque
minute de base injoignable est une minute ou aucun client ne peut creer de devis,
payer, ou etre remboursse ; ce n'est jamais un incident a traiter "au fil de l'eau".

---

## 8. Application DOWN (le processus backend ne repond pas ou ne demarre pas)

**Symptome** : `/actuator/health` ne repond pas du tout (timeout/connexion refusee,
a distinguer du scenario 7 ou l'application repond mais annonce `DOWN` a cause de la
base). Le conteneur `converter-backend` est en `Exited`/`Restarting` en boucle
(`docker ps`), ou le processus JVM a disparu.

**Diagnostic** :
- `docker logs converter-backend --tail 200` (ou equivalent hors Docker) : chercher
  la derniere ligne avant l'arret — un echec de validation Flyway/Hibernate au
  demarrage (`ddl-auto: validate`) s'y trouve explicitement, de meme qu'un
  `OutOfMemoryError` ou une exception non retrapee au demarrage.
- `docker inspect converter-backend --format '{{.State.Status}} {{.State.ExitCode}}'`
  pour distinguer un arret volontaire (code 0/143) d'un crash (tout autre code).
- Verifier que ce n'est pas simplement le scenario 7 deguise : si les journaux
  montrent des tentatives de connexion PostgreSQL en echec repetees, traiter comme
  "Base de donnees indisponible", pas comme un crash applicatif.

**Action sure** :
- Si le journal identifie clairement la cause (ex. variable d'environnement
  manquante empechant le demarrage — `JWT_SECRET`, `DB_PASSWORD`) : corriger la
  configuration puis redemarrer normalement (voir scenario "Redemarrage de
  l'application" ci-dessous).
- Si la cause n'est pas identifiable dans les journaux disponibles : conserver les
  journaux complets avant tout redemarrage (ils peuvent disparaitre si le conteneur
  est recree plutot que simplement redemarre), puis redemarrer.

**Action interdite** :
- Ne jamais recreer le conteneur (`docker compose up --force-recreate` ou
  equivalent) avant d'avoir recupere ses journaux : un conteneur recree perd sa
  sortie console precedente, la seule preuve directe de la cause du crash.
- Ne jamais modifier `ddl-auto` pour contourner un echec de validation Flyway/
  Hibernate au demarrage : cet echec signale une divergence reelle entre le schema
  et les entites JPA, qui doit etre comprise, pas masquee.

**Escalade** : immediate si la cause n'est pas identifiable apres inspection des
journaux, ou si le redemarrage echoue une seconde fois pour une raison differente —
ne pas s'acharner a redemarrer en boucle sans comprendre la cause racine.

---

## 9. Redemarrage de l'application

**Symptome** : deploiement, mise a jour, ou redemarrage planifie/impose.

**Diagnostic** : N/A — procedure preventive.

**Action sure** :
- L'application est **stateless** (JWT, pas de session serveur) : un redemarrage
  n'affecte aucune session utilisateur active au-dela d'une reconnexion cote client.
- Au demarrage, Flyway applique les migrations en attente automatiquement ; `ddl-
  auto: validate` fait echouer le demarrage si le schema ne correspond pas aux
  entites JPA (fail-fast plutot que tourner dans un etat incoherent) — un echec de
  demarrage ici doit etre traite comme un vrai incident, pas contourne.
- Verifier `ADMIN_SEED_ENABLED` : DOIT etre `false` en production courante (le seed
  n'est destine qu'au tout premier demarrage) — un redemarrage avec le seed encore
  actif re-informe cette procedure de detection au prochain runbook mais ne cree pas
  de doublon (le seeding verifie l'absence prealable du compte).
- Verifier apres redemarrage : `GET /actuator/health` retourne `UP`, puis quelques
  requetes de fumee sur les endpoints critiques (login, consultation d'un ordre).

**Action interdite** :
- Ne jamais desactiver Flyway (`spring.flyway.enabled=false`) pour "accelerer" un
  redemarrage : la validation de schema fail-fast est la seule protection contre un
  demarrage silencieusement incoherent avec les entites JPA.

**Escalade** : si le demarrage echoue de maniere repetee apres correction de la
cause apparente — traiter comme le scenario "Application DOWN" ci-dessus plutot que
de continuer a redemarrer.

---

## 10. Restore PostgreSQL

**Symptome** : perte ou corruption averee de la base de production (disque
defaillant, suppression accidentelle, corruption), necessitant une restauration
complete depuis une sauvegarde (voir `scripts/backup.sh`).

**Procedure** (verifiee de bout en bout dans l'audit de fermeture 2026-09 — build
Docker, sauvegarde, restauration vers une instance neuve, redemarrage applicatif
contre la base restauree, tous confirmes fonctionnels) :

1. **Arreter l'application** (`docker compose stop backend` ou equivalent) : elle ne
   doit jamais ecrire pendant une restauration en cours.
2. **Identifier la sauvegarde a restaurer** (`ls -t backups/` — la plus recente,
   sauf instruction contraire explicite lors de l'investigation de l'incident).
3. **Preparer l'instance PostgreSQL cible** :
   - Cas "meme instance corrompue mais processus PostgreSQL sain" : la base cible
     est la base existante (la restauration avec `--clean --if-exists` remplace
     integralement son contenu).
   - Cas "instance perdue" : demarrer une nouvelle instance PostgreSQL vierge
     (`docker compose up -d postgres` sur un volume neuf, ou equivalent).
4. **Restaurer** : `./scripts/restore.sh <fichier.dump> --yes [nom-conteneur-cible]`.
   Le flag `--yes` est obligatoire (operation destructive sur la cible) — ne jamais
   le scripter dans un contexte non supervise.
5. **Verifier les donnees restaurees**, au minimum :
   ```
   docker exec <conteneur-postgres> psql -U <user> -d <db> -c "
     select 'orders' t, count(*) from orders
     union all select 'payments', count(*) from payments
     union all select 'treasury_accounts', count(*) from treasury_accounts
     union all select 'treasury_transactions', count(*) from treasury_transactions
     union all select 'settlements', count(*) from settlements
     union all select 'refunds', count(*) from refunds
     union all select 'quotes', count(*) from quotes
     union all select 'audit_logs', count(*) from audit_logs;"
   ```
   Comparer ces comptes a ceux attendus (derniere reconciliation quotidienne
   connue, voir `TREASURY_RECONCILIATION.md`) — un ecart signale une sauvegarde
   trop ancienne ou une restauration partielle.
6. **Redemarrer l'application** pointee vers l'instance restauree. Flyway valide
   automatiquement que le schema restaure correspond a la version courante des
   migrations (`flyway_schema_history` fait partie de la sauvegarde) ; `ddl-auto:
   validate` echoue au demarrage si une incoherence subsiste — un demarrage reussi
   est en soi une confirmation que la restauration est structurellement saine.
7. **Healthcheck** : `GET /actuator/health` doit repondre `UP`.
8. **Verification fonctionnelle minimale** avant de rouvrir aux clients : une
   connexion, une consultation d'ordre existant, verifier que les soldes de
   tresorerie affiches correspondent a l'etape 5.
9. Documenter l'incident : cause de la perte, sauvegarde utilisee (horodatage),
   duree totale d'indisponibilite (pour affiner le RTO reel vs. estimé — voir
   `PRODUCTION_CHECKLIST.md`), et toute transaction survenue entre l'horodatage de
   la sauvegarde et l'incident (perte de donnees potentielle dans cette fenetre —
   c'est exactement ce que mesure le RPO).

**Action interdite** :
- Ne jamais restaurer directement sur l'instance de production sans etape 1
  (application arretee) : une ecriture concurrente pendant la restauration
  produirait un etat incoherent, pire que l'incident d'origine.
- Ne jamais improviser une commande `pg_restore`/`psql` en dehors de
  `scripts/restore.sh` en situation d'urgence : le script encode deliberement
  `--clean --if-exists --no-owner --no-privileges`, des options dont l'omission
  cause des echecs partiels difficiles a diagnostiquer sous pression.
- Ne jamais supprimer la sauvegarde utilisee immediatement apres une restauration
  reussie : la conserver jusqu'a confirmation complete (etapes 7-8) que la
  restauration est saine.

**Escalade** : toujours — une restauration de production est par nature un
evenement majeur, jamais une action solo non supervisee. Impliquer le responsable
d'architecture avant l'etape 4 (le seul point reellement destructif de la
procedure), pas apres.
