package com.converter.rate.cost;

import com.converter.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests unitaires purs : {@link CostRateCalculator} n'a aucune
 * dependance Spring/HTTP/persistance, il est donc instancie
 * directement, comme {@code RateEngineTest}.
 *
 * <p>Les valeurs de reference de ce fichier sont calculees
 * independamment (arithmetique decimale exacte, arrondi HALF_UP a 6
 * decimales) et non recopiees d'un exemple approximatif : le cahier
 * des charges cite ~87.99 pour le cas nominal a titre illustratif, la
 * valeur exacte produite par l'algorithme specifie est 87.971572 —
 * c'est cette derniere qui est verifiee ici, jamais une constante
 * inventee.
 */
class CostRateCalculatorTest {

    private final CostRateCalculator calculator = new CostRateCalculator();

    @Test
    void calculateBreakEven_nominalCase_matchesReferenceComputation() {
        BreakEvenResult result = calculator.calculateBreakEven(
                new BigDecimal("1000000"), new BigDecimal("583"), new BigDecimal("6.70"),
                new BigDecimal("0.01"), new BigDecimal("1.50"));

        assertThat(result.netXof()).isEqualByComparingTo("990000.00");
        assertThat(result.breakEvenRate()).isEqualByComparingTo("87.971572");
    }

    @Test
    void calculateBreakEven_withNoFees_equalsSimpleCrossRate() {
        // Sans aucun frais, le cout de revient est exactement le taux croise
        // rateXofUsd / rateUsdCny : la chaine XOF -> USD -> CNY ne fait alors
        // que composer deux taux, sans aucun cout additionnel.
        BreakEvenResult result = calculator.calculateBreakEven(
                new BigDecimal("1000000"), new BigDecimal("583"), new BigDecimal("6.70"),
                BigDecimal.ZERO, BigDecimal.ZERO);

        assertThat(result.breakEvenRate()).isEqualByComparingTo("87.014925");
        assertThat(result.breakEvenRate()).isEqualByComparingTo(
                new BigDecimal("583").divide(new BigDecimal("6.70"), 6, java.math.RoundingMode.HALF_UP));
    }

    @Test
    void calculateBreakEven_withXofToUsdFeeOnly_increasesBreakEvenRate() {
        BreakEvenResult result = calculator.calculateBreakEven(
                new BigDecimal("1000000"), new BigDecimal("583"), new BigDecimal("6.70"),
                new BigDecimal("0.02"), BigDecimal.ZERO);

        assertThat(result.breakEvenRate()).isEqualByComparingTo("88.790740");
    }

    @Test
    void calculateBreakEven_withUsdToCnyFeeOnly_increasesBreakEvenRate() {
        BreakEvenResult result = calculator.calculateBreakEven(
                new BigDecimal("1000000"), new BigDecimal("583"), new BigDecimal("6.70"),
                BigDecimal.ZERO, new BigDecimal("5"));

        assertThat(result.breakEvenRate()).isEqualByComparingTo("87.269315");
    }

    @Test
    void calculateBreakEven_withManyDecimals_staysConsistentAndRoundsToSixDecimals() {
        BreakEvenResult result = calculator.calculateBreakEven(
                new BigDecimal("333333"), new BigDecimal("582.37"), new BigDecimal("6.6543"),
                new BigDecimal("0.0137"), new BigDecimal("2.13"));

        assertThat(result.breakEvenRate()).isEqualByComparingTo("89.069557");
        assertThat(result.breakEvenRate().scale()).isEqualTo(6);
    }

    @Test
    void calculateBreakEven_fixedFeeRelativeImpact_shrinksAsAmountGrows() {
        // feeUsdCnyFixedUsd est un cout fixe : son impact RELATIF sur le breakEvenRate (compare a un
        // calcul sans frais fixe, memes taux) doit diminuer strictement a mesure que le montant
        // grandit, puisqu'un cout fixe est de plus en plus dilue par le volume converti.
        BigDecimal[] amounts = {new BigDecimal("100000"), new BigDecimal("1000000"), new BigDecimal("10000000")};
        BigDecimal previousRelativeImpact = null;

        for (BigDecimal amount : amounts) {
            BigDecimal withFixedFee = calculator.calculateBreakEven(
                    amount, new BigDecimal("583"), new BigDecimal("6.70"), BigDecimal.ZERO, new BigDecimal("1.50"))
                    .breakEvenRate();
            BigDecimal withoutFixedFee = calculator.calculateBreakEven(
                    amount, new BigDecimal("583"), new BigDecimal("6.70"), BigDecimal.ZERO, BigDecimal.ZERO)
                    .breakEvenRate();
            BigDecimal relativeImpact = withFixedFee.subtract(withoutFixedFee)
                    .divide(withoutFixedFee, 20, java.math.RoundingMode.HALF_UP);

            if (previousRelativeImpact != null) {
                assertThat(relativeImpact)
                        .as("montant=%s", amount)
                        .isLessThan(previousRelativeImpact);
            }
            previousRelativeImpact = relativeImpact;
        }
    }

    @Test
    void calculateBreakEven_dependsOnReferenceAmount_becauseFixedFeeIsAmountIndependent() {
        // feeUsdCnyFixedUsd est un cout fixe : un petit montant absorbe
        // proportionnellement plus ce cout fixe, donc son breakEvenRate est
        // plus eleve que celui d'un gros montant, memes taux et memes frais
        // proportionnels.
        BreakEvenResult small = calculator.calculateBreakEven(
                new BigDecimal("100000"), new BigDecimal("583"), new BigDecimal("6.70"),
                new BigDecimal("0.01"), new BigDecimal("1.50"));
        BreakEvenResult large = calculator.calculateBreakEven(
                new BigDecimal("1000000"), new BigDecimal("583"), new BigDecimal("6.70"),
                new BigDecimal("0.01"), new BigDecimal("1.50"));

        assertThat(small.breakEvenRate()).isGreaterThan(large.breakEvenRate());
    }

