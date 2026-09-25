# Intégration TransFi BizPay — Phase 1

Ce document décrit l'intégration TransFi BizPay (payin/payout automatisés) telle
qu'implémentée à ce stade, ce qui est confirmé contre la documentation officielle
TransFi et ce qui reste **provisoire**, et comment passer en production en toute
sécurité.

## Pourquoi cette intégration

Aujourd'hui, un ordre est réglé manuellement : le client déclare avoir payé (preuve
uploadée), un administrateur vérifie et exécute le décaissement CNY lui-même
(`SettlementService`). C'est lent et demande une intervention humaine à chaque
ordre.

TransFi BizPay permettrait d'automatiser les deux jambes :
- **Payin** : encaissement XOF (mobile money/banque) auprès du client.
- **Payout** : décaissement vers le bénéficiaire, en CNY (ou USDT en relais si le
  CNY direct n'est pas disponible — non implémenté dans cette phase).

Le modèle "sans marge sur le taux de change" de TransFi impose de facturer notre
marge comme des **frais de service explicites** (déjà le cas côté produit,
`Order.feeXof`), jamais comme un écart de taux caché — TransFi ne permet pas de
marger sur le taux qu'il applique.

## État actuel (Phase 1) — ce qui est fait

**Rien de tout ceci n'est actif par défaut.** Le flux manuel reste le chemin actif
pour tous les clients tant qu'un administrateur ne choisit pas explicitement de
router un ordre vers TransFi (Phase 1), et tant que `TRANSFI_ENABLED` n'est pas
mis à `true` (voir plus bas).

### Grand livre interne (`ledger_entries`)

Indépendant de TransFi : trace notre revenu réel (`SERVICE_FEE`, extrait de
`Order.feeXof`) dès qu'un ordre passe `COMPLETED`, que le règlement ait été
exécuté manuellement (aujourd'hui) ou via TransFi (demain) — un seul point
d'entrée (`OrderService#transitionToCompleted` → `LedgerService.recordServiceFeeForOrder`),
jamais deux chemins qui pourraient diverger. Une fois TransFi actif, les frais
prestataire réels (`PROVIDER_FEE`) y sont aussi enregistrés, à l'extraction du
webhook de payout.

`amount` est toujours positif ; le signe économique (revenu vs coût) est porté
par `LedgerEntryType`, jamais par le signe de la valeur.

Consultable en lecture seule : `GET /api/admin/ledger`, `GET /api/admin/ledger/orders/{orderId}`.

### Domaine TransFi (`transfi_orders`, `transfi_webhook_events`)

- `TransfiOrder` : un enregistrement par (ordre, sens) — au plus un `PAYIN` et un
  `PAYOUT` par ordre (`uq_transfi_orders_order_direction`).
- `TransfiWebhookEvent` : trace de chaque webhook déjà traité — seule protection
  réelle contre le retraitement d'une livraison en double (`providerEventId`
  unique).

### Routage explicite (`AdminTransfiController`)

`POST /api/admin/transfi/orders/{orderId}/payin` : geste **explicite** d'un
administrateur, seul point d'entrée qui crée un payin TransFi. Ne fait jamais
progresser le statut de l'ordre lui-même — c'est la confirmation TransFi (webhook)
qui le fera, jamais à l'optimiste. Échoue en 503 (`TRANSFI_UNAVAILABLE`) si
`app.transfi.enabled=false` ou si les identifiants sont absents.

`GET /api/admin/transfi/orders` : vue de rapprochement (statut TransFi tel que
rapporté par le dernier webhook reçu).

### Webhook (`TransfiWebhookController`)

`POST /api/webhooks/transfi` — route publique (aucun jeton porté possible,
TransFi n'est pas un utilisateur connecté), authentifiée par vérification de
signature HMAC-SHA256 (constante en temps, `WebhookSignatureVerifier`), jamais
par Spring Security.

Idempotence : `providerEventId` est inséré dans `transfi_webhook_events` **avant**
tout traitement métier, dans la même transaction que ce traitement
(`TransfiOrchestrationService.handleWebhook`) — un doublon (contrainte unique
violée) est détecté immédiatement et ignoré sans aucun effet de bord ; si le
traitement métier échoue ensuite, toute la transaction (y compris l'insertion de
l'événement) est annulée, ce qui permet à TransFi de renvoyer légitimement le
même webhook plus tard.

Répond toujours `200` une fois la signature validée, même si l'ordre est inconnu
ou le traitement dégradé (voir le fichier pour le détail) — un code d'erreur
ferait retenter TransFi indéfiniment un webhook déjà reçu et journalisé.

### Orchestration (`TransfiOrchestrationService`)

Réutilise **exactement** les mêmes transitions que le flux manuel
(`OrderService.transitionToPaymentVerified/transitionToProcessing/transitionToCompleted/transitionToRejected`)
et la même consommation de réservation de trésorerie CNY
(`TreasuryService.consume`) — un seul point d'entrée, jamais une seconde
implémentation qui pourrait diverger du flux manuel.

- **Payin réussi** → `PAYMENT_VERIFIED` → `PROCESSING` → création immédiate du
  payout correspondant.
- **Payin échoué** → l'ordre est rejeté (comme un rejet manuel de preuve de
  paiement).
- **Payout réussi** → consommation de la réservation CNY (une seule fois) →
  `COMPLETED` → extraction best-effort du frais prestataire (`PROVIDER_FEE`).
- **Payout échoué** → **l'ordre n'est JAMAIS annulé automatiquement** (l'argent du
  client est déjà encaissé côté payin ; annuler créerait un risque de double
  décaissement ou de perte de traçabilité). L'ordre reste `PROCESSING`, visible
  dans la liste admin — intervention manuelle requise (voir plus bas).

## Ce qui est confirmé vs provisoire

**Confirmé** (implémenté, testé, cohérent avec le reste du backend) :
- Authentification sortante en HTTP Basic (`clientId:clientSecret` en Base64).
- Authentification du webhook entrant par signature.
- Le modèle "frais de service, pas de marge sur le taux".
- Le principe idempotence webhook + statuts internes stables
  (`TransfiOrderStatus`), traduits depuis le statut brut TransFi.
- Les invariants de trésorerie/audit/état d'ordre.

**PROVISOIRE — à vérifier ligne à ligne contre la documentation officielle
TransFi (ou une collection Postman fournie par TransFi) avant toute activation
en production** :
- Chemins d'endpoint : `POST /v3/orders`, `GET /v3/orders/{id}` (`TransFiHttpClient`).
- Forme du payload de création d'ordre : champs `type`/`amount`/`currency`/
  `reference`/`beneficiary.name`/`beneficiary.account`.
- Forme de la réponse : champs `id`/`status`/`payUrl`.
- Vocabulaire de statut brut (`success`/`completed`/`paid`/`settled` → `SUCCESS`,
  etc. — `TransfiOrchestrationService#toStatus`).
- En-tête et encodage de la signature webhook : `X-Transfi-Signature`, hexadécimal
  (`WebhookSignatureVerifier`) — pourrait être en base64, un autre nom d'en-tête,
  ou un préfixe type `sha256=...` comme chez Stripe/GitHub.
- Forme du payload webhook : champs `eventId`/`direction`/`orderId`/`status`
  (`TransfiWebhookController`).
- Nom du champ de frais prestataire dans le webhook de payout : `providerFee` ou
  `fee` (`TransfiOrchestrationService#recordProviderFeeIfPresent`).

**Non fait dans cette phase** (explicitement hors scope) :
- Relais XOF→USDT→CNY si le CNY direct n'est pas disponible (nécessiterait
  d'étendre `Currency`).
- KYB/KYC TransFi (à traiter côté configuration du compte marchand TransFi,
  hors de ce backend).
- Gestion du Locked Balance / prefinancing TransFi.
- Déclenchement automatique du payin à la création d'un ordre (Phase 2, voir
  plus bas).
- UI frontend/mobile (redirection vers `payUrl`, etc.) (Phase 2).

## Aller en production

1. Obtenir les identifiants sandbox TransFi et la documentation officielle
   (ou collection Postman).
2. Comparer ligne à ligne chaque point "PROVISOIRE" ci-dessus contre cette
   documentation ; corriger `TransFiHttpClient`, `WebhookSignatureVerifier`,
   `TransfiWebhookController` et `TransfiOrchestrationService#toStatus` en
   conséquence.
3. Renseigner `TRANSFI_BASE_URL`/`TRANSFI_CLIENT_ID`/`TRANSFI_CLIENT_SECRET`/
   `TRANSFI_WEBHOOK_SECRET` en environnement (sandbox), laisser
   `TRANSFI_ENABLED=false`.
4. Tester en sandbox via `POST /api/admin/transfi/orders/{orderId}/payin` sur un
   ordre de test, vérifier la réception et le traitement du webhook de bout en
   bout (payin réussi → payout créé → webhook payout réussi → ordre `COMPLETED`
   → ligne `SERVICE_FEE` + `PROVIDER_FEE` dans le ledger).
5. Une fois validé en sandbox : passer `TRANSFI_ENABLED=true` en production.
   Le flux reste manuel par défaut tant qu'aucun administrateur n'appelle
   explicitement `POST /api/admin/transfi/orders/{orderId}/payin` — aucune
   bascule automatique.
6. Phase 2 (déclenchement automatique du payin à la création d'un ordre, UI
   frontend/mobile de redirection) : à faire seulement une fois le contrat API
   réel validé en sandbox, jamais avant.

## Échec de payout — procédure de récupération manuelle

Si le payout TransFi échoue (webhook `status=failed` ou équivalent) après un
payin déjà réussi :

1. L'ordre reste `PROCESSING` (jamais annulé automatiquement, voir plus haut).
   Il est visible dans `GET /api/admin/transfi/orders` (statut du `TransfiOrder`
   de direction `PAYOUT`).
2. Vérifier côté TransFi (dashboard ou `GET /v3/orders/{id}`) la cause réelle de
   l'échec.
3. Deux options :
   - **Réessayer via TransFi** : si l'échec est transitoire (ex. compte
     bénéficiaire temporairement indisponible), relancer un payout côté TransFi
     directement (hors de ce backend pour l'instant) puis mettre à jour
     manuellement l'ordre une fois le décaissement confirmé.
   - **Basculer sur le flux manuel** : exécuter le règlement CNY manuellement
     comme pour tout ordre non-TransFi (`POST /api/admin/settlements/{id}/execute`
     après création via `POST /api/admin/orders/{orderId}/settlement` — attention
     à ne **jamais** décaisser deux fois : vérifier d'abord qu'aucun décaissement
     TransFi n'a réellement eu lieu côté TransFi malgré le statut `failed`).
4. Dans tous les cas, ne jamais reconsommer la réservation de trésorerie CNY une
   seconde fois pour le même ordre (`TreasuryService.consume` n'est appelé
   qu'une fois, que ce soit via le flux manuel ou via
   `TransfiOrchestrationService#handlePayoutResult`).
