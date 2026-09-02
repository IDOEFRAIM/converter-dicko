-- =====================================================================
-- V1 — Schema initial
--
-- Conventions :
--   * PK          : UUID (gen_random_uuid(), natif PostgreSQL 13+)
--   * Horodatage  : TIMESTAMPTZ, stocke en UTC
--   * Montants    : NUMERIC — jamais de type flottant
--   * Enums       : VARCHAR + CHECK (jamais de type ENUM PostgreSQL :
--                   ajouter une valeur a un type ENUM verrouille la table)
--
-- Les contraintes portees ici sont la PREMIERE ligne de defense :
-- meme si un bug applicatif passe, la base refuse l'ecriture.
-- =====================================================================

-- ---------------------------------------------------------------------
-- roles
-- ---------------------------------------------------------------------
CREATE TABLE roles (
    id    SMALLINT     NOT NULL,
    code  VARCHAR(20)  NOT NULL,
    label VARCHAR(60)  NOT NULL,
    CONSTRAINT pk_roles PRIMARY KEY (id),
    CONSTRAINT uq_roles_code UNIQUE (code),
    CONSTRAINT ck_roles_code CHECK (code IN ('USER', 'ADMIN'))
);

-- ---------------------------------------------------------------------
-- users
-- ---------------------------------------------------------------------
CREATE TABLE users (
    id             UUID         NOT NULL DEFAULT gen_random_uuid(),
    phone          VARCHAR(20)  NOT NULL,
    password_hash  VARCHAR(100) NOT NULL,
    first_name     VARCHAR(80)  NOT NULL,
    last_name      VARCHAR(80)  NOT NULL,
    email          VARCHAR(160),
    status         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    blocked_at     TIMESTAMPTZ,
    blocked_reason VARCHAR(500),
    blocked_by     UUID,
    last_login_at  TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    version        BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_phone UNIQUE (phone),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'BLOCKED')),
    -- Format E.164 : '+' suivi de 8 a 15 chiffres, le premier non nul.
    CONSTRAINT ck_users_phone_format CHECK (phone ~ '^\+[1-9][0-9]{7,14}$'),
    CONSTRAINT fk_users_blocked_by FOREIGN KEY (blocked_by) REFERENCES users (id)
);

-- ---------------------------------------------------------------------
-- user_roles
-- ---------------------------------------------------------------------
CREATE TABLE user_roles (
    user_id UUID     NOT NULL,
    role_id SMALLINT NOT NULL,
    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id)
);

-- ---------------------------------------------------------------------
-- system_settings — parametres metier administrables
-- ---------------------------------------------------------------------
CREATE TABLE system_settings (
    setting_key VARCHAR(64)  NOT NULL,
    value       VARCHAR(255) NOT NULL,
    value_type  VARCHAR(16)  NOT NULL,
    description VARCHAR(255),
    is_public   BOOLEAN      NOT NULL DEFAULT FALSE,
    updated_by  UUID,
    updated_at  TIMESTAMPTZ  NOT NULL,
    version     BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_system_settings PRIMARY KEY (setting_key),
    CONSTRAINT ck_system_settings_type CHECK (value_type IN ('STRING', 'INTEGER', 'DECIMAL', 'BOOLEAN')),
    CONSTRAINT fk_system_settings_updated_by FOREIGN KEY (updated_by) REFERENCES users (id)
);

-- ---------------------------------------------------------------------
-- exchange_rates — table append-only, historique integral
-- ---------------------------------------------------------------------
CREATE TABLE exchange_rates (
    id             UUID           NOT NULL DEFAULT gen_random_uuid(),
    cfa_per_cny    NUMERIC(18, 6) NOT NULL,
    fee_percentage NUMERIC(6, 4)  NOT NULL DEFAULT 0,
    fixed_fee_cfa  NUMERIC(19, 2) NOT NULL DEFAULT 0,
    effective_from TIMESTAMPTZ    NOT NULL,
    effective_to   TIMESTAMPTZ,
    note           VARCHAR(500),
    created_by     UUID           NOT NULL,
    created_at     TIMESTAMPTZ    NOT NULL,
    CONSTRAINT pk_exchange_rates PRIMARY KEY (id),
    CONSTRAINT ck_exchange_rates_rate_positive CHECK (cfa_per_cny > 0),
    CONSTRAINT ck_exchange_rates_fee_pct CHECK (fee_percentage >= 0 AND fee_percentage < 100),
    CONSTRAINT ck_exchange_rates_fixed_fee CHECK (fixed_fee_cfa >= 0),
    CONSTRAINT ck_exchange_rates_period CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT fk_exchange_rates_created_by FOREIGN KEY (created_by) REFERENCES users (id)
);

