package com.converter.rate.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Cotation de marche produite par un {@link com.converter.rate.provider.RateProvider}.
 *
 * <p>Valeur immuable, jamais persistee telle quelle : c'est la donnee
 * d'entree du {@link com.converter.rate.engine.RateEngine}, distincte
 * du taux finalement propose au client (voir {@code customerRate}
 * calcule par le moteur). {@code rateSourceId} permet a l'appelant de
 * tracer, dans chaque {@code Quote}, exactement quelle ligne de
 * {@code rate_sources} a produit ce taux.
 *
 * @param currencyPair paire de devises, ex. {@code "XOF/CNY"}
 * @param cfaPerCny    taux de marche brut — convention "1 CNY = X XOF"
 * @param source       type de source ayant produit cette cotation
 * @param effectiveAt  instant auquel cette cotation a ete publiee
 * @param rateSourceId identifiant de la ligne {@code rate_sources} correspondante
 */
public record MarketRate(
        String currencyPair,
        BigDecimal cfaPerCny,
        RateProviderType source,
        Instant effectiveAt,
        UUID rateSourceId
) {
}
