-- =====================================================================
-- V14 — Schema Notifications internes (Phase Wallet + Taux preferentiel)
--
-- MVP : canal IN_APP uniquement (consultation dans l'application). La
-- colonne `channel` prepare le modele pour de futurs canaux (SMS,
-- WhatsApp, email...) sans qu'aucun ne soit integre dans cette phase.
-- =====================================================================

CREATE TABLE notifications (
    id         UUID         NOT NULL DEFAULT gen_random_uuid(),
    user_id    UUID         NOT NULL,
    type       VARCHAR(32)  NOT NULL,
    channel    VARCHAR(16)  NOT NULL DEFAULT 'IN_APP',
    title      VARCHAR(200) NOT NULL,
    message    VARCHAR(1000) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    read_at    TIMESTAMPTZ,
    CONSTRAINT pk_notifications PRIMARY KEY (id),
    CONSTRAINT ck_notifications_channel CHECK (channel IN ('IN_APP')),
    CONSTRAINT ck_notifications_type CHECK (type IN (
        'QUOTE_CREATED', 'PAYMENT_SUBMITTED', 'PAYMENT_CONFIRMED',
        'EXCHANGE_STARTED', 'EXCHANGE_PROGRESS', 'EXCHANGE_COMPLETED',
        'PREFERRED_RATE_REACHED', 'PREFERRED_RATE_EXPIRED', 'EXCHANGE_CANCELLED')),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_notifications_user_created ON notifications (user_id, created_at DESC);
CREATE INDEX idx_notifications_user_unread ON notifications (user_id) WHERE read_at IS NULL;