-- ---------------------------------------------------------------------
-- orders
-- ---------------------------------------------------------------------
CREATE TABLE orders (
    id                  UUID           NOT NULL DEFAULT gen_random_uuid(),
    reference           VARCHAR(24)    NOT NULL,
    user_id             UUID           NOT NULL,
    status              VARCHAR(24)    NOT NULL,
    amount_cfa          NUMERIC(19, 2) NOT NULL,
    fee_cfa             NUMERIC(19, 2) NOT NULL,
    net_amount_cfa      NUMERIC(19, 2) NOT NULL,
    amount_cny          NUMERIC(19, 2) NOT NULL,
    exchange_rate       NUMERIC(18, 6) NOT NULL,
    exchange_rate_id    UUID           NOT NULL,
    fee_percentage      NUMERIC(6, 4)  NOT NULL,
    fixed_fee_cfa       NUMERIC(19, 2) NOT NULL,
    rate_locked_at      TIMESTAMPTZ    NOT NULL,
    rate_expires_at     TIMESTAMPTZ    NOT NULL,
    note                VARCHAR(500),
    admin_note          VARCHAR(1000),
    payout_reference    VARCHAR(100),
    cancellation_reason VARCHAR(500),
    rejection_reason    VARCHAR(500),
    treasury_reserved   BOOLEAN        NOT NULL DEFAULT FALSE,
    completed_at        TIMESTAMPTZ,
    cancelled_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ    NOT NULL,
    updated_at          TIMESTAMPTZ    NOT NULL,
    version             BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT pk_orders PRIMARY KEY (id),
    CONSTRAINT uq_orders_reference UNIQUE (reference),
    CONSTRAINT ck_orders_status CHECK (status IN (
        'AWAITING_PAYMENT', 'PAYMENT_SUBMITTED', 'PAYMENT_VERIFIED', 'PROCESSING',
        'COMPLETED', 'CANCELLED', 'REJECTED', 'EXPIRED')),
    CONSTRAINT ck_orders_amount_cfa CHECK (amount_cfa > 0),
    CONSTRAINT ck_orders_fee_cfa CHECK (fee_cfa >= 0),
    CONSTRAINT ck_orders_net_cfa CHECK (net_amount_cfa > 0),
    CONSTRAINT ck_orders_amount_cny CHECK (amount_cny > 0),
    CONSTRAINT ck_orders_rate CHECK (exchange_rate > 0),
    CONSTRAINT ck_orders_net_coherent CHECK (net_amount_cfa = amount_cfa - fee_cfa),
    CONSTRAINT ck_orders_rate_window CHECK (rate_expires_at > rate_locked_at),
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_orders_rate FOREIGN KEY (exchange_rate_id) REFERENCES exchange_rates (id)
);

-- ---------------------------------------------------------------------
-- beneficiaries — snapshot immuable, 1-1 avec l'ordre
-- ---------------------------------------------------------------------
CREATE TABLE beneficiaries (
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    order_id    UUID         NOT NULL,
    type        VARCHAR(24)  NOT NULL,
    full_name   VARCHAR(120) NOT NULL,
    identifier  VARCHAR(120) NOT NULL,
    bank_name   VARCHAR(120),
    bank_branch VARCHAR(120),
    created_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_beneficiaries PRIMARY KEY (id),
    CONSTRAINT uq_beneficiaries_order UNIQUE (order_id),
    CONSTRAINT ck_beneficiaries_type CHECK (type IN ('ALIPAY', 'WECHAT_PAY', 'CHINESE_BANK_ACCOUNT')),
    -- Un compte bancaire chinois exige obligatoirement le nom de la banque.
    CONSTRAINT ck_beneficiaries_bank_required
        CHECK (type <> 'CHINESE_BANK_ACCOUNT' OR bank_name IS NOT NULL),
    CONSTRAINT fk_beneficiaries_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE
);

