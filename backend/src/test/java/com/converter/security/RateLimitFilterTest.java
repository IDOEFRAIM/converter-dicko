package com.converter.security;

import com.converter.config.props.AbuseProtectionProperties;
import com.converter.config.props.LoginProtectionProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test unitaire pur (aucun contexte Spring, aucun conteneur) : verifie la
 * logique de {@link RateLimitFilter} directement, en dehors de la suite
 * d'integration partagee. Necessaire car le contexte Spring et donc le
 * cache Caffeine de ce filtre sont partages par toute la suite IT (voir le
 * commentaire dans {@code application-test.yml}) — un test qui epuiserait
 * volontairement un seuil via de vrais appels HTTP polluerait les tests
 * suivants de la meme JVM.
 */
class RateLimitFilterTest {

    private static final String REGISTER_PATH = "/api/auth/register";
    private static final String QUOTE_PATH = "/api/v1/quotes";
    private static final String PAYMENT_PATH = "/api/v1/orders/abc-123/payments";
    private static final String UNRELATED_PATH = "/api/settings/public";
    private static final String CLIENT_IP = "203.0.113.7";

    private RateLimitFilter newFilter(int registerMax, int writeMax) {
        LoginProtectionProperties loginProperties = new LoginProtectionProperties(5, 15);
        AbuseProtectionProperties abuseProperties =
                new AbuseProtectionProperties(registerMax, 15, writeMax, 5);
        SecurityResponseWriter responseWriter =
                new SecurityResponseWriter(new ObjectMapper().registerModule(new JavaTimeModule()));
        return new RateLimitFilter(loginProperties, abuseProperties, responseWriter);
    }

    private static MockHttpServletRequest postFrom(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr(CLIENT_IP);
        return request;
    }

    private static final FilterChain PASS_THROUGH = (req, res) ->
            ((MockHttpServletResponse) res).setStatus(200);

    @Test
    void registerAttempt_underLimit_passesThrough() throws Exception {
        RateLimitFilter filter = newFilter(2, 2);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(postFrom(REGISTER_PATH), response, PASS_THROUGH);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void registerAttempt_exceedingLimit_isRejectedWith429EquivalentErrorCode() throws Exception {
        RateLimitFilter filter = newFilter(2, 2);

        filter.doFilter(postFrom(REGISTER_PATH), new MockHttpServletResponse(), PASS_THROUGH);
        filter.doFilter(postFrom(REGISTER_PATH), new MockHttpServletResponse(), PASS_THROUGH);
        MockHttpServletResponse third = new MockHttpServletResponse();
        filter.doFilter(postFrom(REGISTER_PATH), third, PASS_THROUGH);

        assertThat(third.getContentAsString()).contains("TOO_MANY_ATTEMPTS");
    }

    @Test
    void writeAttempt_volumeLimit_doesNotResetOnSuccess() throws Exception {
        // Contrairement a login-protection, une reussite ne doit PAS remettre
        // le compteur a zero : c'est une limite de volume, pas de tentatives.
        RateLimitFilter filter = newFilter(2, 2);

        filter.doFilter(postFrom(QUOTE_PATH), new MockHttpServletResponse(), PASS_THROUGH);
        filter.doFilter(postFrom(QUOTE_PATH), new MockHttpServletResponse(), PASS_THROUGH);
        MockHttpServletResponse third = new MockHttpServletResponse();
        filter.doFilter(postFrom(QUOTE_PATH), third, PASS_THROUGH);

        assertThat(third.getContentAsString()).contains("TOO_MANY_ATTEMPTS");
    }

    @Test
    void writeAttempt_onPaymentSubmissionPath_isCountedByThePatternMatch() throws Exception {
        RateLimitFilter filter = newFilter(2, 1);

        filter.doFilter(postFrom(PAYMENT_PATH), new MockHttpServletResponse(), PASS_THROUGH);
        MockHttpServletResponse second = new MockHttpServletResponse();
        filter.doFilter(postFrom(PAYMENT_PATH), second, PASS_THROUGH);

        assertThat(second.getContentAsString()).contains("TOO_MANY_ATTEMPTS");
    }

    @Test
    void unrelatedEndpoint_isNeverCountedOrBlocked() throws Exception {
        RateLimitFilter filter = newFilter(1, 1);

        // Depasse largement les seuils configures : un endpoint hors perimetre
        // ne doit jamais etre compte, quel que soit le volume.
        for (int i = 0; i < 5; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(new MockHttpServletRequest("GET", UNRELATED_PATH), response, PASS_THROUGH);
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    @Test
    void writeAttemptsFromDifferentIps_areCountedSeparately() throws Exception {
        RateLimitFilter filter = newFilter(2, 1);

        MockHttpServletRequest firstIp = postFrom(QUOTE_PATH);
        filter.doFilter(firstIp, new MockHttpServletResponse(), PASS_THROUGH);

        MockHttpServletRequest secondIp = new MockHttpServletRequest("POST", QUOTE_PATH);
        secondIp.setRemoteAddr("198.51.100.9");
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        filter.doFilter(secondIp, secondResponse, PASS_THROUGH);

        assertThat(secondResponse.getStatus()).isEqualTo(200);
    }
}
