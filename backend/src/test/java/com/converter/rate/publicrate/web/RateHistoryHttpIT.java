package com.converter.rate.publicrate.web;

import com.converter.common.api.ApiResponse;
import com.converter.common.api.ErrorResponse;
import com.converter.common.api.PageResponse;
import com.converter.rate.publicrate.dto.PublicRateHistoryEntry;
import com.converter.support.AbstractRateQuoteIT;
import com.converter.user.domain.RoleCode;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code GET /api/v1/rates/history} — authentifie (pas anonyme, voir {@code RateHistoryController}),
 * jamais admin-only. Confidentialite : verifie que la reponse ne contient jamais de champ interne.
 */
class RateHistoryHttpIT extends AbstractRateQuoteIT {

    private ResponseEntity<ApiResponse<PageResponse<PublicRateHistoryEntry>>> historyRaw(
            String userToken, String query) {
        HttpHeaders headers = new HttpHeaders();
        if (userToken != null) {
            headers.setBearerAuth(userToken);
        }
        String url = "/api/v1/rates/history" + (query == null ? "" : "?" + query);
        return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<ApiResponse<PageResponse<PublicRateHistoryEntry>>>() {
                });
    }

    @Test
    void history_authenticatedUser_returns200() {
        resetMarginToZero();
        String admin = adminToken();
        publishCostRate(admin, "85.000000");
        String user = tokenFor(createUser(RoleCode.USER));

        ResponseEntity<ApiResponse<PageResponse<PublicRateHistoryEntry>>> response =
                historyRaw(user, "pair=XOF/CNY&page=0&size=20");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().content()).isNotEmpty();
        assertThat(response.getBody().data().content().get(0).customerRate()).isNotNull();
    }

    @Test
    void history_anonymous_returns401() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/rates/history", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void history_invalidPair_returns400() {
        String user = tokenFor(createUser(RoleCode.USER));

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(user);
        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/v1/rates/history?pair=NOT_A_PAIR", HttpMethod.GET,
                new HttpEntity<>(headers), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void history_defaultsToXofCnyPair_whenPairOmitted() {
        resetMarginToZero();
        String admin = adminToken();
        publishCostRate(admin, "85.000000");
        String user = tokenFor(createUser(RoleCode.USER));

        ResponseEntity<ApiResponse<PageResponse<PublicRateHistoryEntry>>> response = historyRaw(user, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().content()).allSatisfy(
                entry -> assertThat(entry.currencyPair()).isEqualTo("XOF/CNY"));
    }

    @Test
    void history_isPaginated_respectsSizeParameter() {
        resetMarginToZero();
        String admin = adminToken();
        for (int i = 0; i < 3; i++) {
            publishCostRate(admin, "90.00000" + i);
        }
        String user = tokenFor(createUser(RoleCode.USER));

        ResponseEntity<ApiResponse<PageResponse<PublicRateHistoryEntry>>> response =
                historyRaw(user, "pair=XOF/CNY&page=0&size=1");

        assertThat(response.getBody().data().content()).hasSize(1);
        assertThat(response.getBody().data().size()).isEqualTo(1);
    }

    @Test
    void history_isOrderedNewestFirst_regardlessOfAnyClientSortParameter() {
        resetMarginToZero();
        String admin = adminToken();
        publishCostRate(admin, "91.000000");
        publishCostRate(admin, "92.000000");
        String user = tokenFor(createUser(RoleCode.USER));

        // Un tri client (ex. ?sort=customerRate,asc), s'il etait accepte par erreur, ne doit
        // jamais changer le contrat fixe recordedAt DESC.
        ResponseEntity<ApiResponse<PageResponse<PublicRateHistoryEntry>>> response =
                historyRaw(user, "pair=XOF/CNY&page=0&size=2&sort=customerRate,asc");

        assertThat(response.getBody().data().content().get(0).customerRate())
                .isEqualByComparingTo(new BigDecimal("92.000000"));
    }

    @Test
    void publicEntry_neverExposesInternalFieldNamesInJson() {
        resetMarginToZero();
        String admin = adminToken();
        publishCostRate(admin, "85.000000");
        String user = tokenFor(createUser(RoleCode.USER));

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(user);
        ResponseEntity<String> raw = restTemplate.exchange("/api/v1/rates/history?pair=XOF/CNY",
                HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(raw.getBody()).doesNotContain("breakEvenRate", "marginPercentage", "feePercentage",
                "fixedFeeXof", "costConfigurationId", "provider");
    }
}
