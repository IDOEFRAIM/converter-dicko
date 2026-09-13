-- Messagerie SAV (retour client) : un seul fil de discussion continu par utilisateur, ou
-- l'utilisateur peut faire une reclamation et un administrateur repond -- tous les fils sont
-- visibles cote admin.

CREATE TABLE support_threads (
    id                 UUID          NOT NULL DEFAULT gen_random_uuid(),
    user_id            UUID          NOT NULL,
    user_last_read_at  TIMESTAMPTZ,
    admin_last_read_at TIMESTAMPTZ,
    created_at         TIMESTAMPTZ   NOT NULL,
    updated_at         TIMESTAMPTZ   NOT NULL,
    CONSTRAINT pk_support_threads PRIMARY KEY (id),
    CONSTRAINT uq_support_threads_user UNIQUE (user_id),
    CONSTRAINT fk_support_threads_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE support_messages (
    id          UUID          NOT NULL DEFAULT gen_random_uuid(),
    thread_id   UUID          NOT NULL,
    sender_role VARCHAR(16)   NOT NULL,
    sender_id   UUID          NOT NULL,
    body        VARCHAR(2000) NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL,
    CONSTRAINT pk_support_messages PRIMARY KEY (id),
    CONSTRAINT fk_support_messages_thread FOREIGN KEY (thread_id) REFERENCES support_threads (id),
    CONSTRAINT fk_support_messages_sender FOREIGN KEY (sender_id) REFERENCES users (id),
    CONSTRAINT ck_support_messages_sender_role CHECK (sender_role IN ('USER', 'ADMIN')),
    CONSTRAINT ck_support_messages_body_not_blank CHECK (length(btrim(body)) > 0)
);

CREATE INDEX idx_support_messages_thread_created ON support_messages (thread_id, created_at);
CREATE INDEX idx_support_threads_updated ON support_threads (updated_at DESC);

ALTER TABLE notifications DROP CONSTRAINT ck_notifications_type;
ALTER TABLE notifications ADD CONSTRAINT ck_notifications_type CHECK (type IN (
    'QUOTE_CREATED', 'PAYMENT_SUBMITTED', 'PAYMENT_CONFIRMED',
    'EXCHANGE_STARTED', 'EXCHANGE_PROGRESS', 'EXCHANGE_COMPLETED',
    'PREFERRED_RATE_REACHED', 'PREFERRED_RATE_EXPIRED', 'EXCHANGE_CANCELLED',
    'ORDER_EXPIRED', 'RATE_ALERT_TRIGGERED', 'POOL_SUCCEEDED', 'POOL_EXPIRED',
    'BADGE_UNLOCKED', 'SUPPORT_REPLY'));
