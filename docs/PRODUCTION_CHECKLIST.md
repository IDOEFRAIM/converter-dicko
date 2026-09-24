# Checklist de mise en production

Statut : checklist operationnelle (Phase 43, audit de fermeture 2026-09). A executer
avant chaque premiere mise en production, et revue avant chaque deploiement majeur.

## Secrets et configuration

- [ ] `JWT_SECRET` genere aleatoirement (`openssl rand -base64 48` ou equivalent),
      distinct de toute valeur utilisee en dev/test, stocke dans un gestionnaire de
      secrets (pas dans `.env` versionne ni en clair dans un pipeline CI en clair).
- [ ] `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` pointent vers l'instance PostgreSQL de
      production, avec un mot de passe distinct de dev/test.
- [ ] `SPRING_PROFILES_ACTIVE=prod` explicitement positionne (sinon le profil par
      defaut `dev` — cles/secrets de developpement — serait actif).
- [ ] `ADMIN_SEED_ENABLED=false` (le seed automatique d'un compte admin ne doit
      tourner qu'au tout premier demarrage, jamais en continu).
- [ ] `CORS_ALLOWED_ORIGINS` positionne sur le(s) domaine(s) frontend reel(s), pas sur
      `localhost`.
- [ ] Aucun secret (mot de passe, `JWT_SECRET`, token) ne figure dans les journaux —
      verifie manuellement lors de cet audit pour les chemins de log existants (voir
      rapport final, section Observabilite).

## Base de donnees

- [x] Sauvegarde : `scripts/backup.sh` (pg_dump format custom, horodate, retention
      configurable, stocke HORS du volume PostgreSQL) — implemente et **verifie de
      bout en bout** dans l'audit de fermeture (backup reel -> instance PostgreSQL
      neuve -> restore -> demarrage applicatif reussi contre la base restauree,
      comptes de lignes et integrite relationnelle confirmes identiques).
- [ ] DECISION REQUIRED — planification de l'execution automatique du script (aucun
      ordonnanceur systeme/cron/service planifie ne l'invoque encore ; le script
      existe et fonctionne mais rien ne l'appelle seul pour l'instant). Voir RPO/RTO
      ci-dessous pour la frequence recommandee.
- [ ] DECISION REQUIRED — RPO/RTO cible pour le pilote. Aucune exigence metier
      formelle n'existe encore ; proposition (a valider par le responsable
      d'architecture) :
      - **RPO propose : 24h** (backup quotidien via `scripts/backup.sh`) — perte
        maximale de donnees en cas d'incident : les transactions survenues entre le
        dernier backup et l'incident. Un RPO plus court (ex. 6h) est possible en
        augmentant simplement la frequence d'execution du meme script, au prix d'un
        volume de stockage de sauvegardes proportionnellement plus eleve.
      - **RTO propose : 30 minutes** pour un jeu de donnees de taille pilote —
        estimation operationnelle (localiser la sauvegarde, executer
        `scripts/restore.sh`, verifier, redemarrer l'application, healthcheck),
        PAS mesuree sur un volume de production reel : le test effectue dans cette
        mission portait sur une base quasi vide (quelques dizaines de lignes). Le
        temps de `pg_dump`/`pg_restore` croit avec le volume reel de donnees — a
        remesurer periodiquement une fois en production (ex. trimestriellement) et
        a ajuster si le RTO cible n'est plus tenu.
- [x] `spring.jpa.hibernate.ddl-auto=validate` confirme actif en profil `prod`
      (empeche Hibernate de modifier le schema silencieusement).
- [x] Les 20 migrations Flyway ont ete appliquees avec succes sur une base vierge
      representative de la production (verifie via le build Docker de cette mission,
      et re-verifie via le cycle de restauration complet).
- [x] Le volume de donnees PostgreSQL est persistant et sauvegarde independamment du
      cycle de vie du conteneur (`docker-compose.yml` utilise deja un volume nomme).

## Securite applicative

- [x] `springdoc.api-docs.enabled=false` et `springdoc.swagger-ui.enabled=false`
      confirmes en profil `prod` (mission precedente).
- [x] Actuator expose `health`, `info` et `metrics` uniquement
      (`management.endpoints.web.exposure.include`, etendu a `metrics` dans cette
      mission — Micrometer core deja present via l'actuator, aucune nouvelle
      dependance) ; `env`/`beans`/`configprops`/`mappings`/`heapdump` ne sont jamais
      enregistres (verifie par `ActuatorSecurityIT` : 404, pas 401/403 — la route
      n'existe simplement pas). `metrics` exige une authentification (verifie par
      test), comme `info`.
- [ ] DECISION REQUIRED — terminaison TLS. `server.forward-headers-strategy:
      framework` est deja configure cote application (les en-tetes
      `X-Forwarded-Proto`/`X-Forwarded-For` d'un reverse proxy de confiance sont
      deja correctement pris en compte pour construire les URLs et l'IP client —
      rien a changer cote code). Ce qui reste a decider et a mettre en place hors de
      ce depot : quel reverse proxy (nginx/Caddy/Traefik/load balancer manage),
      quel mecanisme d'emission de certificat (Let's Encrypt, certificat manage par
      l'hebergeur). Aucun service de ce type n'existe dans `docker-compose.yml`
      aujourd'hui — volontairement, une decision d'hebergement n'a pas a etre prise
      unilateralement par l'implementeur.
- [x] Rate limiting etendu au-dela du seul login dans cette mission — inscription
      et endpoints d'ecriture authentifies les plus exposes (creation de devis,
      creation d'ordre, soumission de paiement) sont desormais aussi proteges
      (`RateLimitFilter`, base sur l'IP cliente, en memoire — voir rapport final
      pour la justification du choix IP plutot que par utilisateur). Comme pour le
      login : s'assurer que le reverse proxy transmet `X-Forwarded-For`
      correctement, sinon toutes les requetes semblent venir de la meme IP.

## Dependances

- [ ] DECISION REQUIRED : Spring Boot 3.5.16 a atteint sa fin de support OSS le
      2026-06-30 (confirme via endoflife.date et le changelog officiel) — aucun
      correctif de securite gratuit ne sera plus publie pour cette ligne. Decider
      entre : migration vers Spring Boot 4.x, souscription a un support commercial
      (VMware Tanzu), ou acceptation du risque documentee. Voir rapport final pour
      la recommandation motivee (KEEP TEMPORARILY).
- [x] Driver PostgreSQL JDBC mis a jour vers 42.7.13 (etait 42.7.11, gere par le BOM
      Spring Boot) — corrige CVE-2026-54291, suite de tests complete confirmee
      verte apres la mise a jour.
- [ ] Executer un outil SCA (OWASP Dependency-Check, Snyk, ou equivalent) sur
      l'ensemble du `pom.xml` avant la premiere mise en production : les recherches
      web menees dans cette mission n'ont pas pu confirmer/infirmer de maniere fiable
      l'etat CVE de `jjwt 0.12.7`, `springdoc-openapi 2.8.17`, `mapstruct 1.6.3`,
      `flyway-core 11.7.2` — un outil dedie donne un resultat verifiable, une
      recherche web manuelle non.

## CI/CD

- [x] Le workflow `.github/workflows/backend-ci.yml` compile + execute la suite de
      tests complete (unitaire + integration Testcontainers) a chaque push/PR ;
      aucune etape de deploiement n'existe dans ce workflow, donc rien ne peut se
      deployer automatiquement si les tests echouent — il n'y a simplement rien qui
      deploie du tout pour l'instant (voir DECISION REQUIRED ci-dessous).
- [ ] DECISION REQUIRED : aucun pipeline de deploiement automatise n'existe (le CI
      actuel compile/teste seulement) — decider du mecanisme de deploiement
      (manuel encadre par cette checklist, ou pipeline CD a construire).

