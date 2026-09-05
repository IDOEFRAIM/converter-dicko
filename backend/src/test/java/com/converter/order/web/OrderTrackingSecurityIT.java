package com.converter.order.web;

import com.converter.common.api.ApiResponse;
import com.converter.common.api.ErrorResponse;
import com.converter.order.dto.OrderDetailResponse;
import com.converter.order.dto.OrderTrackingResponse;
import com.converter.quote.dto.QuoteResponse;
import com.converter.support.AbstractOrderPipelineIT;
import com.converter.user.domain.RoleCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code GET /api/v1/orders/{id}/tracking} — isolation stricte, meme convention que partout
 * ailleurs (404, jamais 403, sur la ressource d'un autre utilisateur).
 */
class OrderTrackingSecurityIT extends AbstractOrderPipelineIT {

    @Test
    void tracking_authenticatedOwner_returns200() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));
        QuoteResponse quote = createAcceptedQuote(user, "100000");
        OrderDetailResponse order = createOrder(user, quote.id(), alipayBeneficiary());

        ResponseEntity<ApiResponse<OrderTrackingResponse>> response = trackingRaw(user, order.id());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().orderId()).isEqualTo(order.id());
    }

    @Test
    void tracking_anonymous_returns401() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));
        QuoteResponse quote = createAcceptedQuote(user, "100000");
        OrderDetailResponse order = createOrder(user, quote.id(), alipayBeneficiary());

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/orders/" + order.id() + "/tracking", HttpMethod.GET, null, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void tracking_anotherUsersOrder_returns404NotForbidden() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        String owner = tokenFor(createUser(RoleCode.USER));
        QuoteResponse quote = createAcceptedQuote(owner, "100000");
        OrderDetailResponse order = createOrder(owner, quote.id(), alipayBeneficiary());
        String intruder = tokenFor(createUser(RoleCode.USER));

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/v1/orders/" + order.id() + "/tracking", HttpMethod.GET,
                new HttpEntity<>(auth(intruder)), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo("ORDER_NOT_FOUND");
    }

    @Test
    void tracking_unknownOrder_returns404() {
        String user = tokenFor(createUser(RoleCode.USER));

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/v1/orders/" + UUID.randomUUID() + "/tracking", HttpMethod.GET,
                new HttpEntity<>(auth(user)), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo("ORDER_NOT_FOUND");
    }
}
