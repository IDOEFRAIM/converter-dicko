package com.converter.rate.engine;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MoneyRoundingTest {

    @Test
    void roundXofUp_roundsAwayFromZeroOnAnyFraction() {
        assertThat(MoneyRounding.roundXofUp(new BigDecimal("100.00"))).isEqualByComparingTo("100");
        assertThat(MoneyRounding.roundXofUp(new BigDecimal("100.01"))).isEqualByComparingTo("101");
        assertThat(MoneyRounding.roundXofUp(new BigDecimal("100.99"))).isEqualByComparingTo("101");
        assertThat(MoneyRounding.roundXofUp(new BigDecimal("2500.025"))).isEqualByComparingTo("2501");
    }

    @Test
    void roundCnyDown_truncatesTowardZero() {
        assertThat(MoneyRounding.roundCnyDown(new BigDecimal("1176.470588"))).isEqualByComparingTo("1176.47");
        assertThat(MoneyRounding.roundCnyDown(new BigDecimal("1176.479999"))).isEqualByComparingTo("1176.47");
        assertThat(MoneyRounding.roundCnyDown(new BigDecimal("1000.00"))).isEqualByComparingTo("1000.00");
    }

    @Test
    void normalizeRate_fixesScaleAtSixDecimals() {
        assertThat(MoneyRounding.normalizeRate(new BigDecimal("86.2750001")))
                .isEqualByComparingTo("86.275000");
        assertThat(MoneyRounding.normalizeRate(new BigDecimal("86.2750005")))
                .isEqualByComparingTo("86.275001");
    }
}
