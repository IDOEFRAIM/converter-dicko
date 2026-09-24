-- Abonnements Web Push (PWA, mission "blocages Apple/Meta" oct. 2026) : un navigateur genere un
-- `endpoint` unique par abonnement (chez le fournisseur de push du navigateur -- Google/Mozilla/
-- Microsoft...), jamais reutilise par un autre. Un meme utilisateur peut avoir plusieurs
-- abonnements actifs (plusieurs appareils/navigateurs) -- pas de contrainte d'unicite sur
-- user_id, uniquement sur endpoint.

CREATE TABLE push_subscriptions (
    id          UUID          NOT NULL DEFAULT gen_random_uuid(),
    user_id     UUID          NOT NULL,
    endpoint    VARCHAR(500)  NOT NULL,
    p256dh_key  VARCHAR(255)  NOT NULL,
    auth_key    VARCHAR(255)  NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL,
    CONSTRAINT pk_push_subscriptions PRIMARY KEY (id),
    CONSTRAINT uq_push_subscriptions_endpoint UNIQUE (endpoint),
    CONSTRAINT fk_push_subscriptions_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_push_subscriptions_user ON push_subscriptions (user_id);