-- ---------------------------------------------------------------------
-- payments
-- ---------------------------------------------------------------------
CREATE TABLE payments (
    id                    UUID           NOT NULL DEFAULT gen_random_uuid(),
    order_id              UUID           NOT NULL,
    method                VARCHAR(24)    NOT NULL,
    status                VARCHAR(16)    NOT NULL,
    amount_cfa            NUMERIC(19, 2) NOT NULL,
    transaction_reference VARCHAR(100)   NOT NULL,
    payer_phone           VARCHAR(20),
    submitted_at          TIMESTAMPTZ    NOT NULL,
    verified_at           TIMESTAMPTZ,
    rejected_at           TIMESTAMPTZ,
    reviewed_by           UUID,
    rejection_reason      VARCHAR(500),
    created_at            TIMESTAMPTZ    NOT NULL,
    updated_at            TIMESTAMPTZ    NOT NULL,
    version               BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT pk_payments PRIMARY KEY (id),
    CONSTRAINT ck_payments_method CHECK (method IN ('MOBILE_MONEY', 'WAVE', 'BANK_TRANSFER')),
    CONSTRAINT ck_payments_status CHECK (status IN ('SUBMITTED', 'VERIFIED', 'REJECTED')),
    CONSTRAINT ck_payments_amount CHECK (amount_cfa > 0),
    -- Un rejet exige un motif ; sans rejet, pas de motif.
    CONSTRAINT ck_payments_rejection_reason
        CHECK ((status = 'REJECTED') = (rejection_reason IS NOT NULL)),
    CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_payments_reviewed_by FOREIGN KEY (reviewed_by) REFERENCES users (id)
);

-- ---------------------------------------------------------------------
-- payment_proofs — METADONNEES uniquement, aucun binaire en base
-- ---------------------------------------------------------------------
CREATE TABLE payment_proofs (
    id               UUID         NOT NULL DEFAULT gen_random_uuid(),
    payment_id       UUID         NOT NULL,
    file_name        VARCHAR(255) NOT NULL,
    content_type     VARCHAR(100) NOT NULL,
    storage_key      VARCHAR(500) NOT NULL,
    storage_provider VARCHAR(16)  NOT NULL DEFAULT 'LOCAL',
    size_bytes       BIGINT       NOT NULL,
    checksum_sha256  CHAR(64)     NOT NULL,
    uploaded_by      UUID         NOT NULL,
    uploaded_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_payment_proofs PRIMARY KEY (id),
    CONSTRAINT uq_payment_proofs_storage_key UNIQUE (storage_key),
    CONSTRAINT ck_payment_proofs_size CHECK (size_bytes > 0),
    CONSTRAINT ck_payment_proofs_provider CHECK (storage_provider IN ('LOCAL', 'S3')),
    CONSTRAINT ck_payment_proofs_content_type CHECK (content_type IN (
        'image/jpeg', 'image/png', 'image/webp', 'application/pdf')),
    CONSTRAINT fk_payment_proofs_payment FOREIGN KEY (payment_id) REFERENCES payments (id) ON DELETE CASCADE,
    CONSTRAINT fk_payment_proofs_uploaded_by FOREIGN KEY (uploaded_by) REFERENCES users (id)
);

-- ---------------------------------------------------------------------
-- treasury_accounts
-- ---------------------------------------------------------------------
CREATE TABLE treasury_accounts (
    id               UUID           NOT NULL DEFAULT gen_random_uuid(),
    currency         VARCHAR(3)     NOT NULL,
    balance          NUMERIC(21, 2) NOT NULL DEFAULT 0,
    reserved_balance NUMERIC(21, 2) NOT NULL DEFAULT 0,
    low_threshold    NUMERIC(21, 2) NOT NULL DEFAULT 0,
    updated_at       TIMESTAMPTZ    NOT NULL,
    version          BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT pk_treasury_accounts PRIMARY KEY (id),
    CONSTRAINT uq_treasury_accounts_currency UNIQUE (currency),
    CONSTRAINT ck_treasury_accounts_currency CHECK (currency IN ('XOF', 'CNY')),
    CONSTRAINT ck_treasury_accounts_reserved_positive CHECK (reserved_balance >= 0),
    CONSTRAINT ck_treasury_accounts_balance_positive CHECK (balance >= 0),
    -- INVARIANT CENTRAL : le solde disponible ne peut jamais devenir negatif.
    -- Cette contrainte rend une sur-reservation physiquement impossible,
    -- independamment du code applicatif.
    CONSTRAINT ck_treasury_accounts_reserved_le_balance CHECK (reserved_balance <= balance)
);

