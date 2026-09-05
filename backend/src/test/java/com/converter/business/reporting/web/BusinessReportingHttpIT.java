package com.converter.business.reporting.web;

import com.converter.business.profile.domain.BusinessType;
import com.converter.business.profile.dto.UpsertBusinessProfileRequest;
import com.converter.business.reporting.dto.BusinessPaymentSummaryResponse;
import com.converter.common.api.ApiResponse;
import com.converter.common.api.ErrorResponse;
import com.converter.order.dto.CancelOrderRequest;
import com.converter.order.dto.OrderDetailResponse;
import com.converter.payment.dto.PaymentResponse;
import com.converter.payment.repository.PaymentRepository;
import com.converter.quote.dto.QuoteResponse;
import com.converter.refund.dto.RefundResponse;
import com.converter.support.AbstractOrderPipelineIT;
import com.converter.user.domain.RoleCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code GET /api/v1/business/payments/summary} (Phase 8) : reserve aux profils Business,
 * agregation SQL par statut, verite financiere a partir des seuls ordres {@code COMPLETED},
 * separation stricte {@code Refund}/{@code Order} (section 22/23).
 */
class BusinessReportingHttpIT extends AbstractOrderPipelineIT {

    private void createBusinessProfile(String token) {
        restTemplate.exchange("/api/v1/business-profile", HttpMethod.PUT,
                new HttpEntity<>(new UpsertBusinessProfileRequest("Zhang Trading", BusinessType.IMPORTER, null,
                        "Burkina Faso", "Ouagadougou", null), auth(token)),
                new ParameterizedTypeReference<ApiResponse<Object>>() {
                });
    }

    private ResponseEntity<ApiResponse<BusinessPaymentSummaryResponse>> summaryRaw(String token) {
        return restTemplate.exchange("/api/v1/business/payments/summary", HttpMethod.GET,
                new HttpEntity<>(auth(token)),
                new ParameterizedTypeReference<ApiResponse<BusinessPaymentSummaryResponse>>() {
                });
    }

