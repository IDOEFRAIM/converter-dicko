-- =====================================================================
-- V11 — Schema Settlement (Phase 6)
--
-- Un Settlement represente l'execution du paiement CNY au
-- beneficiaire — distinct d'un Payment (qui represente la reception
-- des XOF du client). MVP entierement manuel : la societe execute
-- elle-meme le paiement, aucune integration Binance/OKX/P2P/API
-- chinoise. Voir docs/ARCHITECTURE.md, Partie I, section H.3.
--
-- `order_id` est UNIQUE : un ordre ne peut avoir qu'un seul reglement,
-- garantie SQL en plus de la verification applicative.
--
-- Le beneficiaire est duplique ici (snapshot) plutot que relu par
-- jointure vers `beneficiaries` : un enregistrement de reglement doit
-- rester lisible et auditable seul, sans dependre d'un autre module.
-- =====================================================================

CREATE TABLE settlements (
    id                        UUID           NOT NULL DEFAULT gen_random_uuid(),
    order_id                  UUID           NOT NULL,
    status                    VARCHAR(16)    NOT NULL,
    amount_cny                NUMERIC(19, 2) NOT NULL,
    method                    VARCHAR(24)    NOT NULL,
    beneficiary_full_name     VARCHAR(120)   NOT NULL,
    beneficiary_identifier    VARCHAR(120)   NOT NULL,
    beneficiary_bank_name     VARCHAR(120),
    beneficiary_bank_branch   VARCHAR(120),
    settlement_reference      VARCHAR(100),
    notes                     VARCHAR(1000),
    executed_by               UUID,
    executed_at               TIMESTAMPTZ,
    created_at                TIMESTAMPTZ    NOT NULL,
    updated_at                TIMESTAMPTZ    NOT NULL,
    version                   BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT pk_settlements PRIMARY KEY (id),
    CONSTRAINT uq_settlements_order UNIQUE (order_id),
    CONSTRAINT ck_settlements_status CHECK (status IN ('PENDING', 'EXECUTED')),
    CONSTRAINT ck_settlements_amount CHECK (amount_cny > 0),
    CONSTRAINT ck_settlements_method CHECK (method IN ('ALIPAY', 'WECHAT_PAY', 'CHINESE_BANK_ACCOUNT')),
    -- Une reference de reglement est obligatoire des lors que le
    -- reglement est execute, jamais avant.
    CONSTRAINT ck_settlements_reference_on_execution
        CHECK ((status = 'EXECUTED') = (settlement_reference IS NOT NULL AND executed_at IS NOT NULL)),
    CONSTRAINT fk_settlements_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_settlements_executed_by FOREIGN KEY (executed_by) REFERENCES users (id)
);

CREATE INDEX idx_settlements_status_created ON settlements (status, created_at);

CREATE TABLE settlement_proofs (
    id               UUID         NOT NULL DEFAULT gen_random_uuid(),
    settlement_id    UUID         NOT NULL,
    file_name        VARCHAR(255) NOT NULL,
    content_type     VARCHAR(100) NOT NULL,
    storage_key      VARCHAR(500) NOT NULL,
    storage_provider VARCHAR(16)  NOT NULL DEFAULT 'LOCAL',
    size_bytes       BIGINT       NOT NULL,
    checksum_sha256  VARCHAR(64)  NOT NULL,
    uploaded_by      UUID         NOT NULL,
    uploaded_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_settlement_proofs PRIMARY KEY (id),
    CONSTRAINT uq_settlement_proofs_storage_key UNIQUE (storage_key),
    CONSTRAINT ck_settlement_proofs_size CHECK (size_bytes > 0),
    CONSTRAINT ck_settlement_proofs_provider CHECK (storage_provider IN ('LOCAL', 'S3')),
    CONSTRAINT ck_settlement_proofs_content_type CHECK (content_type IN (
        'image/jpeg', 'image/png', 'image/webp', 'application/pdf')),
    CONSTRAINT fk_settlement_proofs_settlement FOREIGN KEY (settlement_id) REFERENCES settlements (id) ON DELETE CASCADE,
    CONSTRAINT fk_settlement_proofs_uploaded_by FOREIGN KEY (uploaded_by) REFERENCES users (id)
);

CREATE INDEX idx_settlement_proofs_settlement ON settlement_proofs (settlement_id);
