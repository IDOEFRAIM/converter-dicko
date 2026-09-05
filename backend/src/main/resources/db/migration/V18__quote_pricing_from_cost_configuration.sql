-- =====================================================================
-- V18 — Le Quote se price desormais depuis daily_cost_rate_configurations
-- (Phase 3.1)
--
-- Jusqu'ici, quotes.rate_source_id pointait vers rate_sources (cotation
-- de marche saisie manuellement) et quotes.market_rate en portait la
-- valeur. Depuis cette migration, un nouveau Quote fige le
-- breakEvenRate calcule par CostRateCalculator a partir de la derniere
-- daily_cost_rate_configurations (V17) : le cout de revient reel de la
-- chaine XOF -> USD -> CNY, PAS une cotation de marche.
--
-- rate_sources / RateProvider ne sont pas retires : ils restent la
-- source utilisee par le module preferredrate. Ce n'est que la
-- provenance du pricing d'un Quote qui change ici.
--
-- Aucune ligne existante n'est preservee par une conversion de donnees :
-- ce backend est en developpement, sans donnees de production a migrer
-- (voir docs/ARCHITECTURE.md, Partie I, section G.7).
-- =====================================================================

ALTER TABLE quotes DROP CONSTRAINT fk_quotes_rate_source;
DROP INDEX idx_quotes_rate_source;

ALTER TABLE quotes RENAME COLUMN rate_source_id TO cost_configuration_id;
ALTER TABLE quotes ADD CONSTRAINT fk_quotes_cost_configuration
    FOREIGN KEY (cost_configuration_id) REFERENCES daily_cost_rate_configurations (id);
CREATE INDEX idx_quotes_cost_configuration ON quotes (cost_configuration_id);

ALTER TABLE quotes RENAME COLUMN market_rate TO break_even_rate;
ALTER TABLE quotes RENAME CONSTRAINT ck_quotes_market_rate TO ck_quotes_break_even_rate;