    @Test
    void summary_withoutBusinessProfile_returns404() {
        String user = tokenFor(createUser(RoleCode.USER));

        ResponseEntity<ErrorResponse> response = restTemplate.exchange("/api/v1/business/payments/summary",
                HttpMethod.GET, new HttpEntity<>(auth(user)), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo("BUSINESS_PROFILE_NOT_FOUND");
    }

    @Test
    void summary_anonymous_returns401() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/business/payments/summary",
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void summary_countsAndTotals_matchOrderStatesExactly() {
        String admin = adminToken();
        resetMarginToZero();
        publishRate(admin, "84.200000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));
        createBusinessProfile(user);

        // 2 COMPLETED.
        QuoteResponse quote1 = createAcceptedQuote(user, "100000");
        OrderDetailResponse order1 = createOrder(user, quote1.id(), bankBeneficiary());
        completeOrder(admin, user, order1.id(), order1.amountXof().toPlainString(), "MM-SUM-001", "CNY-SUM-001");

        QuoteResponse quote2 = createAcceptedQuote(user, "200000");
        OrderDetailResponse order2 = createOrder(user, quote2.id(), bankBeneficiary());
        completeOrder(admin, user, order2.id(), order2.amountXof().toPlainString(), "MM-SUM-002", "CNY-SUM-002");

        // 1 CANCELLED.
        QuoteResponse quote3 = createAcceptedQuote(user, "50000");
        OrderDetailResponse order3 = createOrder(user, quote3.id(), bankBeneficiary());
        restTemplate.exchange("/api/v1/orders/" + order3.id() + "/cancel", HttpMethod.POST,
                new HttpEntity<>(new CancelOrderRequest("test"), auth(user)), String.class);

        // 1 REJECTED (paiement rejete).
        QuoteResponse quote4 = createAcceptedQuote(user, "70000");
        OrderDetailResponse order4 = createOrder(user, quote4.id(), bankBeneficiary());
        PaymentResponse payment4 = submitPayment(user, order4.id(), order4.amountXof().toPlainString(), "MM-SUM-004");
        rejectPayment(admin, payment4.id(), "test rejet");

        var response = summaryRaw(user);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        BusinessPaymentSummaryResponse summary = response.getBody().data();

        assertThat(summary.transferCount()).isEqualTo(4);
        assertThat(summary.completedCount()).isEqualTo(2);
        assertThat(summary.cancelledCount()).isEqualTo(1);
        assertThat(summary.rejectedCount()).isEqualTo(1);

        // Seuls les COMPLETED contribuent aux montants -- jamais CANCELLED/REJECTED (convention documentee).
        assertThat(summary.totalAmountXof()).isEqualByComparingTo(
                order1.amountXof().add(order2.amountXof()));
        assertThat(summary.totalFeesXof()).isEqualByComparingTo(
                order1.feeXof().add(order2.feeXof()));
    }

    @Test
    void summary_refundOnCompletedOrder_neverAltersTheTotals() {
        String admin = adminToken();
        resetMarginToZero();
        publishRate(admin, "84.200000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));
        createBusinessProfile(user);

        QuoteResponse quote = createAcceptedQuote(user, "100000");
        OrderDetailResponse order = createOrder(user, quote.id(), bankBeneficiary());
        completeOrder(admin, user, order.id(), order.amountXof().toPlainString(), "MM-SUM-REFUND-001",
                "CNY-SUM-REFUND-001");

        BusinessPaymentSummaryResponse before = summaryRaw(user).getBody().data();

        // Recupere le paiement associe pour rembourser.
        var tracking = restTemplate.exchange("/api/v1/orders/" + order.id(), HttpMethod.GET,
                new HttpEntity<>(auth(user)),
                new ParameterizedTypeReference<ApiResponse<OrderDetailResponse>>() {
                }).getBody().data();
        assertThat(tracking.status().name()).isEqualTo("COMPLETED");

        UUID paymentId = findPaymentId(order.id());
        RefundResponse refund = createRefund(admin, paymentId, "Remboursement test reporting");
        processRefund(admin, refund.id(), "XOF-SUM-REFUND-001");


        BusinessPaymentSummaryResponse after = summaryRaw(user).getBody().data();

        assertThat(after.completedCount()).isEqualTo(before.completedCount());
        assertThat(after.totalAmountXof()).isEqualByComparingTo(before.totalAmountXof());
        assertThat(after.totalAmountCny()).isEqualByComparingTo(before.totalAmountCny());
        assertThat(after.totalFeesXof()).isEqualByComparingTo(before.totalFeesXof());
    }

    @Test
    void summary_dateRange_excludesOrdersOutsideWindow() {
        String admin = adminToken();
        resetMarginToZero();
        publishRate(admin, "84.200000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));
        createBusinessProfile(user);

        QuoteResponse quote = createAcceptedQuote(user, "100000");
        OrderDetailResponse order = createOrder(user, quote.id(), bankBeneficiary());
        completeOrder(admin, user, order.id(), order.amountXof().toPlainString(), "MM-SUM-DATE-001",
                "CNY-SUM-DATE-001");

        Instant future = Instant.now().plusSeconds(3600);
        ResponseEntity<ApiResponse<BusinessPaymentSummaryResponse>> response = restTemplate.exchange(
                "/api/v1/business/payments/summary?from=" + future, HttpMethod.GET,
                new HttpEntity<>(auth(user)),
                new ParameterizedTypeReference<ApiResponse<BusinessPaymentSummaryResponse>>() {
                });

        assertThat(response.getBody().data().transferCount()).isZero();
        assertThat(response.getBody().data().totalAmountXof()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Autowired
    private PaymentRepository paymentRepository;

    private UUID findPaymentId(UUID orderId) {
        return paymentRepository.findByOrderId(orderId).orElseThrow().getId();
    }
}
