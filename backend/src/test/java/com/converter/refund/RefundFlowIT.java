package com.converter.refund;

import com.converter.common.api.ApiResponse;
import com.converter.common.api.ErrorResponse;
import com.converter.order.dto.OrderDetailResponse;
import com.converter.payment.dto.PaymentResponse;
import com.converter.quote.dto.QuoteResponse;
import com.converter.refund.domain.RefundStatus;
import com.converter.refund.dto.RefundResponse;
import com.converter.settlement.dto.SettlementResponse;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Flux complet du remboursement XOF, et ses garde-fous vis-a-vis du reste du pipeline
 * (Settlement, Treasury) — voir {@code RefundService} pour le raisonnement metier complet.
 */
class RefundFlowIT extends AbstractOrderPipelineIT {

    @Test
    void create_forConfirmedPayment_startsPendingWithExactReceivedAmount() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));
        QuoteResponse quote = createAcceptedQuote(user, "100000");
        OrderDetailResponse order = createOrder(user, quote.id(), alipayBeneficiary());
        PaymentResponse payment = submitPayment(user, order.id(), "100000", "MM-REFUND-001");
        uploadProof(user, payment.id());
        confirmPayment(admin, payment.id());

        RefundResponse refund = createRefund(admin, payment.id(), "Client a change d'avis");

        assertThat(refund.status()).isEqualTo(RefundStatus.PENDING);
        assertThat(refund.orderId()).isEqualTo(order.id());
        assertThat(refund.paymentId()).isEqualTo(payment.id());
        // Montant toujours derive du paiement reellement recu, jamais saisi librement.
        assertThat(refund.amountXof()).isEqualByComparingTo(payment.receivedAmountXof());
        assertThat(refund.transactionReference()).isNull();
    }

    @Test
    void create_forNonConfirmedPayment_isRejected() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));
        QuoteResponse quote = createAcceptedQuote(user, "100000");
        OrderDetailResponse order = createOrder(user, quote.id(), alipayBeneficiary());
        PaymentResponse payment = submitPayment(user, order.id(), "100000", "MM-REFUND-002");
        // Jamais confirme.

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/admin/payments/" + payment.id() + "/refunds", HttpMethod.POST,
                new HttpEntity<>("{\"reason\":\"test\"}", jsonHeaders(admin)), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).isEqualTo("INVALID_PAYMENT_STATE");
    }

    @Test
    void create_secondRefundForSamePayment_isRejected() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));
        QuoteResponse quote = createAcceptedQuote(user, "100000");
        OrderDetailResponse order = createOrder(user, quote.id(), alipayBeneficiary());
        PaymentResponse payment = submitPayment(user, order.id(), "100000", "MM-REFUND-003");
        uploadProof(user, payment.id());
        confirmPayment(admin, payment.id());
        createRefund(admin, payment.id(), "premier remboursement");

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/admin/payments/" + payment.id() + "/refunds", HttpMethod.POST,
                new HttpEntity<>("{\"reason\":\"deuxieme tentative\"}", jsonHeaders(admin)), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).isEqualTo("REFUND_ALREADY_EXISTS");
    }

    @Test
    void create_afterPreviousRefundWasRejected_isAllowed() {
        // Mission "Refund retry policy" : un rejet est une decision administrative reversible,
        // pas un fait objectif — une nouvelle tentative legitime ne doit pas rester bloquee
        // indefiniment par une erreur d'appreciation corrigee.
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));
        QuoteResponse quote = createAcceptedQuote(user, "100000");
        OrderDetailResponse order = createOrder(user, quote.id(), alipayBeneficiary());
        PaymentResponse payment = submitPayment(user, order.id(), "100000", "MM-REFUND-014");
        uploadProof(user, payment.id());
        confirmPayment(admin, payment.id());
        RefundResponse first = createRefund(admin, payment.id(), "premiere tentative");
        rejectRefundRaw(admin, first.id(), "mauvais paiement selectionne, erreur admin");

        RefundResponse second = createRefund(admin, payment.id(), "nouvelle tentative apres correction");

        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(second.status()).isEqualTo(RefundStatus.PENDING);
        assertThat(second.amountXof()).isEqualByComparingTo(payment.receivedAmountXof());

        RefundResponse processed = processRefund(admin, second.id(), "MM-OUT-RETRY-1");
        assertThat(processed.status()).isEqualTo(RefundStatus.PROCESSED);
    }

    @Test
    void create_afterPreviousRefundWasProcessed_isStillRejected() {
        // A l'inverse d'un REJECTED : un PROCESSED signifie que l'argent a reellement quitte la
        // tresorerie — celui-la reste bloquant pour toujours, aucune exception.
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));
        QuoteResponse quote = createAcceptedQuote(user, "100000");
        OrderDetailResponse order = createOrder(user, quote.id(), alipayBeneficiary());
        PaymentResponse payment = submitPayment(user, order.id(), "100000", "MM-REFUND-015");
        uploadProof(user, payment.id());
        confirmPayment(admin, payment.id());
        RefundResponse first = createRefund(admin, payment.id(), "premier remboursement");
        processRefund(admin, first.id(), "MM-OUT-016");

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/admin/payments/" + payment.id() + "/refunds", HttpMethod.POST,
                new HttpEntity<>("{\"reason\":\"deuxieme tentative injustifiee\"}", jsonHeaders(admin)),
                ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).isEqualTo("REFUND_ALREADY_EXISTS");
    }

    @Test
    void process_debitsExactAmountFromXofTreasuryAndRequiresReference() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));
        QuoteResponse quote = createAcceptedQuote(user, "100000");
        OrderDetailResponse order = createOrder(user, quote.id(), alipayBeneficiary());
        PaymentResponse payment = submitPayment(user, order.id(), "100000", "MM-REFUND-004");
        uploadProof(user, payment.id());
        confirmPayment(admin, payment.id());
        RefundResponse refund = createRefund(admin, payment.id(), "remboursement test");

        BigDecimal xofBefore = treasurySnapshot(admin, Currency.XOF).balance();

        RefundResponse processed = processRefund(admin, refund.id(), "MM-OUT-001");

        assertThat(processed.status()).isEqualTo(RefundStatus.PROCESSED);
        assertThat(processed.transactionReference()).isEqualTo("MM-OUT-001");

        BigDecimal xofAfter = treasurySnapshot(admin, Currency.XOF).balance();
        assertThat(xofBefore.subtract(xofAfter)).isEqualByComparingTo(refund.amountXof());
    }

    @Test
    void process_withoutTransactionReference_isRejectedByValidation() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));
        QuoteResponse quote = createAcceptedQuote(user, "100000");
        OrderDetailResponse order = createOrder(user, quote.id(), alipayBeneficiary());
        PaymentResponse payment = submitPayment(user, order.id(), "100000", "MM-REFUND-005");
        uploadProof(user, payment.id());
        confirmPayment(admin, payment.id());
        RefundResponse refund = createRefund(admin, payment.id(), "remboursement test");

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/admin/refunds/" + refund.id() + "/process", HttpMethod.POST,
                new HttpEntity<>("{\"transactionReference\":\"\"}", jsonHeaders(admin)), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void process_anAlreadyProcessedRefund_returnsConflict() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));
        QuoteResponse quote = createAcceptedQuote(user, "50000");
        OrderDetailResponse order = createOrder(user, quote.id(), alipayBeneficiary());
        PaymentResponse payment = submitPayment(user, order.id(), "50000", "MM-REFUND-006");
        uploadProof(user, payment.id());
        confirmPayment(admin, payment.id());
        RefundResponse refund = createRefund(admin, payment.id(), "test");
        processRefund(admin, refund.id(), "MM-OUT-002");

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/admin/refunds/" + refund.id() + "/process", HttpMethod.POST,
                new HttpEntity<>("{\"transactionReference\":\"MM-OUT-003\"}", jsonHeaders(admin)), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).isEqualTo("INVALID_REFUND_STATE");
    }

    @Test
    void reject_pendingRefund_producesNoTreasuryEffect() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));
        QuoteResponse quote = createAcceptedQuote(user, "50000");
        OrderDetailResponse order = createOrder(user, quote.id(), alipayBeneficiary());
        PaymentResponse payment = submitPayment(user, order.id(), "50000", "MM-REFUND-007");
        uploadProof(user, payment.id());
        confirmPayment(admin, payment.id());
        RefundResponse refund = createRefund(admin, payment.id(), "test");

        BigDecimal xofBefore = treasurySnapshot(admin, Currency.XOF).balance();

        ResponseEntity<ApiResponse<RefundResponse>> rejected = rejectRefundRaw(admin, refund.id(), "finalement non");

        assertThat(rejected.getBody().data().status()).isEqualTo(RefundStatus.REJECTED);
        BigDecimal xofAfter = treasurySnapshot(admin, Currency.XOF).balance();
        assertThat(xofAfter).isEqualByComparingTo(xofBefore);
    }

    @Test
    void process_withInsufficientXofTreasury_isRejectedAndRefundStaysPending() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));
        QuoteResponse quote = createAcceptedQuote(user, "100000");
        OrderDetailResponse order = createOrder(user, quote.id(), alipayBeneficiary());
        PaymentResponse payment = submitPayment(user, order.id(), "100000", "MM-REFUND-008");
        uploadProof(user, payment.id());
        confirmPayment(admin, payment.id());
        RefundResponse refund = createRefund(admin, payment.id(), "test");

        // Retire artificiellement TOUTE la liquidite XOF disponible (le solde XOF est partage par
        // toute la suite de tests, jamais suppose vide au depart) pour ne laisser qu'une somme
        // symbolique, trop faible pour honorer ce remboursement de 100 000.
        BigDecimal currentXof = treasurySnapshot(admin, Currency.XOF).balance();
        BigDecimal delta = BigDecimal.TEN.subtract(currentXof);
        adjustTreasury(admin, Currency.XOF, delta.toPlainString(), "retrait test insuffisance");

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/admin/refunds/" + refund.id() + "/process", HttpMethod.POST,
                new HttpEntity<>("{\"transactionReference\":\"MM-OUT-004\"}", jsonHeaders(admin)), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).isEqualTo("INSUFFICIENT_TREASURY");

        RefundResponse reloaded = getRefund(admin, refund.id());
        assertThat(reloaded.status()).isEqualTo(RefundStatus.PENDING);
        assertThat(reloaded.transactionReference()).isNull();
    }

    @Test
    void process_afterSettlementAlreadyExecuted_stillWorksIndependently() {
        // Cas B de la mission : Payment CONFIRMED, Settlement EXECUTED, Order COMPLETED — le
        // remboursement reste possible, sans pretendre annuler le decaissement CNY deja fait.
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));
        QuoteResponse quote = createAcceptedQuote(user, "100000");
        OrderDetailResponse order = createOrder(user, quote.id(), alipayBeneficiary());
        PaymentResponse payment = submitPayment(user, order.id(), "100000", "MM-REFUND-009");
        uploadProof(user, payment.id());
        confirmPayment(admin, payment.id());
        SettlementResponse settlement = createSettlement(admin, order.id());
        uploadSettlementProof(admin, settlement.id());
        SettlementResponse executed = executeSettlement(admin, settlement.id(), "CNY-PAYOUT-REFUND-1");
        assertThat(executed.status().name()).isEqualTo("EXECUTED");

        RefundResponse refund = createRefund(admin, payment.id(), "erreur decouverte apres coup");
        RefundResponse processed = processRefund(admin, refund.id(), "MM-OUT-005");

        assertThat(processed.status()).isEqualTo(RefundStatus.PROCESSED);
        // Le Settlement deja execute n'est jamais touche : cette API n'a aucun moyen de le modifier.
        ResponseEntity<ApiResponse<SettlementResponse>> reloadedSettlement = restTemplate.exchange(
                "/api/admin/settlements/" + settlement.id(), HttpMethod.GET, new HttpEntity<>(auth(admin)),
                new ParameterizedTypeReference<ApiResponse<SettlementResponse>>() {
                });
        assertThat(reloadedSettlement.getBody().data().status().name()).isEqualTo("EXECUTED");
    }

    @Test
    void executeSettlement_afterRefundProcessed_isBlocked() {
        // Cas A / section 8 de la mission : rembourser le client PUIS executer quand meme le
        // reglement CNY produirait un double decaissement economique — doit etre refuse.
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));
        QuoteResponse quote = createAcceptedQuote(user, "100000");
        OrderDetailResponse order = createOrder(user, quote.id(), alipayBeneficiary());
        PaymentResponse payment = submitPayment(user, order.id(), "100000", "MM-REFUND-010");
        uploadProof(user, payment.id());
        confirmPayment(admin, payment.id());
        SettlementResponse settlement = createSettlement(admin, order.id());
        uploadSettlementProof(admin, settlement.id());

        RefundResponse refund = createRefund(admin, payment.id(), "client rembourse avant reglement");
        processRefund(admin, refund.id(), "MM-OUT-006");

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/admin/settlements/" + settlement.id() + "/execute", HttpMethod.POST,
                new HttpEntity<>("{\"settlementReference\":\"CNY-SHOULD-NOT-HAPPEN\"}", jsonHeaders(admin)),
                ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).isEqualTo("SETTLEMENT_BLOCKED_BY_REFUND");

        ResponseEntity<ApiResponse<SettlementResponse>> reloaded = restTemplate.exchange(
                "/api/admin/settlements/" + settlement.id(), HttpMethod.GET, new HttpEntity<>(auth(admin)),
                new ParameterizedTypeReference<ApiResponse<SettlementResponse>>() {
                });
        assertThat(reloaded.getBody().data().status().name()).isEqualTo("PENDING");
    }

    @Test
    void create_asClient_isForbidden() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));
        QuoteResponse quote = createAcceptedQuote(user, "50000");
        OrderDetailResponse order = createOrder(user, quote.id(), alipayBeneficiary());
        PaymentResponse payment = submitPayment(user, order.id(), "50000", "MM-REFUND-011");
        uploadProof(user, payment.id());
        confirmPayment(admin, payment.id());

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/admin/payments/" + payment.id() + "/refunds", HttpMethod.POST,
                new HttpEntity<>("{\"reason\":\"je veux mon argent\"}", jsonHeaders(user)), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void create_replayedWithSameIdempotencyKey_neverCreatesASecondRefund() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));
        QuoteResponse quote = createAcceptedQuote(user, "100000");
        OrderDetailResponse order = createOrder(user, quote.id(), alipayBeneficiary());
        PaymentResponse payment = submitPayment(user, order.id(), "100000", "MM-REFUND-012");
        uploadProof(user, payment.id());
        confirmPayment(admin, payment.id());
        String idemKey = "refund-create-" + UUID.randomUUID();

        ResponseEntity<ApiResponse<RefundResponse>> first = createRefundRaw(admin, payment.id(), "test", idemKey);
        ResponseEntity<ApiResponse<RefundResponse>> second = createRefundRaw(admin, payment.id(), "test", idemKey);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getBody().data().id()).isEqualTo(first.getBody().data().id());
    }

    @Test
    void create_sameKeyDifferentBody_isRejected() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));
        QuoteResponse quote = createAcceptedQuote(user, "100000");
        OrderDetailResponse order = createOrder(user, quote.id(), alipayBeneficiary());
        PaymentResponse payment = submitPayment(user, order.id(), "100000", "MM-REFUND-013");
        uploadProof(user, payment.id());
        confirmPayment(admin, payment.id());
        String idemKey = "refund-conflict-" + UUID.randomUUID();

        createRefundRaw(admin, payment.id(), "premier motif", idemKey);
        ResponseEntity<ErrorResponse> conflict = restTemplate.exchange(
                "/api/admin/payments/" + payment.id() + "/refunds", HttpMethod.POST,
                new HttpEntity<>("{\"reason\":\"motif different\"}", jsonHeaders(admin, idemKey)),
                ErrorResponse.class);

        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(conflict.getBody().code()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
    }

    // -----------------------------------------------------------------

    private HttpHeaders jsonHeaders(String token) {
        HttpHeaders headers = auth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private HttpHeaders jsonHeaders(String token, String idempotencyKey) {
        HttpHeaders headers = jsonHeaders(token);
        headers.set("Idempotency-Key", idempotencyKey);
        return headers;
    }

}
