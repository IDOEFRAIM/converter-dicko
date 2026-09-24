package com.converter.preferredrate.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifie la fenetre de progression T0 / +45min / +90min / fin (2h max)
 * en fabriquant des {@link Instant} explicites -- aucune attente reelle,
 * meme esprit que {@link PreferredRateRequestTest}.
 */
class ExchangeTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void atT0_noProgressAndNoCompletionDue() {
        Exchange exchange = newExchange();

        assertThat(exchange.isProgress45Due(T0)).isFalse();
        assertThat(exchange.isProgress90Due(T0)).isFalse();
        assertThat(exchange.isCompletionDue(T0)).isFalse();
    }

    @Test
    void at45Minutes_progress45IsDue() {
        Exchange exchange = newExchange();
        Instant at45 = T0.plusSeconds(45 * 60);

        assertThat(exchange.isProgress45Due(at45)).isTrue();
        assertThat(exchange.isProgress90Due(at45)).isFalse();
    }

    @Test
    void at45Minutes_afterMarked_noLongerDue() {
        Exchange exchange = newExchange();
        Instant at45 = T0.plusSeconds(45 * 60);
        exchange.markProgress45Sent(at45);

        assertThat(exchange.isProgress45Due(at45)).isFalse();
        assertThat(exchange.isProgress45Due(at45.plusSeconds(60))).isFalse();
    }

    @Test
    void at90Minutes_progress90IsDue() {
        Exchange exchange = newExchange();
        Instant at90 = T0.plusSeconds(90 * 60);

        assertThat(exchange.isProgress90Due(at90)).isTrue();
    }

    @Test
    void at90Minutes_afterMarked_noLongerDue() {
        Exchange exchange = newExchange();
        Instant at90 = T0.plusSeconds(90 * 60);
        exchange.markProgress90Sent(at90);

        assertThat(exchange.isProgress90Due(at90)).isFalse();
    }

    @Test
    void at2Hours_completionIsDue() {
        Exchange exchange = newExchange();
        Instant at2h = T0.plusSeconds(120 * 60);

        assertThat(exchange.isCompletionDue(at2h)).isTrue();
    }

    @Test
    void afterCompletion_nothingIsDueAnymore() {
        Exchange exchange = newExchange();
        Instant at2h = T0.plusSeconds(120 * 60);
        exchange.complete(at2h);

        assertThat(exchange.isCompletionDue(at2h)).isFalse();
        assertThat(exchange.isProgress45Due(at2h)).isFalse();
        assertThat(exchange.isProgress90Due(at2h)).isFalse();
        assertThat(exchange.getStatus()).isEqualTo(ExchangeStatus.COMPLETED);
    }

    private static Exchange newExchange() {
        return new Exchange(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("100000.00"),
                new BigDecimal("85.000000"), new BigDecimal("1176.47"), T0);
    }
}
