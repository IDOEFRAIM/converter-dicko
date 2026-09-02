-- =====================================================================
-- V12 — Schema Wallet (Phase Wallet + Taux preferentiel)
--
-- Le Wallet n'est PAS un compte bancaire : c'est un solde interne XOF
-- par utilisateur, sur le meme modele que treasury_accounts (Phase 6)
-- -- Available = balance - reserved_balance -- mais scope par
-- utilisateur plutot que par devise globale. Un seul wallet par
-- utilisateur (uq_wallets_user).
--
-- wallet_transactions est le ledger append-only (jamais UPDATE ni
-- DELETE) : chaque mouvement CREDIT/DEBIT/RESERVE/RELEASE capture le
-- solde resultant (balance_after/reserved_after), ce qui permet une
-- reconciliation sans rejouer tout l'historique -- meme principe que
-- treasury_transactions.
-- =====================================================================

CREATE TABLE wallets (
    id               UUID           NOT NULL DEFAULT gen_random_uuid(),
    user_id          UUID           NOT NULL,
    balance          NUMERIC(19, 2) NOT NULL DEFAULT 0,
    reserved_balance NUMERIC(19, 2) NOT NULL DEFAULT 0,
    updated_at       TIMESTAMPTZ    NOT NULL,
    version          BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT pk_wallets PRIMARY KEY (id),
    CONSTRAINT uq_wallets_user UNIQUE (user_id),
    CONSTRAINT ck_wallets_balance_non_negative CHECK (balance >= 0),
    CONSTRAINT ck_wallets_reserved_non_negative CHECK (reserved_balance >= 0),
    CONSTRAINT ck_wallets_reserved_within_balance CHECK (reserved_balance <= balance),
    CONSTRAINT fk_wallets_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE wallet_transactions (
    id             UUID           NOT NULL DEFAULT gen_random_uuid(),
    wallet_id      UUID           NOT NULL,
    type           VARCHAR(16)    NOT NULL,
    amount         NUMERIC(19, 2) NOT NULL,
    balance_after  NUMERIC(19, 2) NOT NULL,
    reserved_after NUMERIC(19, 2) NOT NULL,
    reference_id   UUID,
    reason         VARCHAR(500),
    created_at     TIMESTAMPTZ    NOT NULL,
    CONSTRAINT pk_wallet_transactions PRIMARY KEY (id),
    CONSTRAINT ck_wallet_transactions_type CHECK (type IN ('CREDIT', 'DEBIT', 'RESERVE', 'RELEASE')),
    CONSTRAINT ck_wallet_transactions_amount_positive CHECK (amount > 0),
    CONSTRAINT fk_wallet_transactions_wallet FOREIGN KEY (wallet_id) REFERENCES wallets (id)
);

CREATE INDEX idx_wallet_transactions_wallet_created ON wallet_transactions (wallet_id, created_at DESC);
