-- Integration TransFi BizPay, Phase 1 (voir docs/TRANSFI_INTEGRATION.md) : grand livre interne
-- (revenu de frais de service + couts reels) et suivi des ordres payin/payout TransFi. Aucune de
-- ces tables ne change le comportement existant -- le flux manuel (SettlementService) reste actif
-- par defaut ; ces tables sont peuplees des la mise en service du ledger (revenu) et seulement si
-- app.transfi.enabled=true (TransFi).

-- Grand livre interne : un mouvement de revenu ou de cout reel. `amount` est toujours positif, le
-- signe economique est porte par entry_type (voir LedgerEntry.java).
CREATE TABLE ledger_entries (
    id          UUID           NOT NULL DEFAULT gen_random_uuid(),
    order_id    UUID,
    entry_type  VARCHAR(32)    NOT NULL,
    currency    VARCHAR(3)     NOT NULL,
    amount      NUMERIC(19,4)  NOT NULL,
    description VARCHAR(500),
    created_at  TIMESTAMPTZ    NOT NULL,
    CONSTRAINT pk_ledger_entries PRIMARY KEY (id),
    CONSTRAINT fk_ledger_entries_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT ck_ledger_entries_amount_positive CHECK (amount >= 0)
);

CREATE INDEX idx_ledger_entries_order ON ledger_entries (order_id);
CREATE INDEX idx_ledger_entries_created_at ON ledger_entries (created_at);

-- Au plus un SERVICE_FEE par ordre : le revenu de frais de service n'est jamais enregistre deux
-- fois pour le meme ordre (voir LedgerService.recordServiceFeeForOrder, idempotent).
CREATE UNIQUE INDEX uq_ledger_entries_order_service_fee ON ledger_entries (order_id)
    WHERE entry_type = 'SERVICE_FEE';

-- Suivi d'un ordre payin/payout cote TransFi, rattache a notre commande. Au plus un PAYIN et un
-- PAYOUT par commande (voir TransfiOrder.java).
CREATE TABLE transfi_orders (
    id                 UUID          NOT NULL DEFAULT gen_random_uuid(),
    order_id           UUID          NOT NULL,
    direction          VARCHAR(16)   NOT NULL,
    provider_order_id  VARCHAR(120),
    status             VARCHAR(32)   NOT NULL,
    pay_url            VARCHAR(500),
    raw_payload        TEXT,
    created_at         TIMESTAMPTZ   NOT NULL,
    updated_at         TIMESTAMPTZ   NOT NULL,
    CONSTRAINT pk_transfi_orders PRIMARY KEY (id),
    CONSTRAINT fk_transfi_orders_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT uq_transfi_orders_order_direction UNIQUE (order_id, direction)
);

CREATE INDEX idx_transfi_orders_order ON transfi_orders (order_id);
CREATE UNIQUE INDEX uq_transfi_orders_provider_order_id ON transfi_orders (provider_order_id)
    WHERE provider_order_id IS NOT NULL;

-- Trace d'un webhook TransFi deja traite -- seule protection reelle contre le retraitement d'une
-- livraison en double (voir TransfiWebhookEvent.java / TransfiOrchestrationService.handleWebhook).
CREATE TABLE transfi_webhook_events (
    id                 UUID          NOT NULL DEFAULT gen_random_uuid(),
    provider_event_id  VARCHAR(150)  NOT NULL,
    event_type         VARCHAR(64),
    received_at        TIMESTAMPTZ   NOT NULL,
    CONSTRAINT pk_transfi_webhook_events PRIMARY KEY (id),
    CONSTRAINT uq_transfi_webhook_events_provider_event_id UNIQUE (provider_event_id)
);
