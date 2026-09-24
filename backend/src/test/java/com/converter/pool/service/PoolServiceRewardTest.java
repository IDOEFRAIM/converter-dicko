package com.converter.pool.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Formule de recompense progressive d'une Ruee (voir {@link PoolService#computeReward}) —
 * logique pure, sans contexte Spring ni base (remarque produit #4).
 */
class PoolServiceRewardTest {

    private static final BigDecimal BASE = new BigDecimal("0.5");
    private static final BigDecimal PER_PARTICIPANT = new BigDecimal("0.15");
    private static final BigDecimal PER_MILLION = new BigDecimal("0.10");
    private static final BigDecimal MAX = new BigDecimal("2.0");

    private static BigDecimal reward(int participants, String volumeXof) {
        return PoolService.computeReward(BASE, PER_PARTICIPANT, PER_MILLION, MAX, participants,
                new BigDecimal(volumeXof));
    }

    @ParameterizedTest
    @CsvSource({
            // participants, volumeXof, reductionAttendue
            "1,0,0.500",              // createur seul, rien echange -> base
            "1,999999,0.500",        // moins d'1 M -> aucun bonus volume
            "1,1000000,0.600",       // +1 tranche de 1 M
            "3,0,0.800",             // +2 participants au-dela du premier
            "3,2500000,1.000",       // 0.5 + 2*0.15 + 2*0.10
            "5,4000000,1.500",       // 0.5 + 4*0.15 + 4*0.10
    })
    void growsWithParticipantsAndVolume(int participants, String volumeXof, String expected) {
        assertThat(reward(participants, volumeXof)).isEqualByComparingTo(expected);
    }

    @Test
    void isCappedAtMax() {
        assertThat(reward(50, "100000000")).isEqualByComparingTo("2.000");
    }

    @Test
    void neverNegative_evenWithAbsurdSettings() {
        BigDecimal r = PoolService.computeReward(new BigDecimal("-5"), BigDecimal.ZERO, BigDecimal.ZERO,
                MAX, 10, new BigDecimal("5000000"));
        assertThat(r).isEqualByComparingTo("0.000");
    }

    @Test
    void toleratesNullOrNegativeVolume() {
        assertThat(PoolService.computeReward(BASE, PER_PARTICIPANT, PER_MILLION, MAX, 2, null))
                .isEqualByComparingTo("0.650");
        assertThat(reward(2, "-1")).isEqualByComparingTo("0.650");
    }

    @Test
    void breakdownSeparatesTheTwoAxes() {
        // 3 participants, 2 500 000 XOF echanges : 0.5 base + 2*0.15 + 2*0.10
        var breakdown = PoolService.computeRewardBreakdown(BASE, PER_PARTICIPANT, PER_MILLION, MAX, 3,
                new BigDecimal("2500000"));
        assertThat(breakdown.base()).isEqualByComparingTo("0.500");
        assertThat(breakdown.participantBonus()).isEqualByComparingTo("0.300"); // nombre de personnes
        assertThat(breakdown.volumeBonus()).isEqualByComparingTo("0.200");      // somme echangee
        assertThat(breakdown.total()).isEqualByComparingTo("1.000");
    }

    @Test
    void breakdownTotalStaysCapped() {
        var breakdown = PoolService.computeRewardBreakdown(BASE, PER_PARTICIPANT, PER_MILLION, MAX, 50,
                new BigDecimal("100000000"));
        assertThat(breakdown.total()).isEqualByComparingTo("2.000");
    }
}
