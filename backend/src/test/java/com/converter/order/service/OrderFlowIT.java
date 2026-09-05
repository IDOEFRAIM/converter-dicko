package com.converter.order.service;

import com.converter.common.api.ApiResponse;
import com.converter.common.api.ErrorResponse;
import com.converter.order.domain.OrderStatus;
import com.converter.order.dto.OrderDetailResponse;
import com.converter.quote.domain.QuoteDirection;
import com.converter.quote.dto.CreateQuoteRequest;
import com.converter.quote.dto.QuoteResponse;
import com.converter.support.AbstractOrderPipelineIT;
import com.converter.treasury.domain.Currency;
import com.converter.user.domain.RoleCode;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class OrderFlowIT extends AbstractOrderPipelineIT {

    @Test
    void createOrder_fromAcceptedQuote_reservesTreasuryAndReturnsAwaitingPayment() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));

        QuoteResponse quote = createAcceptedQuote(user, "100000");
        BigDecimal cnyBefore = treasurySnapshot(admin, Currency.CNY).available();

        OrderDetailResponse order = createOrder(user, quote.id(), alipayBeneficiary());

        assertThat(order.status()).isEqualTo(OrderStatus.AWAITING_PAYMENT);
        assertThat(order.amountXof()).isEqualByComparingTo(quote.amountXof());
        assertThat(order.amountCny()).isEqualByComparingTo(quote.amountCny());
        assertThat(order.beneficiary().fullName()).isEqualTo("Zhang San");
        assertThat(order.statusHistory()).hasSize(1);
        assertThat(order.statusHistory().get(0).toStatus()).isEqualTo(OrderStatus.AWAITING_PAYMENT);

        BigDecimal cnyAfter = treasurySnapshot(admin, Currency.CNY).available();
        assertThat(cnyBefore.subtract(cnyAfter)).isEqualByComparingTo(quote.amountCny());
    }

    @Test
    void createOrder_fromAlreadyUsedQuote_returnsConflict() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));
        QuoteResponse quote = createAcceptedQuote(user, "50000");
        createOrder(user, quote.id(), alipayBeneficiary());

        ResponseEntity<ErrorResponse> second = restTemplate.exchange("/api/v1/orders", HttpMethod.POST,
                new HttpEntity<>(new com.converter.order.dto.CreateOrderRequest(quote.id(), alipayBeneficiary(), null, null, null, null),
                        auth(user)),
                ErrorResponse.class);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(second.getBody().code()).isEqualTo("QUOTE_ALREADY_USED");
    }

    @Test
    void createOrder_fromNonAcceptedQuote_returnsConflict() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));
        // Devis cree mais jamais accepte (encore ACTIVE).
        QuoteResponse quote = createQuote(user, new CreateQuoteRequest(QuoteDirection.SEND_XOF,
                new BigDecimal("50000"), null));

        ResponseEntity<ErrorResponse> response = restTemplate.exchange("/api/v1/orders", HttpMethod.POST,
                new HttpEntity<>(new com.converter.order.dto.CreateOrderRequest(quote.id(), alipayBeneficiary(), null, null, null, null),
                        auth(user)),
                ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).isEqualTo("QUOTE_NOT_ACCEPTED");
    }

    @Test
    void createOrder_fromAnotherUsersQuote_returns404NotForbidden() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        String owner = tokenFor(createUser(RoleCode.USER));
        QuoteResponse quote = createAcceptedQuote(owner, "50000");

        String intruder = tokenFor(createUser(RoleCode.USER));
        ResponseEntity<ErrorResponse> response = restTemplate.exchange("/api/v1/orders", HttpMethod.POST,
                new HttpEntity<>(new com.converter.order.dto.CreateOrderRequest(quote.id(), alipayBeneficiary(), null, null, null, null),
                        auth(intruder)),
                ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo("QUOTE_NOT_FOUND");
    }

    @Test
    void getOrder_ofAnotherUser_returns404NotForbidden() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        String owner = tokenFor(createUser(RoleCode.USER));
        QuoteResponse quote = createAcceptedQuote(owner, "50000");
        OrderDetailResponse order = createOrder(owner, quote.id(), alipayBeneficiary());

        String intruder = tokenFor(createUser(RoleCode.USER));
        ResponseEntity<ErrorResponse> response = restTemplate.exchange("/api/v1/orders/" + order.id(),
                HttpMethod.GET, new HttpEntity<>(auth(intruder)), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo("ORDER_NOT_FOUND");
    }

    @Test
    void cancelOrder_releasesTreasuryReservation() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));
        QuoteResponse quote = createAcceptedQuote(user, "100000");
        OrderDetailResponse order = createOrder(user, quote.id(), alipayBeneficiary());

        BigDecimal availableBeforeCancel = treasurySnapshot(admin, Currency.CNY).available();

        ResponseEntity<ApiResponse<OrderDetailResponse>> cancelled = restTemplate.exchange(
                "/api/v1/orders/" + order.id() + "/cancel", HttpMethod.POST,
                new HttpEntity<>(new com.converter.order.dto.CancelOrderRequest("changement d'avis"), auth(user)),
                new ParameterizedTypeReference<ApiResponse<OrderDetailResponse>>() {
                });

        assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cancelled.getBody().data().status()).isEqualTo(OrderStatus.CANCELLED);

        BigDecimal availableAfterCancel = treasurySnapshot(admin, Currency.CNY).available();
        assertThat(availableAfterCancel.subtract(availableBeforeCancel)).isEqualByComparingTo(order.amountCny());
    }

    @Test
    void createOrder_withInsufficientTreasury_returnsConflictAndDoesNotCreateOrder() {
        // Le conteneur Postgres est partage par toute la suite : le solde
        // CNY disponible n'est jamais suppose nul, il est mesure au
        // moment du test. Le montant demande est construit pour depasser
        // ce disponible d'une marge large, quel que soit l'etat deja
        // accumule par les tests executes avant celui-ci.
        String admin = adminToken();
        publishRate(admin, "85.000000");
        resetMarginToZero();
        // Le montant requis pour ce test depasse largement le seuil KYC : l'utilisateur doit
        // etre verifie au prealable, sinon KYC_VERIFICATION_REQUIRED masquerait le cas teste ici.
        com.converter.user.domain.User userEntity = verifyKyc(createUser(RoleCode.USER));
        String user = tokenFor(userEntity);

        BigDecimal availableCny = treasurySnapshot(admin, Currency.CNY).available();
        BigDecimal requiredXof = availableCny.add(new BigDecimal("500000")).multiply(new BigDecimal("85"))
                .setScale(0, java.math.RoundingMode.UP);
        // Le plafond par defaut (2 000 000, seed V4) serait atteint avant
        // meme de tester l'insuffisance de tresorerie : il est releve
        // pour ce test precis, en cause a effet connu.
        settingsService.update(com.converter.settings.domain.SettingKey.MAX_ORDER_AMOUNT_CFA,
                requiredXof.toPlainString(), createUser(RoleCode.ADMIN).getId());

        QuoteResponse quote = createAcceptedQuote(user, requiredXof.toPlainString());

        ResponseEntity<ErrorResponse> response = restTemplate.exchange("/api/v1/orders", HttpMethod.POST,
                new HttpEntity<>(new com.converter.order.dto.CreateOrderRequest(quote.id(), alipayBeneficiary(), null, null, null, null),
                        auth(user)),
                ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).isEqualTo("INSUFFICIENT_TREASURY");
    }

    @Test
    void feasibility_beforeBeneficiary_reflectsCnyLiquidity() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));
        QuoteResponse quote = createAcceptedQuote(user, "100000");

        var ok = restTemplate.exchange(
                "/api/v1/orders/feasibility?quoteId=" + quote.id(), HttpMethod.GET,
                new HttpEntity<>(auth(user)),
                new ParameterizedTypeReference<ApiResponse<com.converter.order.dto.OrderFeasibilityResponse>>() {
                });
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ok.getBody().data().quoteId()).isEqualTo(quote.id());
        assertThat(ok.getBody().data().amountCny()).isEqualByComparingTo(quote.amountCny());
        assertThat(ok.getBody().data().settlementReservationEnabled()).isTrue();
        assertThat(ok.getBody().data().sufficientLiquidity()).isTrue();

        // Devis d'un autre utilisateur -> 404, jamais 403, jamais de fuite.
        String other = tokenFor(createUser(RoleCode.USER));
        var forbidden = restTemplate.exchange(
                "/api/v1/orders/feasibility?quoteId=" + quote.id(), HttpMethod.GET,
                new HttpEntity<>(auth(other)), ErrorResponse.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void feasibility_isFalse_whenCnyLiquidityCannotCoverAmount() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        resetMarginToZero();
        String user = tokenFor(createUser(RoleCode.USER));

        BigDecimal availableCny = treasurySnapshot(admin, Currency.CNY).available();
        BigDecimal requiredXof = availableCny.add(new BigDecimal("500000")).multiply(new BigDecimal("85"))
                .setScale(0, java.math.RoundingMode.UP);
        settingsService.update(com.converter.settings.domain.SettingKey.MAX_ORDER_AMOUNT_CFA,
                requiredXof.toPlainString(), createUser(RoleCode.ADMIN).getId());
        QuoteResponse quote = createAcceptedQuote(user, requiredXof.toPlainString());

        var response = restTemplate.exchange(
                "/api/v1/orders/feasibility?quoteId=" + quote.id(), HttpMethod.GET,
                new HttpEntity<>(auth(user)),
                new ParameterizedTypeReference<ApiResponse<com.converter.order.dto.OrderFeasibilityResponse>>() {
                });
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().sufficientLiquidity()).isFalse();
    }

    @Test
    void createOrder_belowMinimumAmount_returnsBadRequest() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));
        // MIN_ORDER_AMOUNT_CFA = 10 000 (seed V4) : 1 000 XOF est sous le plancher.
        QuoteResponse quote = createAcceptedQuote(user, "1000");

        ResponseEntity<ErrorResponse> response = restTemplate.exchange("/api/v1/orders", HttpMethod.POST,
                new HttpEntity<>(new com.converter.order.dto.CreateOrderRequest(quote.id(), alipayBeneficiary(), null, null, null, null),
                        auth(user)),
                ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("ORDER_AMOUNT_OUT_OF_RANGE");
    }
}
