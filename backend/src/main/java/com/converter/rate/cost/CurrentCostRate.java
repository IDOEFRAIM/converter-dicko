package com.converter.rate.cost;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Cout de revient courant, tel que consomme par la couche de pricing
 * (voir {@link com.converter.rate.cost.provider.CostRateProvider}).
 *
 * <p>Joue, pour {@code breakEvenRate}, exactement le role que
 * {@link com.converter.rate.domain.MarketRate} jouait pour un taux de
 * marche brut : une valeur immuable, non persistee telle quelle, qui
 * transporte a la fois le taux a utiliser et la reference vers la ligne
 * de {@code daily_cost_rate_configurations} qui l'a produit, pour que
 * l'appelant (le {@code Quote}) puisse tracer sa provenance exacte.
 *
 * @param breakEvenRate       cout de revient courant, en XOF par CNY
 * @param costConfigurationId identifiant de la ligne {@code daily_cost_rate_configurations} correspondante
 * @param asOf                instant auquel cette configuration a ete publiee
 */
public record CurrentCostRate(
        BigDecimal breakEvenRate,
        UUID costConfigurationId,
        Instant asOf
) {
}
