-- =====================================================================
-- V17 — Cout de revient XOF -> USD -> CNY (daily_cost_rate_configurations)
--
-- Historise les parametres de la chaine de conversion reellement
-- utilisee par la tresorerie (XOF -> USD -> CNY) et le breakEvenRate
-- (XOF par CNY) qui en decoule pour un montant de reference donne.
--
-- Distincte de rate_sources (V7) : cette table ne porte jamais une
-- cotation publiee aux clients, uniquement la base de cout interne. Les
-- deux tables ne se referencent pas entre elles. Append-only, comme
-- rate_sources : aucune ligne n'est jamais modifiee ni supprimee par le
-- code applicatif, une nouvelle publication insere toujours une
-- nouvelle ligne.
-- =====================================================================

CREATE TABLE daily_cost_rate_configurations (
    id                     UUID           NOT NULL DEFAULT gen_random_uuid(),
    business_date          DATE           NOT NULL,
    rate_xof_usd           NUMERIC(18, 6) NOT NULL,
    rate_usd_cny           NUMERIC(18, 6) NOT NULL,
    fee_xof_usd_percent    NUMERIC(8, 6)  NOT NULL,
    fee_usd_cny_fixed_usd  NUMERIC(12, 2) NOT NULL,
    reference_amount_xof   NUMERIC(19, 2) NOT NULL,
    break_even_rate        NUMERIC(18, 6) NOT NULL,
    note                   VARCHAR(500),
    created_by             UUID           NOT NULL,
    created_at             TIMESTAMPTZ    NOT NULL,
    CONSTRAINT pk_daily_cost_rate_configurations PRIMARY KEY (id),
    CONSTRAINT ck_dcrc_rate_xof_usd_positive CHECK (rate_xof_usd > 0),
    CONSTRAINT ck_dcrc_rate_usd_cny_positive CHECK (rate_usd_cny > 0),
    CONSTRAINT ck_dcrc_fee_xof_usd_percent_range CHECK (fee_xof_usd_percent >= 0 AND fee_xof_usd_percent < 1),
    CONSTRAINT ck_dcrc_fee_usd_cny_fixed_non_negative CHECK (fee_usd_cny_fixed_usd >= 0),
    CONSTRAINT ck_dcrc_reference_amount_positive CHECK (reference_amount_xof > 0),
    CONSTRAINT ck_dcrc_break_even_rate_positive CHECK (break_even_rate > 0),
    CONSTRAINT fk_dcrc_created_by FOREIGN KEY (created_by) REFERENCES users (id)
);

CREATE INDEX idx_dcrc_business_date ON daily_cost_rate_configurations (business_date DESC);
CREATE INDEX idx_dcrc_created_at ON daily_cost_rate_configurations (created_at DESC);
