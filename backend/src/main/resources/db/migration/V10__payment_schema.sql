-- =====================================================================
-- V10 — Schema Payment (Phase 5)
--
-- `payments`/`payment_proofs` avaient ete supprimees en V9 (formes
-- Phase 1 jamais consommees par du code). Cette migration les recree
-- sous leur forme definitive.
--
-- Difference de fond avec la conception Phase 1 : un `Order` rejete
-- est desormais un etat TERMINAL (decision V6, docs/ARCHITECTURE.md) —
-- il n'existe plus de retour a AWAITING_PAYMENT permettant une
-- resoumission. `payments.order_id` est donc UNIQUE : un ordre porte
-- au plus un seul paiement, jamais plusieurs tentatives successives.
-- =====================================================================

CREATE TABLE payments (
    id                    UUID           NOT NULL DEFAULT gen_random_uuid(),
    order_id              UUID           NOT NULL,
    method                VARCHAR(24)    NOT NULL,
    status                VARCHAR(16)    NOT NULL,
    expected_amount_xof   NUMERIC(19, 2) NOT NULL,
    received_amount_xof   NUMERIC(19, 2) NOT NULL,
    transaction_reference VARCHAR(100)   NOT NULL,
    payer_phone           VARCHAR(20),
    submitted_at          TIMESTAMPTZ    NOT NULL,
    confirmed_at          TIMESTAMPTZ,
    rejected_at           TIMESTAMPTZ,
    reviewed_by           UUID,
    rejection_reason      VARCHAR(500),
    created_at            TIMESTAMPTZ    NOT NULL,
    updated_at            TIMESTAMPTZ    NOT NULL,
    version               BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT pk_payments PRIMARY KEY (id),
    CONSTRAINT uq_payments_order UNIQUE (order_id),
    CONSTRAINT ck_payments_method CHECK (method IN ('MOBILE_MONEY', 'WAVE', 'BANK_TRANSFER')),
    CONSTRAINT ck_payments_status CHECK (status IN ('SUBMITTED', 'CONFIRMED', 'REJECTED')),
    CONSTRAINT ck_payments_expected_amount CHECK (expected_amount_xof > 0),
    CONSTRAINT ck_payments_received_amount CHECK (received_amount_xof > 0),
    CONSTRAINT ck_payments_rejection_reason
        CHECK ((status = 'REJECTED') = (rejection_reason IS NOT NULL)),
    CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_payments_reviewed_by FOREIGN KEY (reviewed_by) REFERENCES users (id)
);

CREATE INDEX idx_payments_pending
    ON payments (submitted_at)
    WHERE status = 'SUBMITTED';

-- INVARIANT : une reference de transaction ne sert jamais deux ordres.
CREATE UNIQUE INDEX uq_payments_txref ON payments (method, transaction_reference);

CREATE TABLE payment_proofs (
    id               UUID         NOT NULL DEFAULT gen_random_uuid(),
    payment_id       UUID         NOT NULL,
    file_name        VARCHAR(255) NOT NULL,
    content_type     VARCHAR(100) NOT NULL,
    storage_key      VARCHAR(500) NOT NULL,
    storage_provider VARCHAR(16)  NOT NULL DEFAULT 'LOCAL',
    size_bytes       BIGINT       NOT NULL,
    checksum_sha256  VARCHAR(64)  NOT NULL,
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

CREATE INDEX idx_payment_proofs_payment ON payment_proofs (payment_id);