    @Test
    void calculateBreakEven_withFeeConsumingWholeAmount_throwsBusinessException() {
        assertThatThrownBy(() -> calculator.calculateBreakEven(
                new BigDecimal("100"), new BigDecimal("583"), new BigDecimal("6.70"),
                BigDecimal.ZERO, new BigDecimal("1000")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void calculateBreakEven_withZeroOrNegativeAmount_throwsBusinessException() {
        assertThatThrownBy(() -> calculator.calculateBreakEven(
                BigDecimal.ZERO, new BigDecimal("583"), new BigDecimal("6.70"),
                new BigDecimal("0.01"), new BigDecimal("1.50")))
                .isInstanceOf(BusinessException.class);

        assertThatThrownBy(() -> calculator.calculateBreakEven(
                new BigDecimal("-1"), new BigDecimal("583"), new BigDecimal("6.70"),
                new BigDecimal("0.01"), new BigDecimal("1.50")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void calculateBreakEven_withZeroOrNegativeRates_throwsBusinessException() {
        assertThatThrownBy(() -> calculator.calculateBreakEven(
                new BigDecimal("1000000"), BigDecimal.ZERO, new BigDecimal("6.70"),
                new BigDecimal("0.01"), new BigDecimal("1.50")))
                .isInstanceOf(BusinessException.class);

        assertThatThrownBy(() -> calculator.calculateBreakEven(
                new BigDecimal("1000000"), new BigDecimal("583"), new BigDecimal("-1"),
                new BigDecimal("0.01"), new BigDecimal("1.50")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void calculateBreakEven_withFeeXofUsdPercentOutOfFractionRange_throwsBusinessException() {
        // feeXofUsdPercent est une fraction (0.01 = 1 %) : une valeur >= 1
        // consommerait 100 % ou plus du montant XOF, ce qui est refuse.
        assertThatThrownBy(() -> calculator.calculateBreakEven(
                new BigDecimal("1000000"), new BigDecimal("583"), new BigDecimal("6.70"),
                BigDecimal.ONE, new BigDecimal("1.50")))
                .isInstanceOf(BusinessException.class);

        assertThatThrownBy(() -> calculator.calculateBreakEven(
                new BigDecimal("1000000"), new BigDecimal("583"), new BigDecimal("6.70"),
                new BigDecimal("-0.01"), new BigDecimal("1.50")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void calculateBreakEven_withNegativeFixedFee_throwsBusinessException() {
        assertThatThrownBy(() -> calculator.calculateBreakEven(
                new BigDecimal("1000000"), new BigDecimal("583"), new BigDecimal("6.70"),
                new BigDecimal("0.01"), new BigDecimal("-1")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void calculateBreakEven_whenFeesIncrease_breakEvenRateIncreases() {
        BreakEvenResult base = calculator.calculateBreakEven(
                new BigDecimal("1000000"), new BigDecimal("583"), new BigDecimal("6.70"),
                new BigDecimal("0.01"), new BigDecimal("1.50"));
        BreakEvenResult higherPercentFee = calculator.calculateBreakEven(
                new BigDecimal("1000000"), new BigDecimal("583"), new BigDecimal("6.70"),
                new BigDecimal("0.02"), new BigDecimal("1.50"));
        BreakEvenResult higherFixedFee = calculator.calculateBreakEven(
                new BigDecimal("1000000"), new BigDecimal("583"), new BigDecimal("6.70"),
                new BigDecimal("0.01"), new BigDecimal("5"));

        assertThat(higherPercentFee.breakEvenRate()).isGreaterThan(base.breakEvenRate());
        assertThat(higherFixedFee.breakEvenRate()).isGreaterThan(base.breakEvenRate());
    }

    @Test
    void calculateBreakEven_whenRateUsdCnyIncreases_breakEvenRateDecreases() {
        // Plus on obtient de CNY par dollar, moins il faut de XOF de depart
        // pour obtenir 1 CNY : le cout de revient baisse.
        BreakEvenResult base = calculator.calculateBreakEven(
                new BigDecimal("1000000"), new BigDecimal("583"), new BigDecimal("6.70"),
                new BigDecimal("0.01"), new BigDecimal("1.50"));
        BreakEvenResult higherRateUsdCny = calculator.calculateBreakEven(
                new BigDecimal("1000000"), new BigDecimal("583"), new BigDecimal("7.00"),
                new BigDecimal("0.01"), new BigDecimal("1.50"));

        assertThat(higherRateUsdCny.breakEvenRate()).isLessThan(base.breakEvenRate());
    }

    @Test
    void calculateBreakEven_whenRateXofUsdIncreases_breakEvenRateIncreases() {
        // Plus il faut de XOF pour obtenir 1 USD, plus il faut de XOF pour
        // obtenir 1 CNY au bout de la chaine : le cout de revient monte.
        BreakEvenResult base = calculator.calculateBreakEven(
                new BigDecimal("1000000"), new BigDecimal("583"), new BigDecimal("6.70"),
                new BigDecimal("0.01"), new BigDecimal("1.50"));
        BreakEvenResult higherRateXofUsd = calculator.calculateBreakEven(
                new BigDecimal("1000000"), new BigDecimal("600"), new BigDecimal("6.70"),
                new BigDecimal("0.01"), new BigDecimal("1.50"));

        assertThat(higherRateXofUsd.breakEvenRate()).isGreaterThan(base.breakEvenRate());
    }
}
