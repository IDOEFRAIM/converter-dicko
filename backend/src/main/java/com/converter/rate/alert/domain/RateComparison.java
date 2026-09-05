package com.converter.rate.alert.domain;

import java.math.BigDecimal;

/**
 * Regle de declenchement d'une {@link RateAlert}, encapsulee ici et nulle part ailleurs
 * (jamais un {@code if} disperse dans un controller ou un scheduler — voir {@code RateAlertService}).
 *
 * <p>Comparaison via {@link BigDecimal#compareTo(BigDecimal)} exclusivement, jamais {@code ==}
 * ni conversion en {@code double}/{@code float} : {@code targetRate}/{@code currentRate} peuvent
 * porter des echelles decimales differentes (ex. {@code 83.50} vs {@code 83.500000}) sans que
 * cela n'affecte l'egalite metier.
 */
public enum RateComparison {

    /** Le cas standard XOF/CNY : declenche quand le taux client courant descend a la cible ou en dessous. */
    LESS_THAN_OR_EQUAL {
        @Override
        public boolean isSatisfied(BigDecimal currentRate, BigDecimal targetRate) {
            return currentRate.compareTo(targetRate) <= 0;
        }
    },

    /** Prevu pour une paire/un sens futur — non utilise par le produit aujourd'hui. */
    GREATER_THAN_OR_EQUAL {
        @Override
        public boolean isSatisfied(BigDecimal currentRate, BigDecimal targetRate) {
            return currentRate.compareTo(targetRate) >= 0;
        }
    };

    public abstract boolean isSatisfied(BigDecimal currentRate, BigDecimal targetRate);
}
