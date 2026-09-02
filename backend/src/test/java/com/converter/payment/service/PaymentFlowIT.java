package com.converter.payment.service;

import com.converter.common.api.ApiResponse;
import com.converter.common.api.ErrorResponse;
import com.converter.order.domain.OrderStatus;
import com.converter.order.dto.OrderDetailResponse;
import com.converter.payment.domain.PaymentMethod;
import com.converter.payment.domain.PaymentStatus;
import com.converter.payment.dto.PaymentProofResponse;
import com.converter.payment.dto.PaymentResponse;
import com.converter.payment.dto.SubmitPaymentRequest;
import com.converter.quote.dto.QuoteResponse;
import com.converter.support.AbstractOrderPipelineIT;
import com.converter.treasury.domain.Currency;
import com.converter.user.domain.RoleCode;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentFlowIT extends AbstractOrderPipelineIT {

    @Test
    void submitPayment_transitionsOrderToPaymentSubmitted() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));
        QuoteResponse quote = createAcceptedQuote(user, "100000");
        OrderDetailResponse order = createOrder(user, quote.id(), alipayBeneficiary());

        PaymentResponse payment = submitPayment(user, order.id(), "100000", "MM-REF-001");

        assertThat(payment.status()).isEqualTo(PaymentStatus.SUBMITTED);
        assertThat(payment.orderId()).isEqualTo(order.id());

        ResponseEntity<ApiResponse<OrderDetailResponse>> reloaded = restTemplate.exchange(
                "/api/v1/orders/" + order.id(), HttpMethod.GET, new HttpEntity<>(auth(user)),
                new ParameterizedTypeReference<ApiResponse<OrderDetailResponse>>() {
                });
        assertThat(reloaded.getBody().data().status()).isEqualTo(OrderStatus.PAYMENT_SUBMITTED);
    }

    /**
     * Passe 2, P2-2 : declarer un montant recu inferieur au montant attendu (tolerance 0 par
     * defaut) doit etre rejete AVANT toute ecriture — un sous-paiement accepte serait une perte
     * de change directe (le decaissement CNY reste calcule sur le montant de l'ordre).
     */
    @Test
    void submitPayment_withAmountBelowExpected_isRejectedAndOrderUnchanged() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));
        QuoteResponse quote = createAcceptedQuote(user, "100000");
        OrderDetailResponse order = createOrder(user, quote.id(), alipayBeneficiary());

        HttpHeaders headers = auth(user);
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/v1/orders/" + order.id() + "/payments", HttpMethod.POST,
                new HttpEntity<>("{\"method\":\"MOBILE_MONEY\",\"receivedAmountXof\":1,"
                        + "\"transactionReference\":\"MM-REF-SHORT-1\"}", headers),
                ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("PAYMENT_AMOUNT_MISMATCH");

        ResponseEntity<ApiResponse<OrderDetailResponse>> reloaded = restTemplate.exchange(
                "/api/v1/orders/" + order.id(), HttpMethod.GET, new HttpEntity<>(auth(user)),
                new ParameterizedTypeReference<ApiResponse<OrderDetailResponse>>() {
                });
        assertThat(reloaded.getBody().data().status()).isEqualTo(OrderStatus.AWAITING_PAYMENT);
    }

    @Test
    void submitPayment_twice_returnsConflict() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));
        QuoteResponse quote = createAcceptedQuote(user, "50000");
        OrderDetailResponse order = createOrder(user, quote.id(), alipayBeneficiary());
        submitPayment(user, order.id(), "50000", "MM-REF-002");

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/v1/orders/" + order.id() + "/payments", HttpMethod.POST,
                new HttpEntity<>(new SubmitPaymentRequest(PaymentMethod.MOBILE_MONEY, new BigDecimal("50000"),
                        "MM-REF-003", null), auth(user)),
                ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void uploadProof_withWrongMagicBytes_isRejected() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));
        QuoteResponse quote = createAcceptedQuote(user, "50000");
        OrderDetailResponse order = createOrder(user, quote.id(), alipayBeneficiary());
        PaymentResponse payment = submitPayment(user, order.id(), "50000", "MM-REF-004");

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        byte[] notAnImage = "this is definitely not a jpeg".getBytes();
        body.add("file", new org.springframework.core.io.ByteArrayResource(notAnImage) {
            @Override
            public String getFilename() {
                return "fake.jpg";
            }
        });
        HttpHeaders headers = auth(user);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/v1/payments/" + payment.id() + "/proofs", HttpMethod.POST,
                new HttpEntity<>(body, headers), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("INVALID_PAYMENT_PROOF");
    }

    @Test
    void confirm_withoutAnyProof_isRejected() {
        // REQUIRE_PAYMENT_PROOF = true (seed V4) : la confirmation doit
        // etre bloquee tant qu'aucune preuve n'a ete televersee.
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));
        QuoteResponse quote = createAcceptedQuote(user, "50000");
        OrderDetailResponse order = createOrder(user, quote.id(), alipayBeneficiary());
        PaymentResponse payment = submitPayment(user, order.id(), "50000", "MM-REF-005");

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/admin/payments/" + payment.id() + "/confirm", HttpMethod.POST,
                new HttpEntity<>(auth(admin)), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("INVALID_PAYMENT_PROOF");
    }

    @Test
    void confirm_withProof_transitionsOrderAndDepositsTreasury() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));
        QuoteResponse quote = createAcceptedQuote(user, "100000");
        OrderDetailResponse order = createOrder(user, quote.id(), alipayBeneficiary());
        PaymentResponse payment = submitPayment(user, order.id(), "100000", "MM-REF-006");
        uploadProof(user, payment.id());

        BigDecimal xofBefore = treasurySnapshot(admin, Currency.XOF).balance();

        PaymentResponse confirmed = confirmPayment(admin, payment.id());

        assertThat(confirmed.status()).isEqualTo(PaymentStatus.CONFIRMED);

        ResponseEntity<ApiResponse<OrderDetailResponse>> reloaded = restTemplate.exchange(
                "/api/v1/orders/" + order.id(), HttpMethod.GET, new HttpEntity<>(auth(user)),
                new ParameterizedTypeReference<ApiResponse<OrderDetailResponse>>() {
                });
        assertThat(reloaded.getBody().data().status()).isEqualTo(OrderStatus.PAYMENT_VERIFIED);

        BigDecimal xofAfter = treasurySnapshot(admin, Currency.XOF).balance();
        assertThat(xofAfter.subtract(xofBefore)).isEqualByComparingTo(new BigDecimal("100000.00"));
    }

    @Test
    void reject_terminatesOrderAndReleasesTreasury() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));
        QuoteResponse quote = createAcceptedQuote(user, "100000");
        OrderDetailResponse order = createOrder(user, quote.id(), alipayBeneficiary());
        PaymentResponse payment = submitPayment(user, order.id(), "100000", "MM-REF-007");
        uploadProof(user, payment.id());

        BigDecimal cnyAvailableBefore = treasurySnapshot(admin, Currency.CNY).available();

        ResponseEntity<ApiResponse<PaymentResponse>> rejected = restTemplate.exchange(
                "/api/admin/payments/" + payment.id() + "/reject", HttpMethod.POST,
                new HttpEntity<>(new com.converter.payment.dto.RejectPaymentRequest("preuve illisible"), auth(admin)),
                new ParameterizedTypeReference<ApiResponse<PaymentResponse>>() {
                });

        assertThat(rejected.getBody().data().status()).isEqualTo(PaymentStatus.REJECTED);

        ResponseEntity<ApiResponse<OrderDetailResponse>> reloaded = restTemplate.exchange(
                "/api/v1/orders/" + order.id(), HttpMethod.GET, new HttpEntity<>(auth(user)),
                new ParameterizedTypeReference<ApiResponse<OrderDetailResponse>>() {
                });
        assertThat(reloaded.getBody().data().status()).isEqualTo(OrderStatus.REJECTED);

        BigDecimal cnyAvailableAfter = treasurySnapshot(admin, Currency.CNY).available();
        assertThat(cnyAvailableAfter.subtract(cnyAvailableBefore)).isEqualByComparingTo(order.amountCny());
    }

    @Test
    void confirm_anAlreadyConfirmedPayment_returnsConflict() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));
        QuoteResponse quote = createAcceptedQuote(user, "50000");
        OrderDetailResponse order = createOrder(user, quote.id(), alipayBeneficiary());
        PaymentResponse payment = submitPayment(user, order.id(), "50000", "MM-REF-008");
        uploadProof(user, payment.id());
        confirmPayment(admin, payment.id());

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/admin/payments/" + payment.id() + "/confirm", HttpMethod.POST,
                new HttpEntity<>(auth(admin)), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).isEqualTo("INVALID_PAYMENT_STATE");
    }

    @Test
    void get_paymentOfAnotherUser_returns404NotForbidden() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        String owner = tokenFor(createUser(RoleCode.USER));
        QuoteResponse quote = createAcceptedQuote(owner, "50000");
        OrderDetailResponse order = createOrder(owner, quote.id(), alipayBeneficiary());
        PaymentResponse payment = submitPayment(owner, order.id(), "50000", "MM-REF-009");

        String intruder = tokenFor(createUser(RoleCode.USER));
        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/v1/payments/" + payment.id(), HttpMethod.GET, new HttpEntity<>(auth(intruder)),
                ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo("PAYMENT_NOT_FOUND");
    }
}
