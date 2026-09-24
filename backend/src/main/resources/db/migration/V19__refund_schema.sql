-- =====================================================================
-- V19 — Schema Refund (remboursement XOF client)
--
-- Un Refund represente le fait que l'entreprise a rendu au client l'argent
-- qu'il avait paye pour un Order — independant du Settlement (le
-- decaissement CNY en Chine). `payment_id` est UNIQUE : le modele actuel
-- ne connait ni paiement partiel ni sur-paiement au-dela de la tolerance,
-- donc pas de remboursement partiel a modeliser — un seul remboursement,
-- total, par paiement.
--
-- Ajoute egalement le type de mouvement REFUND au ledger de tresorerie,
-- distinct de WITHDRAWAL (decaissement CNY vers la Chine) : les deux
-- racontent des evenements economiques differents, jamais fusionnes.
-- =====================================================================

CREATE TABLE refunds (
    id                     UUID           NOT NULL DEFAULT gen_random_uuid(),
    order_id               UUID           NOT NULL,
    payment_id             UUID           NOT NULL,
    amount_xof             NUMERIC(19, 2) NOT NULL,
    status                 VARCHAR(16)    NOT NULL,
    reason                 VARCHAR(500)   NOT NULL,
    rejection_reason       VARCHAR(500),
    transaction_reference  VARCHAR(100),
    created_by             UUID           NOT NULL,
    processed_by           UUID,
    created_at             TIMESTAMPTZ    NOT NULL,
    updated_at             TIMESTAMPTZ    NOT NULL,
    processed_at           TIMESTAMPTZ,
    version                BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT pk_refunds PRIMARY KEY (id),
    CONSTRAINT uq_refunds_payment UNIQUE (payment_id),
    CONSTRAINT ck_refunds_status CHECK (status IN ('PENDING', 'PROCESSED', 'REJECTED')),
    CONSTRAINT ck_refunds_amount CHECK (amount_xof > 0),
    -- Une reference de transaction est obligatoire des lors que le remboursement est traite,
    -- jamais avant — meme principe que ck_settlements_reference_on_execution (V11).
    CONSTRAINT ck_refunds_reference_on_processed
        CHECK ((status = 'PROCESSED') = (transaction_reference IS NOT NULL AND processed_at IS NOT NULL)),
    -- Un motif de rejet est obligatoire des lors que le remboursement est rejete, jamais avant.
    CONSTRAINT ck_refunds_rejection_reason_on_rejected
        CHECK ((status = 'REJECTED') = (rejection_reason IS NOT NULL)),
    CONSTRAINT fk_refunds_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_refunds_payment FOREIGN KEY (payment_id) REFERENCES payments (id),
    CONSTRAINT fk_refunds_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT fk_refunds_processed_by FOREIGN KEY (processed_by) REFERENCES users (id)
);

CREATE INDEX idx_refunds_order ON refunds (order_id);
CREATE INDEX idx_refunds_status_created ON refunds (status, created_at);

ALTER TABLE treasury_transactions DROP CONSTRAINT ck_treasury_tx_type;
ALTER TABLE treasury_transactions ADD CONSTRAINT ck_treasury_tx_type
    CHECK (type IN ('DEPOSIT', 'WITHDRAWAL', 'RESERVATION', 'RELEASE', 'ADJUSTMENT', 'REFUND'));
