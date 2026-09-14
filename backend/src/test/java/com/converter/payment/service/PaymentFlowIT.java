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
                        + "\"transactionReference\":\"MM-REF-SHORT-1\","
                        + "\"payerPhone\":\"+2250700000000\",\"payerName\":\"Payeur Test\"}", headers),
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
                        "MM-REF-003", "+2250700000000", "Payeur Test"), auth(user)),
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

    /**
     * REJECTED n'est plus un etat terminal (resoumission possible, voir plus bas) : la
     * reservation CNY reste donc VOLONTAIREMENT en place apres un rejet -- la liberer puis la
     * reprendre a la resoumission violerait {@code uq_treasury_tx_reservation_per_order}/{@code
     * uq_treasury_tx_resolution_per_order} (V16, P2-4 : au plus une reservation/resolution par
     * ordre). Seuls CANCELLED/EXPIRED (veritablement terminaux) liberent encore la CNY.
     */
    /**
     * Regression : {@code confirm} n'utilisait aucune protection d'idempotence -- une reponse
     * perdue (timeout reseau) puis un nouvel essai de l'admin heurtait {@code requireSubmitted()}
     * et renvoyait 409 "deja traite", alors que l'admin n'avait jamais vu de succes.
     */
    @Test
    void confirm_replayedWithSameIdempotencyKey_returnsTheSameConfirmationInsteadOfConflict() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));
        QuoteResponse quote = createAcceptedQuote(user, "50000");
        OrderDetailResponse order = createOrder(user, quote.id(), alipayBeneficiary());
        PaymentResponse payment = submitPayment(user, order.id(), "50000", "MM-REF-IDEMP-CONFIRM");
        uploadProof(user, payment.id());

        HttpHeaders headers = auth(admin);
        headers.set("Idempotency-Key", "confirm-" + payment.id());
        ResponseEntity<ApiResponse<PaymentResponse>> first = restTemplate.exchange(
                "/api/admin/payments/" + payment.id() + "/confirm", HttpMethod.POST, new HttpEntity<>(headers),
                new ParameterizedTypeReference<ApiResponse<PaymentResponse>>() {
                });
        ResponseEntity<ApiResponse<PaymentResponse>> replay = restTemplate.exchange(
                "/api/admin/payments/" + payment.id() + "/confirm", HttpMethod.POST, new HttpEntity<>(headers),
                new ParameterizedTypeReference<ApiResponse<PaymentResponse>>() {
                });

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(replay.getBody().data().status()).isEqualTo(PaymentStatus.CONFIRMED);
    }

    /** Meme regression que {@link #confirm_replayedWithSameIdempotencyKey_returnsTheSameConfirmationInsteadOfConflict}, cote rejet. */
    @Test
    void reject_replayedWithSameIdempotencyKey_returnsTheSameRejectionInsteadOfConflict() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));
        QuoteResponse quote = createAcceptedQuote(user, "50000");
        OrderDetailResponse order = createOrder(user, quote.id(), alipayBeneficiary());
        PaymentResponse payment = submitPayment(user, order.id(), "50000", "MM-REF-IDEMP-REJECT");
        uploadProof(user, payment.id());

        HttpHeaders headers = auth(admin);
        headers.set("Idempotency-Key", "reject-" + payment.id());
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<com.converter.payment.dto.RejectPaymentRequest> request =
                new HttpEntity<>(new com.converter.payment.dto.RejectPaymentRequest("preuve illisible"), headers);
        ResponseEntity<ApiResponse<PaymentResponse>> first = restTemplate.exchange(
                "/api/admin/payments/" + payment.id() + "/reject", HttpMethod.POST, request,
                new ParameterizedTypeReference<ApiResponse<PaymentResponse>>() {
                });
        ResponseEntity<ApiResponse<PaymentResponse>> replay = restTemplate.exchange(
                "/api/admin/payments/" + payment.id() + "/reject", HttpMethod.POST, request,
                new ParameterizedTypeReference<ApiResponse<PaymentResponse>>() {
                });

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(replay.getBody().data().status()).isEqualTo(PaymentStatus.REJECTED);
    }

    @Test
    void reject_setsOrderToRejectedButKeepsTreasuryReservedForAPossibleResubmission() {
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
        assertThat(cnyAvailableAfter).isEqualByComparingTo(cnyAvailableBefore);
    }

    /**
     * Regression : un motif de rejet multi-lignes (saisi via un {@code <textarea>} cote
     * frontend) faisait echouer l'insertion de {@code audit_logs.metadata} avec une
     * {@code DataIntegrityViolationException} ("invalid input syntax for type json", le
     * caractere de controle 0x0A n'etait pas echappe) — le rejet retournait 409 alors que le
     * motif etait parfaitement valide. Voir {@code JsonUtil#jsonString}.
     */
    @Test
    void reject_withMultilineReason_succeeds() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));
        QuoteResponse quote = createAcceptedQuote(user, "50000");
        OrderDetailResponse order = createOrder(user, quote.id(), alipayBeneficiary());
        PaymentResponse payment = submitPayment(user, order.id(), "50000", "MM-REF-MULTILINE");
        uploadProof(user, payment.id());

        ResponseEntity<ApiResponse<PaymentResponse>> rejected = rejectPaymentRaw(admin, payment.id(),
                "Preuve illisible\nMerci de renvoyer une photo plus nette.");

        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rejected.getBody().data().status()).isEqualTo(PaymentStatus.REJECTED);
    }

    // ---- Resoumission apres rejet (retour client : forcer un nouvel ordre etait un contournement) ----

    @Test
    void resubmitPayment_afterRejection_succeedsOnTheSameOrderWithoutCreatingANewOne() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));
        QuoteResponse quote = createAcceptedQuote(user, "100000");
        OrderDetailResponse order = createOrder(user, quote.id(), alipayBeneficiary());
        PaymentResponse firstAttempt = submitPayment(user, order.id(), "100000", "MM-REF-RESUB-1");
        uploadProof(user, firstAttempt.id());
        rejectPayment(admin, firstAttempt.id(), "preuve illisible");

        ResponseEntity<ApiResponse<PaymentResponse>> resubmitted = submitPaymentRaw(
                user, order.id(), "100000", "MM-REF-RESUB-2");

        assertThat(resubmitted.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        PaymentResponse payment = resubmitted.getBody().data();
        // MEME paiement mis a jour (meme id), pas un second paiement cree pour cet ordre.
        assertThat(payment.id()).isEqualTo(firstAttempt.id());
        assertThat(payment.status()).isEqualTo(PaymentStatus.SUBMITTED);
        assertThat(payment.rejectionReason()).isNull();
        assertThat(payment.transactionReference()).isEqualTo("MM-REF-RESUB-2");

        ResponseEntity<ApiResponse<OrderDetailResponse>> reloaded = restTemplate.exchange(
                "/api/v1/orders/" + order.id(), HttpMethod.GET, new HttpEntity<>(auth(user)),
                new ParameterizedTypeReference<ApiResponse<OrderDetailResponse>>() {
                });
        // MEME ordre (meme id/reference), jamais un second ordre a recreer.
        assertThat(reloaded.getBody().data().id()).isEqualTo(order.id());
        assertThat(reloaded.getBody().data().status()).isEqualTo(OrderStatus.PAYMENT_SUBMITTED);
        assertThat(reloaded.getBody().data().rejectionReason()).isNull();
    }

    /**
     * Le paiement reel hors plateforme n'a pas change : reutiliser exactement la MEME reference
     * de transaction lors de la resoumission ne doit jamais etre traite comme un doublon contre
     * soi-meme (voir {@code existsByMethodAndTransactionReferenceAndIdNot}).
     */
    @Test
    void resubmitPayment_reusingTheSameTransactionReference_isAllowed() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));
        QuoteResponse quote = createAcceptedQuote(user, "50000");
        OrderDetailResponse order = createOrder(user, quote.id(), alipayBeneficiary());
        PaymentResponse firstAttempt = submitPayment(user, order.id(), "50000", "MM-REF-SAME-REF");
        uploadProof(user, firstAttempt.id());
        rejectPayment(admin, firstAttempt.id(), "montant illisible sur la photo");

        ResponseEntity<ApiResponse<PaymentResponse>> resubmitted = submitPaymentRaw(
                user, order.id(), "50000", "MM-REF-SAME-REF");

        assertThat(resubmitted.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resubmitted.getBody().data().transactionReference()).isEqualTo("MM-REF-SAME-REF");
    }

    @Test
    void resubmitPayment_reusingAnotherOrdersTransactionReference_isStillRejectedAsDuplicate() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "2000000");
        String user = tokenFor(createUser(RoleCode.USER));

        QuoteResponse quoteA = createAcceptedQuote(user, "50000");
        OrderDetailResponse orderA = createOrder(user, quoteA.id(), alipayBeneficiary());
        submitPayment(user, orderA.id(), "50000", "MM-REF-OTHER-ORDER");

        QuoteResponse quoteB = createAcceptedQuote(user, "60000");
        OrderDetailResponse orderB = createOrder(user, quoteB.id(), alipayBeneficiary());
        PaymentResponse paymentB = submitPayment(user, orderB.id(), "60000", "MM-REF-TO-REJECT");
        uploadProof(user, paymentB.id());
        rejectPayment(admin, paymentB.id(), "reference illisible");

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/v1/orders/" + orderB.id() + "/payments", HttpMethod.POST,
                new HttpEntity<>(new SubmitPaymentRequest(PaymentMethod.MOBILE_MONEY, new BigDecimal("60000"),
                        "MM-REF-OTHER-ORDER", "+2250700000000", "Payeur Test"), auth(user)),
                ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).isEqualTo("DUPLICATE_TRANSACTION_REFERENCE");
    }

    /**
     * Le coeur du bug signale : apres un rejet, le client resoumet sur le MEME ordre puis
     * l'admin confirme -- l'ordre doit progresser normalement vers PAYMENT_VERIFIED, jamais un
     * "reglement deja fait" ou tout autre etat incoherent issu d'un contournement (recreer un
     * second ordre pour la meme transaction reelle).
     */
    @Test
    void confirmAfterResubmission_transitionsOrderToPaymentVerified() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));
        QuoteResponse quote = createAcceptedQuote(user, "100000");
        OrderDetailResponse order = createOrder(user, quote.id(), alipayBeneficiary());
        PaymentResponse firstAttempt = submitPayment(user, order.id(), "100000", "MM-REF-CONFIRM-1");
        uploadProof(user, firstAttempt.id());
        rejectPayment(admin, firstAttempt.id(), "preuve illisible");

        PaymentResponse resubmitted = submitPayment(user, order.id(), "100000", "MM-REF-CONFIRM-2");
        uploadProof(user, resubmitted.id());
        PaymentResponse confirmed = confirmPayment(admin, resubmitted.id());

        assertThat(confirmed.status()).isEqualTo(PaymentStatus.CONFIRMED);
        ResponseEntity<ApiResponse<OrderDetailResponse>> reloaded = restTemplate.exchange(
                "/api/v1/orders/" + order.id(), HttpMethod.GET, new HttpEntity<>(auth(user)),
                new ParameterizedTypeReference<ApiResponse<OrderDetailResponse>>() {
                });
        assertThat(reloaded.getBody().data().status()).isEqualTo(OrderStatus.PAYMENT_VERIFIED);

        // Le reglement peut bien etre cree pour cet ordre -- jamais "un reglement existe deja".
        SettlementResponse settlement = createSettlement(admin, order.id());
        assertThat(settlement.orderId()).isEqualTo(order.id());
    }

    @Test
    void resubmitPayment_neverTouchesTheStillHeldTreasuryReservation() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));
        QuoteResponse quote = createAcceptedQuote(user, "100000");
        OrderDetailResponse order = createOrder(user, quote.id(), alipayBeneficiary());
        PaymentResponse firstAttempt = submitPayment(user, order.id(), "100000", "MM-REF-TREASURY-1");
        uploadProof(user, firstAttempt.id());
        rejectPayment(admin, firstAttempt.id(), "preuve illisible");

        BigDecimal cnyAvailableBeforeResubmit = treasurySnapshot(admin, Currency.CNY).available();

        submitPayment(user, order.id(), "100000", "MM-REF-TREASURY-2");

        // Ni reduite (pas de deuxieme reservation -- interdite par uq_treasury_tx_reservation_
        // per_order) ni augmentee (jamais liberee au rejet) : totalement inchangee.
        BigDecimal cnyAvailableAfterResubmit = treasurySnapshot(admin, Currency.CNY).available();
        assertThat(cnyAvailableAfterResubmit).isEqualByComparingTo(cnyAvailableBeforeResubmit);
    }

    /**
     * Regression : la preuve etait toujours servie en {@code application/octet-stream}
     * (jamais le type reel, verifie par signature binaire a l'upload), forcant un
     * telechargement generique la ou l'admin doit pouvoir visualiser l'image en ligne pour
     * verifier le paiement avant de confirmer/rejeter.
     */
    @Test
    void adminDownloadProof_returnsTheRealVerifiedContentType() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));
        QuoteResponse quote = createAcceptedQuote(user, "50000");
        OrderDetailResponse order = createOrder(user, quote.id(), alipayBeneficiary());
        PaymentResponse payment = submitPayment(user, order.id(), "50000", "MM-REF-PROOFVIEW");
        PaymentProofResponse proof = uploadProof(user, payment.id());

        ResponseEntity<byte[]> response = restTemplate.exchange(
                "/api/admin/payments/" + payment.id() + "/proofs/" + proof.id(), HttpMethod.GET,
                new HttpEntity<>(auth(admin)), byte[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_JPEG);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).isEqualTo("inline");
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
