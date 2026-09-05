package com.converter.rate.alert.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Section 32 — moteur de comparaison isole, teste sans base de donnees ni Spring. Utilise
 * exclusivement {@link BigDecimal#compareTo(BigDecimal)} (section 27) : verifie explicitement le
 * cas d'egalite (section 26) pour les deux sens de comparaison.
 */
class RateComparisonTest {

    @Test
    void lessThanOrEqual_currentAboveTarget_isNotSatisfied() {
        assertThat(RateComparison.LESS_THAN_OR_EQUAL.isSatisfied(new BigDecimal("84.20"), new BigDecimal("83.50")))
                .isFalse();
    }

    @Test
    void lessThanOrEqual_currentJustAboveTarget_isNotSatisfied() {
        assertThat(RateComparison.LESS_THAN_OR_EQUAL.isSatisfied(new BigDecimal("83.80"), new BigDecimal("83.50")))
                .isFalse();
    }

    @Test
    void lessThanOrEqual_currentEqualsTarget_isSatisfied() {
        // Section 26 : le cas d'egalite doit etre explicitement vrai.
        assertThat(RateComparison.LESS_THAN_OR_EQUAL.isSatisfied(new BigDecimal("83.50"), new BigDecimal("83.50")))
                .isTrue();
    }

    @Test
    void lessThanOrEqual_currentEqualsTarget_differentScale_isStillSatisfied() {
        // BigDecimal.compareTo ignore l'echelle (83.500000 vs 83.50) — jamais equals().
        assertThat(RateComparison.LESS_THAN_OR_EQUAL.isSatisfied(new BigDecimal("83.500000"), new BigDecimal("83.50")))
                .isTrue();
    }

    @Test
    void lessThanOrEqual_currentBelowTarget_isSatisfied() {
        assertThat(RateComparison.LESS_THAN_OR_EQUAL.isSatisfied(new BigDecimal("83.20"), new BigDecimal("83.50")))
                .isTrue();
    }

    @Test
    void greaterThanOrEqual_isTheExactOppositeSense_neverConfusedWithLessThanOrEqual() {
        // Section 4 : garde contre une inversion accidentelle de la logique de comparaison.
        assertThat(RateComparison.GREATER_THAN_OR_EQUAL.isSatisfied(new BigDecimal("84.20"), new BigDecimal("83.50")))
                .isTrue();
        assertThat(RateComparison.GREATER_THAN_OR_EQUAL.isSatisfied(new BigDecimal("83.20"), new BigDecimal("83.50")))
                .isFalse();
        assertThat(RateComparison.GREATER_THAN_OR_EQUAL.isSatisfied(new BigDecimal("83.50"), new BigDecimal("83.50")))
                .isTrue();
    }
}
