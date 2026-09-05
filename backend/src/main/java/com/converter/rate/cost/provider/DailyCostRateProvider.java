package com.converter.rate.cost.provider;

import com.converter.common.exception.BusinessException;
import com.converter.common.exception.ErrorCode;
import com.converter.rate.cost.CurrentCostRate;
import com.converter.rate.cost.domain.DailyCostRateConfiguration;
import com.converter.rate.cost.repository.DailyCostRateConfigurationRepository;
import org.springframework.stereotype.Component;

/**
 * Source de cout operationnelle : la derniere configuration de cout
 * publiee par un administrateur (voir {@link com.converter.rate.cost.service.CostRateAdminService}).
 *
 * <p>{@code daily_cost_rate_configurations} n'a pas de notion de ligne
 * "courante" (contrairement a {@code rate_sources}) : chaque
 * publication insere simplement une nouvelle ligne complete et
 * immuable, jamais de cloture ni de verrou necessaire — deux
 * publications concurrentes ne peuvent donc jamais laisser un lecteur
 * composer un resultat a partir de deux lignes differentes ; une
 * transaction ne voit, a tout instant, que la derniere ligne
 * entierement validee (garantie MVCC PostgreSQL), jamais une ligne
 * partiellement ecrite.
 */
@Component
public class DailyCostRateProvider implements CostRateProvider {

    private final DailyCostRateConfigurationRepository repository;

    public DailyCostRateProvider(DailyCostRateConfigurationRepository repository) {
        this.repository = repository;
    }

    @Override
    public CurrentCostRate currentRate() {
        DailyCostRateConfiguration configuration = repository.findFirstByOrderByCreatedAtDesc()
                .orElseThrow(() -> new BusinessException(ErrorCode.COST_RATE_UNAVAILABLE,
                        "Aucune configuration de cout n'a encore ete publiee. Un administrateur doit "
                                + "d'abord publier les parametres du jour (rateXofUsd, rateUsdCny, frais)."));
        return new CurrentCostRate(configuration.getBreakEvenRate(), configuration.getId(), configuration.getCreatedAt());
    }
}
