-- =====================================================================
-- V24 — Historique public du taux client (Phase 5, evolution Burkina
-- Faso <-> Chine)
--
-- Serie temporelle append-only du taux REELLEMENT propose au client
-- (breakEvenRate + marge commerciale du moment), jamais recalculee a
-- posteriori. Distincte de `daily_cost_rate_configurations` (interne,
-- porte le breakEvenRate confidentiel — jamais exposee ici, meme
-- indirectement) et de `quotes` (snapshot par transaction individuelle,
-- pas une serie temporelle publique). Pas de contrainte UNIQUE sur
-- (currency_pair) : plusieurs publications par jour doivent toutes
-- rester consultables.
-- =====================================================================

CREATE TABLE public_rate_snapshots (
    id            UUID           NOT NULL DEFAULT gen_random_uuid(),
    currency_pair VARCHAR(10)    NOT NULL,
    customer_rate NUMERIC(18, 6) NOT NULL,
    recorded_at   TIMESTAMPTZ    NOT NULL,
    CONSTRAINT pk_public_rate_snapshots PRIMARY KEY (id),
    CONSTRAINT ck_public_rate_snapshots_rate_positive CHECK (customer_rate > 0)
);

-- Ordre de lecture principal de l'historique public : la paire, du plus
-- recent au plus ancien.
CREATE INDEX idx_public_rate_snapshots_pair_recorded ON public_rate_snapshots (currency_pair, recorded_at DESC);
