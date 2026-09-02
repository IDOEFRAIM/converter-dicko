package com.converter.rate.service;

import com.converter.rate.domain.RateProviderType;
import com.converter.rate.domain.RateSource;
import com.converter.rate.dto.RateSourceResponse;
import com.converter.rate.provider.RateProvider;
import com.converter.rate.repository.RateSourceRepository;
import com.converter.support.AbstractRateQuoteIT;
import com.converter.user.domain.RoleCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Garantit qu'il n'existe jamais deux taux manuels "courants"
 * simultanement — l'invariant le plus important du module {@code rate}
 * (section 8/9 du cahier des charges de cette phase).
 */
class RateSourceUniquenessIT extends AbstractRateQuoteIT {

    @Autowired
    private RateAdminService rateAdminService;

    @Autowired
    private RateSourceRepository rateSourceRepository;

    @Test
    void publishingTwice_closesThePreviousRateAndKeepsExactlyOneCurrent() {
        var actorId = createUser(RoleCode.ADMIN).getId();

        RateSourceResponse first = rateAdminService.publishManualRate(
                new BigDecimal("85.000000"), "premiere", RateProvider.DEFAULT_CURRENCY_PAIR, actorId);
        assertThat(hasExactlyOneCurrent()).isTrue();

        RateSourceResponse second = rateAdminService.publishManualRate(
                new BigDecimal("90.000000"), "seconde", RateProvider.DEFAULT_CURRENCY_PAIR, actorId);

        assertThat(hasExactlyOneCurrent()).isTrue();
        assertThat(rateSourceRepository.findById(first.id()).orElseThrow().getEffectiveTo()).isNotNull();
        assertThat(rateSourceRepository.findById(second.id()).orElseThrow().getEffectiveTo()).isNull();

        // L'historique n'est jamais ecrase : les deux lignes existent
        // toujours, seule leur periode d'effectivite differe.
        assertThat(rateSourceRepository.findById(first.id())).isPresent();
        assertThat(rateSourceRepository.findById(second.id())).isPresent();
    }

    @Test
    void insertingASecondCurrentRateDirectly_violatesTheUniqueConstraint() {
        var actorId = createUser(RoleCode.ADMIN).getId();
        rateAdminService.publishManualRate(new BigDecimal("85.000000"), "premiere",
                RateProvider.DEFAULT_CURRENCY_PAIR, actorId);

        // Contournement volontaire du service (qui cloture toujours
        // l'ancienne ligne avant d'inserer la nouvelle), pour prouver que
        // la garantie tient au niveau de la base elle-meme, independamment
        // de la discipline du code applicatif : insertion directe d'une
        // deuxieme ligne "courante" pour la meme (provider, paire).
        var rogue = new RateSource(
                RateProviderType.MANUAL, RateProvider.DEFAULT_CURRENCY_PAIR, new BigDecimal("99.000000"),
                Instant.now(), "insertion directe", actorId, Instant.now());

        assertThatThrownBy(() -> {
            rateSourceRepository.saveAndFlush(rogue);
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    private boolean hasExactlyOneCurrent() {
        return rateSourceRepository
                .findByProviderTypeAndCurrencyPairAndEffectiveToIsNull(
                        RateProviderType.MANUAL, RateProvider.DEFAULT_CURRENCY_PAIR)
                .isPresent();
    }
}
