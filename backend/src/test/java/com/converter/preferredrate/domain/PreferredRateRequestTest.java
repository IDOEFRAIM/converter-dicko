package com.converter.preferredrate.domain;

import com.converter.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test unitaire pur (sans Spring ni base de donnees), meme style que
 * {@code QuoteTest} : les transitions temporelles se verifient en
 * fabriquant des {@link Instant} explicites, jamais en attendant un
 * vrai delai -- c'est ce qui rend le test deterministe.
 */
class PreferredRateRequestTest {

    private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant EXPIRES = CREATED.plus(3, ChronoUnit.DAYS);

    @Test
    void isPastDeadline_beforeExpiry_isFalse() {
        PreferredRateRequest request = newRequest();
        assertThat(request.isPastDeadline(EXPIRES.minusSeconds(1))).isFalse();
    }

    @Test
    void isPastDeadline_exactlyAtExpiry_isTrue() {
        PreferredRateRequest request = newRequest();
        assertThat(request.isPastDeadline(EXPIRES)).isTrue();
    }

    @Test
    void isPastDeadline_afterExpiry_isTrue() {
        PreferredRateRequest request = newRequest();
        assertThat(request.isPastDeadline(EXPIRES.plusSeconds(1))).isTrue();
    }

    @Test
    void execute_setsStatusAndAchievedRate() {
        PreferredRateRequest request = newRequest();
        UUID exchangeId = UUID.randomUUID();

        request.execute(CREATED.plusSeconds(3600), new BigDecimal("84.500000"), exchangeId);

        assertThat(request.getStatus()).isEqualTo(PreferredRateStatus.EXECUTED);
        assertThat(request.getAchievedRate()).isEqualByComparingTo("84.500000");
        assertThat(request.getExchangeId()).isEqualTo(exchangeId);
        assertThat(request.getExecutedAt()).isEqualTo(CREATED.plusSeconds(3600));
    }

    @Test
    void expire_setsStatusExpired() {
        PreferredRateRequest request = newRequest();

        request.expire(EXPIRES);

        assertThat(request.getStatus()).isEqualTo(PreferredRateStatus.EXPIRED);
        assertThat(request.getExpiredAt()).isEqualTo(EXPIRES);
    }

    @Test
    void cancel_setsStatusCancelled() {
        PreferredRateRequest request = newRequest();

        request.cancel(CREATED.plusSeconds(60));

        assertThat(request.getStatus()).isEqualTo(PreferredRateStatus.CANCELLED);
    }

    /**
     * Une demande deja executee, expiree ou annulee ne peut plus subir
     * aucune transition -- garantit qu'un ancien taux atteint ne peut
     * jamais etre "rejoue" pour re-executer la meme demande.
     */
    @Test
    void execute_onAlreadyExecutedRequest_throws() {
        PreferredRateRequest request = newRequest();
        request.execute(CREATED.plusSeconds(60), new BigDecimal("84.500000"), UUID.randomUUID());

        assertThatThrownBy(() -> request.execute(CREATED.plusSeconds(120), new BigDecimal("80.000000"), UUID.randomUUID()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void expire_onAlreadyExpiredRequest_throws() {
        PreferredRateRequest request = newRequest();
        request.expire(EXPIRES);

        assertThatThrownBy(() -> request.expire(EXPIRES.plusSeconds(1)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void cancel_onAlreadyExecutedRequest_throws() {
        PreferredRateRequest request = newRequest();
        request.execute(CREATED.plusSeconds(60), new BigDecimal("84.500000"), UUID.randomUUID());

        assertThatThrownBy(() -> request.cancel(CREATED.plusSeconds(120)))
                .isInstanceOf(BusinessException.class);
    }

    private static PreferredRateRequest newRequest() {
        return new PreferredRateRequest(UUID.randomUUID(), PreferredRateDirection.XOF_TO_CNY,
                new BigDecimal("100000.00"), new BigDecimal("85.000000"), CREATED, EXPIRES);
    }
}
