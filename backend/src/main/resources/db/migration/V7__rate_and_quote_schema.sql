-- =====================================================================
-- V7 — Schema Rate Engine + Quote (Phase 3)
--
-- Remplace l'usage prevu pour `exchange_rates` (Phase 1) sans y toucher :
-- cette table est deja migree (V1) mais n'est consommee par aucun code
-- applicatif (le module `exchange` n'a jamais ete construit). Son
-- remplacement par `rate_sources` ci-dessous ne casse donc rien ; elle
-- sera retiree lors de la reconstruction du module `order` (Phase 4),
-- qui referencera `quotes` au lieu de `exchange_rates`. Voir
-- docs/ARCHITECTURE.md, Partie I, section K.3.
--
-- Conventions identiques au reste du schema : UUID, TIMESTAMPTZ (UTC),
-- NUMERIC pour tout montant, enums en VARCHAR + CHECK.
-- =====================================================================

-- ---------------------------------------------------------------------
-- rate_sources — cotations brutes d'un RateProvider (append-only)
--
-- Une ligne = une cotation publiee par une source (MANUAL pour ce MVP).
-- Separe explicitement la donnee de marche (ce que dit la source) de
-- la decision commerciale (marge, frais), qui vit desormais dans
-- `quotes` au moment de chaque devis, jamais ici.
-- ---------------------------------------------------------------------
CREATE TABLE rate_sources (
    id             UUID           NOT NULL DEFAULT gen_random_uuid(),
    provider_type  VARCHAR(16)    NOT NULL,
    currency_pair  VARCHAR(10)    NOT NULL DEFAULT 'XOF/CNY',
    cfa_per_cny    NUMERIC(18, 6) NOT NULL,
    effective_from TIMESTAMPTZ    NOT NULL,
    effective_to   TIMESTAMPTZ,
    note           VARCHAR(500),
    created_by     UUID           NOT NULL,
    created_at     TIMESTAMPTZ    NOT NULL,
    CONSTRAINT pk_rate_sources PRIMARY KEY (id),
    CONSTRAINT ck_rate_sources_provider_type CHECK (provider_type IN ('MANUAL', 'MARKET', 'P2P')),
    CONSTRAINT ck_rate_sources_rate_positive CHECK (cfa_per_cny > 0),
    CONSTRAINT ck_rate_sources_period CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT fk_rate_sources_created_by FOREIGN KEY (created_by) REFERENCES users (id)
);

-- INVARIANT : au plus une cotation "courante" par (source, paire de
-- devises) a la fois. Identique dans son principe a
-- uq_exchange_rate_current (V2) : deux publications concurrentes se
-- serialisent, la seconde echoue en contrainte d'unicite plutot que de
-- produire deux taux courants simultanes.
CREATE UNIQUE INDEX uq_rate_source_current
    ON rate_sources (provider_type, currency_pair, (effective_to IS NULL))
    WHERE effective_to IS NULL;

CREATE INDEX idx_rate_sources_effective_from ON rate_sources (currency_pair, effective_from DESC);

-- ---------------------------------------------------------------------
-- quotes — devis client, snapshot financier immuable
--
-- Separe explicitement market_rate (donnee de marche), margin_percentage
-- (decision commerciale) et customer_rate (ce qui est propose au
-- client) : trois valeurs distinctes, jamais fusionnees. fee_percentage/
-- fixed_fee_xof/fee_xof sont une ligne de revenu separee de la marge.
--
-- Une fois cree, AUCUNE colonne financiere de cette table n'est
-- modifiee : une republication ulterieure du taux manuel, de la marge
-- ou des frais ne touche jamais une ligne existante. Seules `status`,
-- `accepted_at`, `cancelled_at` et `version` changent, via les
-- transitions ACTIVE -> ACCEPTED | EXPIRED | CANCELLED.
-- ---------------------------------------------------------------------
CREATE TABLE quotes (
    id                UUID           NOT NULL DEFAULT gen_random_uuid(),
    user_id           UUID           NOT NULL,
    direction         VARCHAR(16)    NOT NULL,
    amount_xof        NUMERIC(19, 2) NOT NULL,
    amount_cny        NUMERIC(19, 2) NOT NULL,
    market_rate       NUMERIC(18, 6) NOT NULL,
    margin_percentage NUMERIC(6, 4)  NOT NULL,
    customer_rate     NUMERIC(18, 6) NOT NULL,
    fee_percentage    NUMERIC(6, 4)  NOT NULL,
    fixed_fee_xof     NUMERIC(19, 2) NOT NULL,
    fee_xof           NUMERIC(19, 2) NOT NULL,
    net_amount_xof    NUMERIC(19, 2) NOT NULL,
    rate_source_id    UUID           NOT NULL,
    status            VARCHAR(16)    NOT NULL,
    created_at        TIMESTAMPTZ    NOT NULL,
    expires_at        TIMESTAMPTZ    NOT NULL,
    accepted_at       TIMESTAMPTZ,
    cancelled_at      TIMESTAMPTZ,
    version           BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT pk_quotes PRIMARY KEY (id),
    CONSTRAINT ck_quotes_direction CHECK (direction IN ('SEND_XOF', 'RECEIVE_CNY')),
    CONSTRAINT ck_quotes_status CHECK (status IN ('ACTIVE', 'ACCEPTED', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT ck_quotes_amount_xof CHECK (amount_xof > 0),
    CONSTRAINT ck_quotes_amount_cny CHECK (amount_cny > 0),
    CONSTRAINT ck_quotes_market_rate CHECK (market_rate > 0),
    CONSTRAINT ck_quotes_customer_rate CHECK (customer_rate > 0),
    CONSTRAINT ck_quotes_margin CHECK (margin_percentage >= 0),
    CONSTRAINT ck_quotes_fee_percentage CHECK (fee_percentage >= 0 AND fee_percentage < 100),
    CONSTRAINT ck_quotes_fixed_fee CHECK (fixed_fee_xof >= 0),
    CONSTRAINT ck_quotes_fee_xof CHECK (fee_xof >= 0),
    CONSTRAINT ck_quotes_net_amount CHECK (net_amount_xof > 0),
    -- Identite comptable verifiee au niveau base, en plus du calcul
    -- applicatif : defense en profondeur contre un bug de calcul futur.
    CONSTRAINT ck_quotes_net_coherent CHECK (net_amount_xof = amount_xof - fee_xof),
    CONSTRAINT ck_quotes_expiry_window CHECK (expires_at > created_at),
    CONSTRAINT fk_quotes_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_quotes_rate_source FOREIGN KEY (rate_source_id) REFERENCES rate_sources (id)
);

CREATE INDEX idx_quotes_user_created ON quotes (user_id, created_at DESC);

-- Index partiel : seuls les devis reellement actifs sont indexes, pour
-- un futur job d'expiration ou un balayage de supervision.
CREATE INDEX idx_quotes_status_expiry
    ON quotes (status, expires_at)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_quotes_rate_source ON quotes (rate_source_id);
