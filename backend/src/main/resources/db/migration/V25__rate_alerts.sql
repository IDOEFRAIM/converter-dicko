-- =====================================================================
-- V25 — Alertes de taux (Phase 6, evolution Burkina Faso <-> Chine)
--
-- Intention utilisateur persistante, jamais une transaction financiere : "previens-moi quand
-- le taux client public XOF/CNY atteint mon objectif". Distincte de `preferred_rate_requests`
-- (qui immobilise un montant sur le Wallet et declenche un echange reel) — `rate_alerts` ne
-- porte aucun montant et n'a aucun impact Wallet/Treasury.
--
-- Contrairement a `public_rate_snapshots` (V24, append-only), cette table a un cycle de vie :
-- ACTIVE -> TRIGGERED | CANCELLED | EXPIRED, jamais l'inverse. Le taux compare a `target_rate`
-- est toujours lu depuis `public_rate_snapshots` au moment de l'evaluation (voir
-- RateAlertService) — jamais depuis `daily_cost_rate_configurations`/`rate_sources`.
-- =====================================================================

CREATE TABLE rate_alerts (
    id             UUID          NOT NULL DEFAULT gen_random_uuid(),
    user_id        UUID          NOT NULL,
    currency_pair  VARCHAR(10)   NOT NULL,
    direction      VARCHAR(16)   NOT NULL,
    target_rate    NUMERIC(18,6) NOT NULL,
    comparison     VARCHAR(24)   NOT NULL,
    status         VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE',
    created_at     TIMESTAMPTZ   NOT NULL,
    expires_at     TIMESTAMPTZ,
    triggered_at   TIMESTAMPTZ,
    cancelled_at   TIMESTAMPTZ,
    expired_at     TIMESTAMPTZ,
    version        BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_rate_alerts PRIMARY KEY (id),
    CONSTRAINT ck_rate_alerts_target_rate CHECK (target_rate > 0),
    CONSTRAINT ck_rate_alerts_direction CHECK (direction IN ('XOF_TO_CNY')),
    CONSTRAINT ck_rate_alerts_comparison CHECK (comparison IN ('LESS_THAN_OR_EQUAL', 'GREATER_THAN_OR_EQUAL')),
    CONSTRAINT ck_rate_alerts_status CHECK (status IN ('ACTIVE', 'TRIGGERED', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT fk_rate_alerts_user FOREIGN KEY (user_id) REFERENCES users (id)
);

-- Requetes du scheduler (candidats ACTIVE) et de la liste utilisateur filtree par statut.
CREATE INDEX idx_rate_alerts_user_status ON rate_alerts (user_id, status);
CREATE INDEX idx_rate_alerts_status_pair ON rate_alerts (status, currency_pair);

-- ---------------------------------------------------------------------
-- notifications : nouveau type RATE_ALERT_TRIGGERED (meme patron d'extension additive que
-- V16 pour ORDER_EXPIRED -- DROP puis ADD, la seule maniere d'etendre un CHECK PostgreSQL).
-- ---------------------------------------------------------------------
ALTER TABLE notifications DROP CONSTRAINT ck_notifications_type;
ALTER TABLE notifications ADD CONSTRAINT ck_notifications_type CHECK (type IN (
    'QUOTE_CREATED', 'PAYMENT_SUBMITTED', 'PAYMENT_CONFIRMED',
    'EXCHANGE_STARTED', 'EXCHANGE_PROGRESS', 'EXCHANGE_COMPLETED',
    'PREFERRED_RATE_REACHED', 'PREFERRED_RATE_EXPIRED', 'EXCHANGE_CANCELLED',
    'ORDER_EXPIRED', 'RATE_ALERT_TRIGGERED'));