-- ---------------------------------------------------------------------
-- treasury_transactions — ledger append-only
-- ---------------------------------------------------------------------
CREATE TABLE treasury_transactions (
    id             UUID           NOT NULL DEFAULT gen_random_uuid(),
    account_id     UUID           NOT NULL,
    type           VARCHAR(16)    NOT NULL,
    amount         NUMERIC(21, 2) NOT NULL,
    balance_after  NUMERIC(21, 2) NOT NULL,
    reserved_after NUMERIC(21, 2) NOT NULL,
    order_id       UUID,
    performed_by   UUID,
    reason         VARCHAR(500),
    created_at     TIMESTAMPTZ    NOT NULL,
    CONSTRAINT pk_treasury_transactions PRIMARY KEY (id),
    CONSTRAINT ck_treasury_tx_type CHECK (type IN (
        'DEPOSIT', 'WITHDRAWAL', 'RESERVATION', 'RELEASE', 'ADJUSTMENT')),
    -- Le montant est toujours positif : le sens est porte par `type`.
    CONSTRAINT ck_treasury_tx_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_treasury_tx_balance_after CHECK (balance_after >= 0),
    CONSTRAINT ck_treasury_tx_reserved_after CHECK (reserved_after >= 0),
    CONSTRAINT fk_treasury_tx_account FOREIGN KEY (account_id) REFERENCES treasury_accounts (id),
    CONSTRAINT fk_treasury_tx_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_treasury_tx_performed_by FOREIGN KEY (performed_by) REFERENCES users (id)
);

-- ---------------------------------------------------------------------
-- order_status_history
-- ---------------------------------------------------------------------
CREATE TABLE order_status_history (
    id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    order_id    UUID        NOT NULL,
    from_status VARCHAR(24),
    to_status   VARCHAR(24) NOT NULL,
    changed_by  UUID,
    reason      VARCHAR(500),
    created_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_order_status_history PRIMARY KEY (id),
    CONSTRAINT ck_osh_to_status CHECK (to_status IN (
        'AWAITING_PAYMENT', 'PAYMENT_SUBMITTED', 'PAYMENT_VERIFIED', 'PROCESSING',
        'COMPLETED', 'CANCELLED', 'REJECTED', 'EXPIRED')),
    CONSTRAINT fk_osh_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_osh_changed_by FOREIGN KEY (changed_by) REFERENCES users (id)
);

-- ---------------------------------------------------------------------
-- audit_logs
-- ---------------------------------------------------------------------
CREATE TABLE audit_logs (
    id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    actor_id    UUID,
    actor_phone VARCHAR(20),
    action      VARCHAR(48) NOT NULL,
    entity_type VARCHAR(48),
    entity_id   VARCHAR(64),
    metadata    JSONB,
    ip_address  VARCHAR(45),
    user_agent  VARCHAR(255),
    created_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_audit_logs PRIMARY KEY (id),
    -- Pas de FK stricte vers users : l'audit doit survivre a toute
    -- disparition de l'acteur. `actor_phone` conserve l'identite.
    CONSTRAINT ck_audit_logs_action_not_blank CHECK (length(btrim(action)) > 0)
);

-- ---------------------------------------------------------------------
-- idempotency_keys
-- ---------------------------------------------------------------------
CREATE TABLE idempotency_keys (
    id              UUID         NOT NULL DEFAULT gen_random_uuid(),
    idem_key        VARCHAR(80)  NOT NULL,
    user_id         UUID         NOT NULL,
    endpoint        VARCHAR(120) NOT NULL,
    request_hash    CHAR(64)     NOT NULL,
    response_status INTEGER,
    response_body   JSONB,
    created_at      TIMESTAMPTZ  NOT NULL,
    expires_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_idempotency_keys PRIMARY KEY (id),
    CONSTRAINT uq_idempotency_keys UNIQUE (user_id, endpoint, idem_key),
    CONSTRAINT ck_idempotency_expiry CHECK (expires_at > created_at),
    CONSTRAINT fk_idempotency_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
