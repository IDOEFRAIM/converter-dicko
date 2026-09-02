-- =====================================================================
-- V13 — Schema Taux preferentiel + Echange (Phase Wallet + Taux preferentiel)
--
-- Une PreferredRateRequest immobilise (RESERVE, voir V12) un montant
-- XOF du Wallet du client des sa creation -- jamais debite tant que le
-- taux cible n'est pas atteint (section "Immutabilite et concurrence"
-- de la specification). Le scheduler (PreferredRateScheduler) evalue
-- periodiquement chaque demande ACTIVE contre le taux courant
-- (ManualRateProvider, aucune source externe) et soit la declenche
-- (statut EXECUTED, cree un Exchange), soit l'expire a J+3 (statut
-- EXPIRED, fonds restitues).
--
-- Un Exchange represente l'operation elle-meme une fois le taux
-- atteint -- distinct d'un Order/Settlement (qui exigent un
-- beneficiaire en Chine, non demande par cette fonctionnalite) :
-- l'echange se limite a la conversion de valeur du solde Wallet, avec
-- son propre cycle de progression (T0, +45min, +90min, fin -- 2h max).
-- =====================================================================

CREATE TABLE preferred_rate_requests (
    id            UUID           NOT NULL DEFAULT gen_random_uuid(),
    user_id       UUID           NOT NULL,
    direction     VARCHAR(16)    NOT NULL,
    amount_xof    NUMERIC(19, 2) NOT NULL,
    target_rate   NUMERIC(18, 6) NOT NULL,
    status        VARCHAR(16)    NOT NULL,
    achieved_rate NUMERIC(18, 6),
    exchange_id   UUID,
    created_at    TIMESTAMPTZ    NOT NULL,
    expires_at    TIMESTAMPTZ    NOT NULL,
    executed_at   TIMESTAMPTZ,
    expired_at    TIMESTAMPTZ,
    cancelled_at  TIMESTAMPTZ,
    version       BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT pk_preferred_rate_requests PRIMARY KEY (id),
    CONSTRAINT ck_preferred_rate_direction CHECK (direction IN ('XOF_TO_CNY')),
    CONSTRAINT ck_preferred_rate_status CHECK (status IN ('ACTIVE', 'EXECUTED', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT ck_preferred_rate_amount_positive CHECK (amount_xof > 0),
    CONSTRAINT ck_preferred_rate_target_positive CHECK (target_rate > 0),
    CONSTRAINT fk_preferred_rate_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_preferred_rate_status_expires ON preferred_rate_requests (status, expires_at);
CREATE INDEX idx_preferred_rate_user ON preferred_rate_requests (user_id, created_at DESC);

CREATE TABLE exchanges (
    id                       UUID           NOT NULL DEFAULT gen_random_uuid(),
    user_id                  UUID           NOT NULL,
    preferred_rate_request_id UUID          NOT NULL,
    amount_xof               NUMERIC(19, 2) NOT NULL,
    achieved_rate             NUMERIC(18, 6) NOT NULL,
    amount_cny                NUMERIC(19, 2) NOT NULL,
    status                    VARCHAR(16)    NOT NULL,
    started_at                TIMESTAMPTZ    NOT NULL,
    progress_45_sent_at       TIMESTAMPTZ,
    progress_90_sent_at       TIMESTAMPTZ,
    completed_at              TIMESTAMPTZ,
    cancelled_at              TIMESTAMPTZ,
    version                   BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT pk_exchanges PRIMARY KEY (id),
    CONSTRAINT uq_exchanges_preferred_rate_request UNIQUE (preferred_rate_request_id),
    CONSTRAINT ck_exchanges_status CHECK (status IN ('STARTED', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT fk_exchanges_preferred_rate_request
        FOREIGN KEY (preferred_rate_request_id) REFERENCES preferred_rate_requests (id),
    CONSTRAINT fk_exchanges_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_exchanges_status ON exchanges (status);

ALTER TABLE preferred_rate_requests
    ADD CONSTRAINT fk_preferred_rate_exchange FOREIGN KEY (exchange_id) REFERENCES exchanges (id);
