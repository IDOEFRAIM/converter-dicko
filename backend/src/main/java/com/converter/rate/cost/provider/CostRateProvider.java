package com.converter.rate.cost.provider;

import com.converter.rate.cost.CurrentCostRate;

/**
 * Port d'acces au cout de revient courant (XOF par CNY).
 *
 * <p>Symetrique de {@link com.converter.rate.provider.RateProvider} pour
 * la chaine de cout : le domaine (ici, la creation d'un {@code Quote})
 * ne depend jamais d'une implementation concrete. Seule implementation
 * a ce stade : {@link DailyCostRateProvider}, adossee a
 * {@code daily_cost_rate_configurations}.
 */
public interface CostRateProvider {

    /**
     * Configuration de cout actuellement en vigueur.
     *
     * @throws com.converter.common.exception.BusinessException {@code COST_RATE_UNAVAILABLE}
     *         si aucune configuration de cout n'a encore ete publiee
     */
    CurrentCostRate currentRate();
}
