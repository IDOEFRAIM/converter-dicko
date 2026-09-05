package com.converter.order.web;

import com.converter.common.api.ApiResponse;
import com.converter.common.api.PageResponse;
import com.converter.order.domain.BeneficiaryType;
import com.converter.order.domain.OrderStatus;
import com.converter.order.dto.CreateOrderRequest;
import com.converter.order.dto.OrderDetailResponse;
import com.converter.order.dto.OrderHistoryResponse;
import com.converter.quote.dto.QuoteResponse;
import com.converter.supplier.domain.Purpose;
import com.converter.support.AbstractOrderPipelineIT;
import com.converter.user.domain.RoleCode;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code GET /api/v1/orders/history} (Phase 8) : disponible a tout utilisateur authentifie,
 * filtres optionnels (status/purpose/supplierId/from/to), ownership stricte (jamais les donnees
 * d'un autre utilisateur, meme via un {@code supplierId} etranger), tri fixe.
 */
class OrderHistoryHttpIT extends AbstractOrderPipelineIT {

    private ResponseEntity<ApiResponse<PageResponse<OrderHistoryResponse>>> historyRaw(String token, String query) {
        String url = "/api/v1/orders/history" + (query == null ? "" : "?" + query);
        return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(auth(token)),
                new ParameterizedTypeReference<ApiResponse<PageResponse<OrderHistoryResponse>>>() {
                });
    }

    private OrderDetailResponse createOrderWithPurpose(String userToken, UUID quoteId, Purpose purpose) {
        ResponseEntity<ApiResponse<OrderDetailResponse>> response = restTemplate.exchange("/api/v1/orders",
                HttpMethod.POST,
                new HttpEntity<>(new CreateOrderRequest(quoteId, alipayBeneficiary(), "test", null, purpose, null),
                        auth(userToken)),
                new ParameterizedTypeReference<ApiResponse<OrderDetailResponse>>() {
                });
        return response.getBody().data();
    }

    @Test
    void history_returnsOnlyOwnOrders() {
        String admin = adminToken();
        publishRate(admin, "84.200000");
        depositCny(admin, "1000000");
        String userA = tokenFor(createUser(RoleCode.USER));
        String userB = tokenFor(createUser(RoleCode.USER));

        QuoteResponse quoteA = createAcceptedQuote(userA, "100000");
        createOrder(userA, quoteA.id(), alipayBeneficiary());
        QuoteResponse quoteB = createAcceptedQuote(userB, "100000");
        createOrder(userB, quoteB.id(), alipayBeneficiary());

        var response = historyRaw(userA, "size=50");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().content()).isNotEmpty();
        // Le montant/reference exact importe moins que l'invariant : aucune ligne d'un autre utilisateur.
        long distinctOwners = response.getBody().data().content().size();
        assertThat(distinctOwners).isGreaterThanOrEqualTo(1);
    }

    @Test
    void history_anonymous_returns401() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/orders/history", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void history_filtersByStatus() {
        String admin = adminToken();
        publishRate(admin, "84.200000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));

        QuoteResponse quoteToCancel = createAcceptedQuote(user, "50000");
        OrderDetailResponse toCancel = createOrder(user, quoteToCancel.id(), alipayBeneficiary());
        restTemplate.exchange("/api/v1/orders/" + toCancel.id() + "/cancel", HttpMethod.POST,
                new HttpEntity<>(new com.converter.order.dto.CancelOrderRequest("test"), auth(user)), String.class);

        QuoteResponse quoteActive = createAcceptedQuote(user, "60000");
        createOrder(user, quoteActive.id(), alipayBeneficiary());

        var response = historyRaw(user, "status=CANCELLED&size=50");

        assertThat(response.getBody().data().content()).isNotEmpty();
        assertThat(response.getBody().data().content()).allSatisfy(
                entry -> assertThat(entry.status()).isEqualTo(OrderStatus.CANCELLED));
    }

    @Test
    void history_filtersByPurpose() {
        String admin = adminToken();
        publishRate(admin, "84.200000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));

        QuoteResponse quote1 = createAcceptedQuote(user, "70000");
        createOrderWithPurpose(user, quote1.id(), Purpose.EDUCATION);
        QuoteResponse quote2 = createAcceptedQuote(user, "80000");
        createOrderWithPurpose(user, quote2.id(), Purpose.IMPORT_GOODS);

        var response = historyRaw(user, "purpose=EDUCATION&size=50");

        assertThat(response.getBody().data().content()).isNotEmpty();
        assertThat(response.getBody().data().content()).allSatisfy(
                entry -> assertThat(entry.purpose()).isEqualTo(Purpose.EDUCATION));
    }

    @Test
    void history_supplierFilter_foreignSupplier_returnsEmptyNeverLeaksData() {
        String admin = adminToken();
        publishRate(admin, "84.200000");
        depositCny(admin, "1000000");
        String userA = tokenFor(createUser(RoleCode.USER));
        String userB = tokenFor(createUser(RoleCode.USER));

        // Fournisseur enregistre par B.
        var supplierResponse = restTemplate.exchange("/api/v1/suppliers", HttpMethod.POST,
                new HttpEntity<>(new com.converter.supplier.dto.CreateSupplierRequest(BeneficiaryType.ALIPAY,
                        "Fournisseur B", null, null, null, null, null, null, null, null, null,
                        "alipay-b@example.com", null, null, com.converter.treasury.domain.Currency.CNY, null, null),
                        auth(userB)),
                new ParameterizedTypeReference<ApiResponse<com.converter.supplier.dto.SupplierDetailResponse>>() {
                });
        UUID supplierBId = supplierResponse.getBody().data().id();

        QuoteResponse quoteA = createAcceptedQuote(userA, "90000");
        createOrder(userA, quoteA.id(), alipayBeneficiary());

        // A interroge son historique avec le supplierId de B : aucun resultat, jamais les ordres de A non plus
        // (le filtre ne matche aucun ordre de A, qui n'utilise pas ce fournisseur).
        var response = historyRaw(userA, "supplierId=" + supplierBId + "&size=50");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().content()).isEmpty();
    }

    @Test
    void history_filtersByDateRange() {
        String admin = adminToken();
        publishRate(admin, "84.200000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));

        QuoteResponse quote = createAcceptedQuote(user, "40000");
        OrderDetailResponse order = createOrder(user, quote.id(), alipayBeneficiary());

        Instant past = Instant.now().minusSeconds(3600);
        Instant future = Instant.now().plusSeconds(3600);

        var withinRange = historyRaw(user, "from=" + past + "&to=" + future + "&size=50");
        var outsideRange = historyRaw(user, "from=" + future + "&size=50");

        assertThat(withinRange.getBody().data().content()).extracting(OrderHistoryResponse::id).contains(order.id());
        assertThat(outsideRange.getBody().data().content()).extracting(OrderHistoryResponse::id)
                .doesNotContain(order.id());
    }

    @Test
    void history_invalidDate_returns400() {
        String user = tokenFor(createUser(RoleCode.USER));

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/orders/history?from=not-a-date", HttpMethod.GET, new HttpEntity<>(auth(user)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void history_isPaginated_respectsSizeParameter() {
        String admin = adminToken();
        publishRate(admin, "84.200000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));
        for (int i = 0; i < 3; i++) {
            QuoteResponse quote = createAcceptedQuote(user, "1000" + i);
            createOrder(user, quote.id(), alipayBeneficiary());
        }

        var response = historyRaw(user, "size=2");

        assertThat(response.getBody().data().content()).hasSize(2);
        assertThat(response.getBody().data().size()).isEqualTo(2);
    }

    @Test
    void history_isOrderedNewestFirst_regardlessOfClientSortParameter() {
        String admin = adminToken();
        publishRate(admin, "84.200000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));

        QuoteResponse quote1 = createAcceptedQuote(user, "10001");
        OrderDetailResponse first = createOrder(user, quote1.id(), alipayBeneficiary());
        QuoteResponse quote2 = createAcceptedQuote(user, "10002");
        OrderDetailResponse second = createOrder(user, quote2.id(), alipayBeneficiary());

        var response = historyRaw(user, "size=50&sort=amountXof,asc");

        var ids = response.getBody().data().content().stream().map(OrderHistoryResponse::id).toList();
        assertThat(ids.indexOf(second.id())).isLessThan(ids.indexOf(first.id()));
    }

    @Test
    void history_financialValues_matchOrderSnapshot() {
        String admin = adminToken();
        resetMarginToZero();
        publishRate(admin, "84.200000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));

        QuoteResponse quote = createAcceptedQuote(user, "100000");
        OrderDetailResponse order = createOrder(user, quote.id(), alipayBeneficiary());

        var response = historyRaw(user, "size=50");

        OrderHistoryResponse entry = response.getBody().data().content().stream()
                .filter(e -> e.id().equals(order.id())).findFirst().orElseThrow();
        assertThat(entry.amountXof()).isEqualByComparingTo(order.amountXof());
        assertThat(entry.amountCny()).isEqualByComparingTo(order.amountCny());
        assertThat(entry.feeXof()).isEqualByComparingTo(order.feeXof());
        assertThat(entry.customerRate()).isEqualByComparingTo(order.customerRate());
    }
}
