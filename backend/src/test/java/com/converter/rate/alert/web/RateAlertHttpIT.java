package com.converter.rate.alert.web;

import com.converter.common.api.ApiResponse;
import com.converter.common.api.ErrorResponse;
import com.converter.common.api.PageResponse;
import com.converter.rate.alert.domain.RateAlertStatus;
import com.converter.rate.alert.dto.CreateRateAlertRequest;
import com.converter.rate.alert.dto.RateAlertResponse;
import com.converter.support.AbstractRateQuoteIT;
import com.converter.user.domain.RoleCode;
import com.converter.user.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code /api/v1/rate-alerts} : creation, liste, detail, annulation. Authentifie (comme tout
 * {@code /api/v1/**}), jamais anonyme. Ownership : ressource d'autrui -&gt; 404, jamais 403.
 */
class RateAlertHttpIT extends AbstractRateQuoteIT {

    private ResponseEntity<ApiResponse<RateAlertResponse>> createRaw(String token, CreateRateAlertRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange("/api/v1/rate-alerts", HttpMethod.POST, new HttpEntity<>(request, headers),
                new ParameterizedTypeReference<ApiResponse<RateAlertResponse>>() {
                });
    }

    @Test
    void create_authenticatedUser_returns201() {
        User user = createUser(RoleCode.USER);
        ResponseEntity<ApiResponse<RateAlertResponse>> response = createRaw(tokenFor(user),
                new CreateRateAlertRequest("XOF/CNY", null, new BigDecimal("83.50"), null, null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().data().status()).isEqualTo(RateAlertStatus.ACTIVE);
        assertThat(response.getBody().data().targetRate()).isEqualByComparingTo("83.50");
    }

    @Test
    void create_anonymous_returns401() {
        ResponseEntity<String> response = restTemplate.postForEntity("/api/v1/rate-alerts",
                new CreateRateAlertRequest("XOF/CNY", null, new BigDecimal("83.50"), null, null), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void create_nonPositiveTargetRate_returns400() {
        User user = createUser(RoleCode.USER);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenFor(user));
        ResponseEntity<ErrorResponse> response = restTemplate.exchange("/api/v1/rate-alerts", HttpMethod.POST,
                new HttpEntity<>(new CreateRateAlertRequest("XOF/CNY", null, BigDecimal.ZERO, null, null), headers),
                ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void create_unsupportedPair_returns400() {
        User user = createUser(RoleCode.USER);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenFor(user));
        ResponseEntity<ErrorResponse> response = restTemplate.exchange("/api/v1/rate-alerts", HttpMethod.POST,
                new HttpEntity<>(new CreateRateAlertRequest("USD/EUR", null, new BigDecimal("83.50"), null, null),
                        headers),
                ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void get_ownAlert_returns200() {
        User user = createUser(RoleCode.USER);
        RateAlertResponse created = createRaw(tokenFor(user),
                new CreateRateAlertRequest("XOF/CNY", null, new BigDecimal("83.50"), null, null)).getBody().data();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenFor(user));
        ResponseEntity<ApiResponse<RateAlertResponse>> response = restTemplate.exchange(
                "/api/v1/rate-alerts/" + created.id(), HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<ApiResponse<RateAlertResponse>>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().id()).isEqualTo(created.id());
    }

    @Test
    void get_anotherUsersAlert_returns404() {
        User owner = createUser(RoleCode.USER);
        User stranger = createUser(RoleCode.USER);
        RateAlertResponse created = createRaw(tokenFor(owner),
                new CreateRateAlertRequest("XOF/CNY", null, new BigDecimal("83.50"), null, null)).getBody().data();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenFor(stranger));
        ResponseEntity<ErrorResponse> response = restTemplate.exchange("/api/v1/rate-alerts/" + created.id(),
                HttpMethod.GET, new HttpEntity<>(headers), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo("RATE_ALERT_NOT_FOUND");
    }

    @Test
    void get_nonExistentAlert_returns404() {
        User user = createUser(RoleCode.USER);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenFor(user));
        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/v1/rate-alerts/" + UUID.randomUUID(), HttpMethod.GET, new HttpEntity<>(headers),
                ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void cancel_ownAlert_returns200AndCancels() {
        User user = createUser(RoleCode.USER);
        RateAlertResponse created = createRaw(tokenFor(user),
                new CreateRateAlertRequest("XOF/CNY", null, new BigDecimal("83.50"), null, null)).getBody().data();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenFor(user));
        ResponseEntity<ApiResponse<RateAlertResponse>> response = restTemplate.exchange(
                "/api/v1/rate-alerts/" + created.id() + "/cancel", HttpMethod.POST, new HttpEntity<>(headers),
                new ParameterizedTypeReference<ApiResponse<RateAlertResponse>>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().status()).isEqualTo(RateAlertStatus.CANCELLED);
    }

    @Test
    void cancel_anotherUsersAlert_returns404_neverCancelled() {
        User owner = createUser(RoleCode.USER);
        User stranger = createUser(RoleCode.USER);
        RateAlertResponse created = createRaw(tokenFor(owner),
                new CreateRateAlertRequest("XOF/CNY", null, new BigDecimal("83.50"), null, null)).getBody().data();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenFor(stranger));
        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/v1/rate-alerts/" + created.id() + "/cancel", HttpMethod.POST, new HttpEntity<>(headers),
                ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        HttpHeaders ownerHeaders = new HttpHeaders();
        ownerHeaders.setBearerAuth(tokenFor(owner));
        ResponseEntity<ApiResponse<RateAlertResponse>> stillActive = restTemplate.exchange(
                "/api/v1/rate-alerts/" + created.id(), HttpMethod.GET, new HttpEntity<>(ownerHeaders),
                new ParameterizedTypeReference<ApiResponse<RateAlertResponse>>() {
                });
        assertThat(stillActive.getBody().data().status()).isEqualTo(RateAlertStatus.ACTIVE);
    }

    @Test
    void list_returnsOnlyOwnAlerts_paginated() {
        User user = createUser(RoleCode.USER);
        User other = createUser(RoleCode.USER);
        for (int i = 0; i < 3; i++) {
            createRaw(tokenFor(user), new CreateRateAlertRequest("XOF/CNY", null,
                    new BigDecimal("80.0" + i), null, null));
        }
        createRaw(tokenFor(other), new CreateRateAlertRequest("XOF/CNY", null, new BigDecimal("90.00"), null, null));

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenFor(user));
        ResponseEntity<ApiResponse<PageResponse<RateAlertResponse>>> response = restTemplate.exchange(
                "/api/v1/rate-alerts?page=0&size=2", HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<ApiResponse<PageResponse<RateAlertResponse>>>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().content()).hasSize(2);
        assertThat(response.getBody().data().totalElements()).isEqualTo(3);
    }

    @Test
    void list_filtersByStatus() {
        User user = createUser(RoleCode.USER);
        RateAlertResponse toCancel = createRaw(tokenFor(user),
                new CreateRateAlertRequest("XOF/CNY", null, new BigDecimal("83.50"), null, null)).getBody().data();
        createRaw(tokenFor(user), new CreateRateAlertRequest("XOF/CNY", null, new BigDecimal("70.00"), null, null));

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenFor(user));
        restTemplate.exchange("/api/v1/rate-alerts/" + toCancel.id() + "/cancel", HttpMethod.POST,
                new HttpEntity<>(headers), ApiResponse.class);

        ResponseEntity<ApiResponse<PageResponse<RateAlertResponse>>> response = restTemplate.exchange(
                "/api/v1/rate-alerts?status=CANCELLED", HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<ApiResponse<PageResponse<RateAlertResponse>>>() {
                });

        assertThat(response.getBody().data().content()).hasSize(1);
        assertThat(response.getBody().data().content().get(0).status()).isEqualTo(RateAlertStatus.CANCELLED);
    }
}
