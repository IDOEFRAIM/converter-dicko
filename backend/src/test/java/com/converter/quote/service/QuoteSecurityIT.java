package com.converter.quote.service;

import com.converter.common.api.ErrorResponse;
import com.converter.quote.domain.QuoteDirection;
import com.converter.quote.dto.CreateQuoteRequest;
import com.converter.quote.dto.QuoteResponse;
import com.converter.support.AbstractRateQuoteIT;
import com.converter.user.domain.RoleCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Un utilisateur ne peut consulter ou faire transiter que ses propres
 * devis. Un acces au devis d'un autre utilisateur renvoie 404, jamais
 * 403 (meme regle que pour les autres ressources, cf. Phase 2).
 */
class QuoteSecurityIT extends AbstractRateQuoteIT {

    @Test
    void get_anotherUsersQuote_returns404NotForbidden() {
        String admin = adminToken();
        publishRate(admin, "85.000000");

        String owner = tokenFor(createUser(RoleCode.USER));
        QuoteResponse quote = createQuote(owner, new CreateQuoteRequest(QuoteDirection.SEND_XOF,
                new BigDecimal("100000"), null));

        String intruder = tokenFor(createUser(RoleCode.USER));
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(intruder);

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/v1/quotes/" + quote.id(), HttpMethod.GET, new HttpEntity<>(headers), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo("QUOTE_NOT_FOUND");
    }

    @Test
    void accept_anotherUsersQuote_returns404NotForbidden() {
        String admin = adminToken();
        publishRate(admin, "85.000000");

        String owner = tokenFor(createUser(RoleCode.USER));
        QuoteResponse quote = createQuote(owner, new CreateQuoteRequest(QuoteDirection.SEND_XOF,
                new BigDecimal("100000"), null));

        String intruder = tokenFor(createUser(RoleCode.USER));
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(intruder);

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/v1/quotes/" + quote.id() + "/accept", HttpMethod.POST,
                new HttpEntity<>(headers), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo("QUOTE_NOT_FOUND");
    }

    @Test
    void create_withoutToken_returns401() {
        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                "/api/v1/quotes",
                new CreateQuoteRequest(QuoteDirection.SEND_XOF, new BigDecimal("1000"), null),
                ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().code()).isEqualTo("AUTHENTICATION_REQUIRED");
    }
}
