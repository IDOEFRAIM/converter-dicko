-- =====================================================================
-- V21 — Carnet de fournisseurs/beneficiaires reutilisables (Phase 1,
-- evolution Burkina Faso <-> Chine)
--
-- Distinct de `beneficiaries` (snapshot immuable 1-1 avec un `order`,
-- inchange par cette migration) : `suppliers` est un carnet d'adresses
-- reutilisable, propre a chaque client (`owner_user_id`), jamais
-- reference par cle etrangere depuis `beneficiaries` — un fournisseur
-- modifie ou desactive n'a donc aucun moyen d'alterer un ordre deja
-- cree (voir Phase 2 pour le lien tracable, purement informatif,
-- `orders.supplier_id`).
--
-- `type` reutilise les memes valeurs que `beneficiaries.type`
-- (ALIPAY / WECHAT_PAY / CHINESE_BANK_ACCOUNT) : la copie vers un
-- snapshot de commande reste une simple copie de valeur.
-- `purpose` reutilise le catalogue transversal introduit par cette
-- meme evolution (voir V23, `orders.purpose`) : classification par
-- defaut du fournisseur, jamais copiee aveuglement sur un ordre.
-- =====================================================================

CREATE TABLE suppliers (
    id             UUID          NOT NULL DEFAULT gen_random_uuid(),
    owner_user_id  UUID          NOT NULL,
    type           VARCHAR(24)   NOT NULL,
    display_name   VARCHAR(120)  NOT NULL,
    legal_name     VARCHAR(160),
    phone          VARCHAR(30),
    email          VARCHAR(160),
    country        VARCHAR(100),
    city           VARCHAR(100),
    province       VARCHAR(100),
    bank_name      VARCHAR(120),
    bank_branch    VARCHAR(120),
    account_name   VARCHAR(120),
    account_number VARCHAR(120)  NOT NULL,
    bank_address   VARCHAR(255),
    swift_code     VARCHAR(20),
    currency       VARCHAR(3)    NOT NULL,
    purpose        VARCHAR(24),
    notes          VARCHAR(1000),
    is_favorite    BOOLEAN       NOT NULL DEFAULT FALSE,
    status         VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE',
    created_at     TIMESTAMPTZ   NOT NULL,
    updated_at     TIMESTAMPTZ   NOT NULL,
    version        BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_suppliers PRIMARY KEY (id),
    CONSTRAINT ck_suppliers_type CHECK (type IN ('ALIPAY', 'WECHAT_PAY', 'CHINESE_BANK_ACCOUNT')),
    CONSTRAINT ck_suppliers_bank_name_required
        CHECK (type <> 'CHINESE_BANK_ACCOUNT' OR bank_name IS NOT NULL),
    CONSTRAINT ck_suppliers_currency CHECK (currency IN ('XOF', 'CNY')),
    CONSTRAINT ck_suppliers_purpose CHECK (purpose IS NULL OR purpose IN (
        'PERSONAL', 'EDUCATION', 'FAMILY_SUPPORT', 'IMPORT_GOODS', 'SERVICES', 'BUSINESS', 'OTHER')),
    CONSTRAINT ck_suppliers_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT fk_suppliers_owner FOREIGN KEY (owner_user_id) REFERENCES users (id)
);

CREATE INDEX idx_suppliers_owner ON suppliers (owner_user_id);
CREATE INDEX idx_suppliers_owner_status ON suppliers (owner_user_id, status);
CREATE INDEX idx_suppliers_owner_favorite ON suppliers (owner_user_id, is_favorite);
