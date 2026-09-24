-- =====================================================================
-- V29 — Ruee collective (mission "differenciation marketing", Lot 3)
--
-- Un participant cree un pool avec un objectif de volume XOF et une echeance. Des amis le
-- rejoignent via un code court partageable. Chaque ordre cree en reference a ce pool (voir
-- orders.pool_id) fait progresser current_amount_xof. Si l'objectif est atteint avant l'echeance,
-- TOUS les participants (pas seulement les contributeurs) recoivent une reduction de marge
-- utilisable sur leur PROCHAIN devis (jamais retroactive sur l'ordre qui a rempli l'objectif --
-- immutabilite du pricing deja fige, voir QuoteService).
--
-- reward_margin_reduction_percentage est fige sur le pool a sa creation (snapshot du reglage
-- POOL_REWARD_MARGIN_REDUCTION_PERCENTAGE) puis recopie sur chaque pool_participants a la reussite
-- -- une modification ulterieure du reglage global n'affecte jamais une Ruee deja en cours ou deja
-- recompensee.
-- =====================================================================

CREATE TABLE pools (
    id                                UUID          NOT NULL DEFAULT gen_random_uuid(),
    code                              VARCHAR(8)    NOT NULL,
    creator_id                        UUID          NOT NULL,
    currency_pair                     VARCHAR(10)   NOT NULL,
    target_amount_xof                 NUMERIC(19,2) NOT NULL,
    current_amount_xof                NUMERIC(19,2) NOT NULL DEFAULT 0,
    status                            VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE',
    reward_margin_reduction_percentage NUMERIC(6,3) NOT NULL,
    created_at                        TIMESTAMPTZ   NOT NULL,
    expires_at                        TIMESTAMPTZ   NOT NULL,
    succeeded_at                      TIMESTAMPTZ,
    expired_at                        TIMESTAMPTZ,
    cancelled_at                      TIMESTAMPTZ,
    version                           BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_pools PRIMARY KEY (id),
    CONSTRAINT uq_pools_code UNIQUE (code),
    CONSTRAINT ck_pools_target_positive CHECK (target_amount_xof > 0),
    CONSTRAINT ck_pools_current_non_negative CHECK (current_amount_xof >= 0),
    CONSTRAINT ck_pools_status CHECK (status IN ('ACTIVE', 'SUCCEEDED', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT fk_pools_creator FOREIGN KEY (creator_id) REFERENCES users (id)
);

CREATE INDEX idx_pools_status ON pools (status);
CREATE INDEX idx_pools_creator ON pools (creator_id);

CREATE TABLE pool_participants (
    id                                UUID          NOT NULL DEFAULT gen_random_uuid(),
    pool_id                           UUID          NOT NULL,
    user_id                           UUID          NOT NULL,
    is_creator                        BOOLEAN       NOT NULL DEFAULT FALSE,
    joined_at                         TIMESTAMPTZ   NOT NULL,
    contributed_amount_xof            NUMERIC(19,2) NOT NULL DEFAULT 0,
    reward_granted_at                 TIMESTAMPTZ,
    reward_margin_reduction_percentage NUMERIC(6,3),
    reward_consumed_at                TIMESTAMPTZ,
    version                           BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_pool_participants PRIMARY KEY (id),
    CONSTRAINT uq_pool_participants_pool_user UNIQUE (pool_id, user_id),
    CONSTRAINT ck_pool_participants_contributed_non_negative CHECK (contributed_amount_xof >= 0),
    CONSTRAINT fk_pool_participants_pool FOREIGN KEY (pool_id) REFERENCES pools (id),
    CONSTRAINT fk_pool_participants_user FOREIGN KEY (user_id) REFERENCES users (id)
);

-- Requete-cle de QuoteService : trouver une recompense active non consommee pour un utilisateur.
CREATE INDEX idx_pool_participants_user_reward
    ON pool_participants (user_id)
    WHERE reward_granted_at IS NOT NULL AND reward_consumed_at IS NULL;

-- ---------------------------------------------------------------------
-- orders : reference optionnelle vers le pool auquel cet ordre contribue (NULL = ordre normal,
-- immense majorite des cas). Jamais modifiable apres creation, comme supplier_id (V22).
-- ---------------------------------------------------------------------
ALTER TABLE orders
    ADD COLUMN pool_id UUID,
    ADD CONSTRAINT fk_orders_pool FOREIGN KEY (pool_id) REFERENCES pools (id);

CREATE INDEX idx_orders_pool ON orders (pool_id) WHERE pool_id IS NOT NULL;

-- ---------------------------------------------------------------------
-- quotes : trace si une reduction de Ruee a ete appliquee a CE devis precis (fige a la creation,
-- jamais recalcule -- voir QuoteService#create).
-- ---------------------------------------------------------------------
ALTER TABLE quotes
    ADD COLUMN pool_reward_applied BOOLEAN NOT NULL DEFAULT FALSE;

-- ---------------------------------------------------------------------
-- notifications : nouveaux types POOL_SUCCEEDED/POOL_EXPIRED (meme patron DROP+ADD que V16/V25).
-- ---------------------------------------------------------------------
ALTER TABLE notifications DROP CONSTRAINT ck_notifications_type;
ALTER TABLE notifications ADD CONSTRAINT ck_notifications_type CHECK (type IN (
    'QUOTE_CREATED', 'PAYMENT_SUBMITTED', 'PAYMENT_CONFIRMED',
    'EXCHANGE_STARTED', 'EXCHANGE_PROGRESS', 'EXCHANGE_COMPLETED',
    'PREFERRED_RATE_REACHED', 'PREFERRED_RATE_EXPIRED', 'EXCHANGE_CANCELLED',
    'ORDER_EXPIRED', 'RATE_ALERT_TRIGGERED', 'POOL_SUCCEEDED', 'POOL_EXPIRED'));

-- ---------------------------------------------------------------------
-- system_settings : reduction de marge accordee par une Ruee reussie.
-- ---------------------------------------------------------------------
INSERT INTO system_settings (setting_key, value, value_type, description, is_public, updated_at) VALUES
    ('POOL_REWARD_MARGIN_REDUCTION_PERCENTAGE', '0.5', 'DECIMAL',
     'Points de marge retires sur le PROCHAIN devis de chaque participant d''une Ruee reussie.',
     FALSE, now())
ON CONFLICT (setting_key) DO NOTHING;
