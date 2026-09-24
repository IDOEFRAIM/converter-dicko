package com.converter.order.service;

import com.converter.common.api.ApiResponse;
import com.converter.order.domain.OrderStatus;
import com.converter.order.dto.OrderDetailResponse;
import com.converter.quote.dto.QuoteResponse;
import com.converter.settings.domain.SettingKey;
import com.converter.support.AbstractOrderPipelineIT;
import com.converter.treasury.domain.Currency;
import com.converter.user.domain.RoleCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Passe 2, P2-1 : un ordre en attente de paiement dont l'echeance est depassee doit etre expire
 * et sa reservation CNY liberee — sinon la liquidite reste bloquee indefiniment. Les methodes
 * acceptent l'instant de reference en parametre : aucun besoin d'attendre la fenetre reelle.
 */
class OrderExpirationServiceIT extends AbstractOrderPipelineIT {

    @Autowired
    private OrderExpirationService orderExpirationService;

    @Autowired
    private OrderService orderService;

    @Test
    void overdueAwaitingPaymentOrder_isExpired_andItsCnyReservationReleased_idempotently() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));
        QuoteResponse quote = createAcceptedQuote(user, "100000");
        OrderDetailResponse order = createOrder(user, quote.id(), alipayBeneficiary());
        assertThat(order.status()).isEqualTo(OrderStatus.AWAITING_PAYMENT);
        assertThat(order.paymentDeadlineAt()).isNotNull();

        BigDecimal reservedBefore = treasurySnapshot(admin, Currency.CNY).reservedBalance();
        Instant afterDeadline = order.paymentDeadlineAt().plus(1, ChronoUnit.MINUTES);

        // Appel cible (pas de scan global) : deterministe, sans effet de bord sur d'autres tests.
        boolean expired = orderService.expireIfOverdue(order.id(), afterDeadline);
        assertThat(expired).isTrue();

        assertThat(getOrder(user, order.id()).status()).isEqualTo(OrderStatus.EXPIRED);
        BigDecimal reservedAfter = treasurySnapshot(admin, Currency.CNY).reservedBalance();
        assertThat(reservedBefore.subtract(reservedAfter)).isEqualByComparingTo(order.amountCny());

        // Idempotent : un second appel (meme instant, ou plus tard) ne retouche rien.
        assertThat(orderService.expireIfOverdue(order.id(), afterDeadline.plus(1, ChronoUnit.HOURS))).isFalse();
        assertThat(getOrder(user, order.id()).status()).isEqualTo(OrderStatus.EXPIRED);
    }

    @Test
    void orderStillWithinItsPaymentWindow_isNotExpired() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));
        QuoteResponse quote = createAcceptedQuote(user, "50000");
        OrderDetailResponse order = createOrder(user, quote.id(), alipayBeneficiary());

        assertThat(orderService.expireIfOverdue(order.id(),
                order.paymentDeadlineAt().minus(1, ChronoUnit.MINUTES))).isFalse();
        assertThat(getOrder(user, order.id()).status()).isEqualTo(OrderStatus.AWAITING_PAYMENT);
    }

    @Test
    void scanPath_respectsOrderAutoExpireEnabledFlag_andExpiresOurOverdueOrder() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));
        QuoteResponse quote = createAcceptedQuote(user, "50000");
        OrderDetailResponse order = createOrder(user, quote.id(), alipayBeneficiary());
        Instant afterDeadline = order.paymentDeadlineAt().plus(1, ChronoUnit.MINUTES);

        // Drapeau desactive : le scan ne fait rien, notre ordre reste AWAITING_PAYMENT.
        updateSetting(admin, SettingKey.ORDER_AUTO_EXPIRE_ENABLED, "false");
        try {
            assertThat(orderExpirationService.expireOverdue(afterDeadline)).isZero();
            assertThat(getOrder(user, order.id()).status()).isEqualTo(OrderStatus.AWAITING_PAYMENT);
        } finally {
            updateSetting(admin, SettingKey.ORDER_AUTO_EXPIRE_ENABLED, "true");
        }

        // Drapeau reactive : le scan expire (au moins) notre ordre.
        int expired = orderExpirationService.expireOverdue(afterDeadline);
        assertThat(expired).isGreaterThanOrEqualTo(1);
        assertThat(getOrder(user, order.id()).status()).isEqualTo(OrderStatus.EXPIRED);
    }

    private OrderDetailResponse getOrder(String userToken, UUID orderId) {
        ResponseEntity<ApiResponse<OrderDetailResponse>> response = restTemplate.exchange(
                "/api/v1/orders/" + orderId, HttpMethod.GET, new HttpEntity<>(auth(userToken)),
                new ParameterizedTypeReference<>() {
                });
        return response.getBody().data();
    }

    private void updateSetting(String adminToken, SettingKey key, String value) {
        org.springframework.http.HttpHeaders headers = auth(adminToken);
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        restTemplate.exchange("/api/admin/settings/" + key.name(), HttpMethod.PUT,
                new HttpEntity<>("{\"value\":\"" + value + "\"}", headers), String.class);
    }
}