## Observabilite

- [x] Chaque reponse HTTP porte un `X-Request-ID` et, en cas d'erreur, un `traceId`
      identique dans le corps de reponse — verifie coherent sur les deux chemins
      (controleurs via `GlobalExceptionHandler`, rejets au niveau filtre via
      `SecurityResponseWriter`).
- [x] Chaque ligne de log applicatif porte le `requestId` de la requete en cours
      (`logging.pattern.level`), permettant de retrouver l'integralite des journaux
      d'un incident a partir du `traceId` rendu au client.
- [x] `/actuator/metrics` expose (authentifie) : distribution des codes de statut
      HTTP (`http.server.requests`), sante JVM — visibilite operationnelle minimale
      sans nouvel outil, ajoute dans cette mission.
- [ ] DECISION REQUIRED : aucune metrique **financiere** dediee (volume de
      transactions, solde tresorerie, taux d'echec paiement) n'est exposee — au-dela
      de ce que `/actuator/metrics` donne generiquement. Evaluer si un tableau de
      bord minimal est necessaire avant la mise en production, ou si la procedure de
      reconciliation manuelle quotidienne (`TREASURY_RECONCILIATION.md`) suffit au
      demarrage du pilote.

## Stockage de fichiers et retention de donnees

- [x] Preuves de paiement/reglement : validation par magic bytes, cle de stockage
      generee cote serveur (jamais derivee d'une entree client), hors racine web,
      servies uniquement en piece jointe a l'endpoint authentifie du proprietaire de
      l'ordre (`PaymentController.downloadProof` — "Reserve au proprietaire de
      l'ordre. Servi en piece jointe, jamais en ligne.") — aucune URL publique.
      Deja conforme, verifie sans modification necessaire.
- [ ] DECISION REQUIRED : aucune politique de retention/archivage/suppression
      n'existe pour les donnees sensibles (preuves de paiement, telephone,
      coordonnees beneficiaire, `audit_logs`). Rien n'est supprime automatiquement
      aujourd'hui (choix par defaut le plus sur pour un systeme financier — les
      donnees historiques ne doivent jamais disparaitre sans decision explicite).
      A trancher : duree de conservation legale/reglementaire applicable, politique
      d'archivage le cas echeant.

## Verification finale avant bascule

- [x] Build Docker de l'image backend reussi et conteneur atteignant l'etat `healthy`
      contre une base PostgreSQL reelle.
- [x] Cycle complet sauvegarde -> restauration verifie de bout en bout : instance
      PostgreSQL neuve, restauration reussie, redemarrage applicatif contre la base
      restauree confirme (Flyway/Hibernate valident sans intervention), comptes de
      lignes et integrite relationnelle identiques a l'original.
- [x] Suite de tests complete verte : **246 tests, 0 echec, 0 erreur** (231 a la
      cloture de la mission precedente, +15 dans cette mission : rate limiting
      etendu, exposition Actuator/metrics — voir rapport final pour le detail).
- [ ] Procedure de rollback identifiee (revenir a l'image Docker precedente ; les
      migrations Flyway de ce projet sont toutes additives — aucune ne supprime de
      colonne/table existante a ce jour, donc un rollback applicatif sans rollback de
      schema reste possible, sous reserve de revalider ce point migration par
      migration avant chaque nouvelle version).
