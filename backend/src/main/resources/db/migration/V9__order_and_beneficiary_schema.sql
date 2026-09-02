-- =====================================================================
-- V9 — Remplacement du schema Order/Beneficiary (Phase 4)
--
-- Les tables `orders`, `beneficiaries`, `order_status_history` et
-- `exchange_rates` ont ete migrees en V1 mais n'ont jamais ete
-- consommees par aucun code applicatif (le module `order` n'existait
-- pas). La revision architecturale (docs/ARCHITECTURE.md, Partie I,
-- section K.3/K.5) a decide que l'ordre reference desormais un `Quote`
-- (deja construit en Phase 3) plutot qu'un taux brut : cette migration
-- remplace donc ces tables par leur forme definitive, sans aucun cout
-- de regression puisqu'aucune ligne de code ne les touchait.
--
-- `payments`/`payment_proofs` (Phase 5) et `treasury_transactions`
-- (deja fonctionnelle) referencent `orders.id` par cle etrangere : la
-- contrainte est retiree avant la suppression, puis recreee a la fin
-- une fois la nouvelle table `orders` en place. `payments`/
-- `payment_proofs` sont supprimees ici et recreees dans la migration
-- V10, sous leur forme definitive (Phase 5).
-- =====================================================================

-- ---------------------------------------------------------------------
-- Depose les contraintes et tables dependantes, dans l'ordre inverse
-- de leurs dependances.
-- ---------------------------------------------------------------------
ALTER TABLE treasury_transactions DROP CONSTRAINT fk_treasury_tx_order;

DROP TABLE IF EXISTS payment_proofs;
DROP TABLE IF EXISTS payments;
DROP TABLE IF EXISTS order_status_history;
DROP TABLE IF EXISTS beneficiaries;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS exchange_rates;

-- ---------------------------------------------------------------------
-- orders — reference un Quote, jamais un taux brut
--
-- `quote_id` porte une contrainte UNIQUE : un Quote ne peut jamais
-- produire plus d'un Order (garantie SQL, en plus de la verification
-- applicative faite avant l'insertion).
--
-- Les colonnes financieres sont des copies immuables du Quote au
-- moment de la creation (lecture rapide sans jointure systematique) ;
-- `quote_id` reste la reference tracable vers le detail complet
-- (market_rate, margin_percentage, rate_source_id...).
-- ---------------------------------------------------------------------
CREATE TABLE orders (
    id                  UUID           NOT NULL DEFAULT gen_random_uuid(),
    reference           VARCHAR(24)    NOT NULL,
    user_id             UUID           NOT NULL,
    quote_id            UUID           NOT NULL,
    status              VARCHAR(24)    NOT NULL,
    amount_xof          NUMERIC(19, 2) NOT NULL,
    amount_cny          NUMERIC(19, 2) NOT NULL,
    customer_rate       NUMERIC(18, 6) NOT NULL,
    fee_xof             NUMERIC(19, 2) NOT NULL,
    net_amount_xof      NUMERIC(19, 2) NOT NULL,
    note                VARCHAR(500),
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
    CONSTRAINT uq_orders_quote UNIQUE (quote_id),
    CONSTRAINT ck_orders_status CHECK (status IN (
        'AWAITING_PAYMENT', 'PAYMENT_SUBMITTED', 'PAYMENT_VERIFIED', 'PROCESSING',
        'COMPLETED', 'CANCELLED', 'REJECTED', 'EXPIRED')),
    CONSTRAINT ck_orders_amount_xof CHECK (amount_xof > 0),
    CONSTRAINT ck_orders_amount_cny CHECK (amount_cny > 0),
    CONSTRAINT ck_orders_customer_rate CHECK (customer_rate > 0),
    CONSTRAINT ck_orders_fee_xof CHECK (fee_xof >= 0),
    CONSTRAINT ck_orders_net_amount CHECK (net_amount_xof > 0),
    CONSTRAINT ck_orders_net_coherent CHECK (net_amount_xof = amount_xof - fee_xof),
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_orders_quote FOREIGN KEY (quote_id) REFERENCES quotes (id)
);

CREATE INDEX idx_orders_user_created ON orders (user_id, created_at DESC);
CREATE INDEX idx_orders_status_created ON orders (status, created_at DESC);

-- ---------------------------------------------------------------------
-- beneficiaries — snapshot immuable, identique dans sa forme a la
-- conception initiale (Phase 1) : aucun changement necessaire ici.
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
    CONSTRAINT ck_beneficiaries_bank_required
        CHECK (type <> 'CHINESE_BANK_ACCOUNT' OR bank_name IS NOT NULL),
    CONSTRAINT fk_beneficiaries_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE
);

CREATE INDEX idx_beneficiaries_identifier ON beneficiaries (identifier);

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

CREATE INDEX idx_osh_order_created ON order_status_history (order_id, created_at);

-- ---------------------------------------------------------------------
-- Restaure la reference treasury_transactions -> orders, vers la
-- nouvelle table.
-- ---------------------------------------------------------------------
ALTER TABLE treasury_transactions
    ADD CONSTRAINT fk_treasury_tx_order FOREIGN KEY (order_id) REFERENCES orders (id);
