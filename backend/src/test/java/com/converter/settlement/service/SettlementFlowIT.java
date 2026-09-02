package com.converter.settlement.service;

import com.converter.common.api.ApiResponse;
import com.converter.common.api.ErrorResponse;
import com.converter.order.domain.BeneficiaryType;
import com.converter.order.domain.OrderStatus;
import com.converter.order.dto.OrderDetailResponse;
import com.converter.payment.dto.PaymentResponse;
import com.converter.quote.dto.QuoteResponse;
import com.converter.settings.domain.SettingKey;
import com.converter.settlement.domain.SettlementStatus;
import com.converter.settlement.dto.SettlementResponse;
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

class SettlementFlowIT extends AbstractOrderPipelineIT {

    @Test
    void create_beforePaymentVerified_returnsConflict() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));
        QuoteResponse quote = createAcceptedQuote(user, "50000");
        OrderDetailResponse order = createOrder(user, quote.id(), alipayBeneficiary());
        // Ordre encore AWAITING_PAYMENT : aucun paiement soumis.

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/admin/orders/" + order.id() + "/settlement", HttpMethod.POST,
                new HttpEntity<>(auth(admin)), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).isEqualTo("INVALID_ORDER_STATE");
    }

    @Test
    void create_capturesBeneficiarySnapshotIncludingBankFields() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));
        QuoteResponse quote = createAcceptedQuote(user, "100000");
        OrderDetailResponse order = createOrder(user, quote.id(), bankBeneficiary());
        PaymentResponse payment = submitPayment(user, order.id(), "100000", "MM-REF-010");
        uploadProof(user, payment.id());
        confirmPayment(admin, payment.id());

        SettlementResponse settlement = createSettlement(admin, order.id());

        assertThat(settlement.status()).isEqualTo(SettlementStatus.PENDING);
        assertThat(settlement.method()).isEqualTo(BeneficiaryType.CHINESE_BANK_ACCOUNT);
        assertThat(settlement.beneficiaryFullName()).isEqualTo("Li Wei");
        assertThat(settlement.beneficiaryIdentifier()).isEqualTo("6222000000000000");
        assertThat(settlement.beneficiaryBankName()).isEqualTo("Bank of China");
        assertThat(settlement.beneficiaryBankBranch()).isEqualTo("Shanghai Branch");
        assertThat(settlement.amountCny()).isEqualByComparingTo(order.amountCny());

        ResponseEntity<ApiResponse<OrderDetailResponse>> reloaded = restTemplate.exchange(
                "/api/v1/orders/" + order.id(), HttpMethod.GET, new HttpEntity<>(auth(user)),
                new ParameterizedTypeReference<ApiResponse<OrderDetailResponse>>() {
                });
        assertThat(reloaded.getBody().data().status()).isEqualTo(OrderStatus.PROCESSING);
    }

    @Test
    void execute_withoutReference_isRejectedByValidation() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));
        QuoteResponse quote = createAcceptedQuote(user, "50000");
        OrderDetailResponse order = createOrder(user, quote.id(), alipayBeneficiary());
        PaymentResponse payment = submitPayment(user, order.id(), "50000", "MM-REF-011");
        uploadProof(user, payment.id());
        confirmPayment(admin, payment.id());
        SettlementResponse settlement = createSettlement(admin, order.id());

        ResponseEntity<ErrorResponse> validationResponse = restTemplate.exchange(
                "/api/admin/settlements/" + settlement.id() + "/execute", HttpMethod.POST,
                new HttpEntity<>("{\"settlementReference\":\"\"}", jsonHeaders(admin)), ErrorResponse.class);

        assertThat(validationResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void execute_consumesTreasuryAndCompletesOrder() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));
        QuoteResponse quote = createAcceptedQuote(user, "100000");
        OrderDetailResponse order = createOrder(user, quote.id(), alipayBeneficiary());
        PaymentResponse payment = submitPayment(user, order.id(), "100000", "MM-REF-012");
        uploadProof(user, payment.id());
        confirmPayment(admin, payment.id());
        SettlementResponse settlement = createSettlement(admin, order.id());

        BigDecimal cnyAvailableBefore = treasurySnapshot(admin, Currency.CNY).available();
        BigDecimal cnyBalanceBefore = treasurySnapshot(admin, Currency.CNY).balance();

        // Preuve requise avant execution (REQUIRE_PAYMENT_PROOF = true).
        uploadSettlementProof(admin, settlement.id());
        SettlementResponse executed = executeSettlement(admin, settlement.id(), "CNY-PAYOUT-REF-001");

        assertThat(executed.status()).isEqualTo(SettlementStatus.EXECUTED);
        assertThat(executed.settlementReference()).isEqualTo("CNY-PAYOUT-REF-001");

        BigDecimal cnyAvailableAfter = treasurySnapshot(admin, Currency.CNY).available();
        BigDecimal cnyBalanceAfter = treasurySnapshot(admin, Currency.CNY).balance();
        // Consommation : balance ET reserved_balance diminuent du meme montant,
        // le solde disponible (deja net de la reservation) ne bouge donc pas ici,
        // seul le solde total baisse.
        assertThat(cnyBalanceBefore.subtract(cnyBalanceAfter)).isEqualByComparingTo(order.amountCny());
        assertThat(cnyAvailableAfter).isEqualByComparingTo(cnyAvailableBefore);

        ResponseEntity<ApiResponse<OrderDetailResponse>> reloaded = restTemplate.exchange(
                "/api/v1/orders/" + order.id(), HttpMethod.GET, new HttpEntity<>(auth(user)),
                new ParameterizedTypeReference<ApiResponse<OrderDetailResponse>>() {
                });
        assertThat(reloaded.getBody().data().status()).isEqualTo(OrderStatus.COMPLETED);
    }

    /**
     * Regression du constat critique §15-16 de {@code docs/AUDIT_BUSINESS_LOGIC.md} :
     * {@code reserved_balance} est un solde <b>agrege</b> par devise, partage entre tous les
     * ordres. Executer le reglement d'un ordre qui n'a jamais ete reserve (fenetre ou
     * {@code TREASURY_RESERVE_ON_ORDER} etait desactive) ne doit JAMAIS entamer le pool de
     * reservation qui protege les autres ordres — {@code SettlementService.execute} doit sauter
     * la consommation plutot que "d'emprunter" silencieusement sur une reservation d'un tiers.
     */
    @Test
    void execute_whenOrderWasNeverReserved_doesNotBorrowFromSharedReservationPool() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));

        // Ordre A, reserve normalement : sa reservation doit rester intacte tout au long du test.
        QuoteResponse quoteA = createAcceptedQuote(user, "200000");
        createOrder(user, quoteA.id(), alipayBeneficiary());
        BigDecimal reservedAfterA = treasurySnapshot(admin, Currency.CNY).reservedBalance();
        assertThat(reservedAfterA).isGreaterThan(BigDecimal.ZERO);

        // Ordre B, cree pendant une fenetre ou la reservation automatique est desactivee :
        // aucune ecriture RESERVATION n'existe pour cet ordre.
        OrderDetailResponse orderB;
        updateSetting(admin, SettingKey.TREASURY_RESERVE_ON_ORDER, "false");
        try {
            QuoteResponse quoteB = createAcceptedQuote(user, "100000");
            orderB = createOrder(user, quoteB.id(), alipayBeneficiary());
        } finally {
            updateSetting(admin, SettingKey.TREASURY_RESERVE_ON_ORDER, "true");
        }

        PaymentResponse paymentB = submitPayment(user, orderB.id(), "100000", "MM-REF-NORES-1");
        uploadProof(user, paymentB.id());
        confirmPayment(admin, paymentB.id());
        SettlementResponse settlementB = createSettlement(admin, orderB.id());
        uploadSettlementProof(admin, settlementB.id());

        BigDecimal reservedBeforeExecute = treasurySnapshot(admin, Currency.CNY).reservedBalance();

        SettlementResponse executed = executeSettlement(admin, settlementB.id(), "CNY-PAYOUT-NORES-1");

        assertThat(executed.status()).isEqualTo(SettlementStatus.EXECUTED);
        ResponseEntity<ApiResponse<OrderDetailResponse>> reloadedB = restTemplate.exchange(
                "/api/v1/orders/" + orderB.id(), HttpMethod.GET, new HttpEntity<>(auth(user)),
                new ParameterizedTypeReference<ApiResponse<OrderDetailResponse>>() {
                });
        assertThat(reloadedB.getBody().data().status()).isEqualTo(OrderStatus.COMPLETED);

        // Le point verifie : le pool de reservation partage n'a pas bouge, en particulier il n'a
        // pas ete entame pour "payer" la consommation de l'ordre B qui n'y avait jamais contribue.
        BigDecimal reservedAfterExecute = treasurySnapshot(admin, Currency.CNY).reservedBalance();
        assertThat(reservedAfterExecute).isEqualByComparingTo(reservedBeforeExecute);
        assertThat(reservedAfterExecute).isEqualByComparingTo(reservedAfterA);
    }

    @Test
    void execute_aSecondTime_returnsConflict() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));
        QuoteResponse quote = createAcceptedQuote(user, "50000");
        OrderDetailResponse order = createOrder(user, quote.id(), alipayBeneficiary());
        PaymentResponse payment = submitPayment(user, order.id(), "50000", "MM-REF-013");
        uploadProof(user, payment.id());
        confirmPayment(admin, payment.id());
        SettlementResponse settlement = createSettlement(admin, order.id());
        uploadSettlementProof(admin, settlement.id());
        executeSettlement(admin, settlement.id(), "CNY-PAYOUT-REF-002");

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/admin/settlements/" + settlement.id() + "/execute", HttpMethod.POST,
                new HttpEntity<>("{\"settlementReference\":\"CNY-PAYOUT-REF-003\"}", jsonHeaders(admin)),
                ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).isEqualTo("INVALID_SETTLEMENT_STATE");
    }

    /**
     * Passe 2, §16 : plusieurs threads executent le MEME reglement simultanement. Exactement un
     * doit reussir (`EXECUTED`), les autres recoivent `409 INVALID_SETTLEMENT_STATE`. La
     * consommation de tresorerie CNY ne doit avoir lieu qu'une seule fois — garantie par le
     * verrou pessimiste (`findByIdForUpdate`), la garde `Settlement.execute` (statut `PENDING`)
     * et l'index `uq_treasury_tx_resolution_per_order`.
     */
    @Test
    void concurrentExecute_ofTheSameSettlement_consumesTreasuryExactlyOnce() throws InterruptedException {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));
        QuoteResponse quote = createAcceptedQuote(user, "100000");
        OrderDetailResponse order = createOrder(user, quote.id(), alipayBeneficiary());
        PaymentResponse payment = submitPayment(user, order.id(), "100000", "MM-REF-CONC-1");
        uploadProof(user, payment.id());
        confirmPayment(admin, payment.id());
        SettlementResponse settlement = createSettlement(admin, order.id());
        uploadSettlementProof(admin, settlement.id());

        java.math.BigDecimal cnyBalanceBefore = treasurySnapshot(admin, Currency.CNY).balance();

        int attempts = 6;
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(attempts);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger executed = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger conflicts = new java.util.concurrent.atomic.AtomicInteger();
        try {
            var futures = java.util.stream.IntStream.range(0, attempts).mapToObj(i -> pool.submit(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                ResponseEntity<String> r = restTemplate.exchange(
                        "/api/admin/settlements/" + settlement.id() + "/execute", HttpMethod.POST,
                        new HttpEntity<>("{\"settlementReference\":\"CNY-CONC-" + i + "\"}", jsonHeaders(admin)),
                        String.class);
                if (r.getStatusCode() == HttpStatus.OK) {
                    executed.incrementAndGet();
                } else if (r.getStatusCode() == HttpStatus.CONFLICT
                        && r.getBody() != null && r.getBody().contains("INVALID_SETTLEMENT_STATE")) {
                    conflicts.incrementAndGet();
                }
            })).toList();
            start.countDown();
            for (var f : futures) {
                f.get(30, java.util.concurrent.TimeUnit.SECONDS);
            }
        } catch (Exception ex) {
            throw new AssertionError("Echec de l'orchestration concurrente", ex);
        } finally {
            pool.shutdown();
        }

        assertThat(executed.get()).isEqualTo(1);
        assertThat(conflicts.get()).isEqualTo(attempts - 1);

        java.math.BigDecimal cnyBalanceAfter = treasurySnapshot(admin, Currency.CNY).balance();
        assertThat(cnyBalanceBefore.subtract(cnyBalanceAfter)).isEqualByComparingTo(order.amountCny());
    }

    /**
     * Bascule un parametre metier via l'API admin. Prend effet immediatement
     * (le cache de {@code SettingsService} est invalide a l'ecriture), mais reste un etat
     * <b>global partage</b> par toute la suite de tests dans cette JVM : chaque appelant doit
     * imperativement restaurer la valeur d'origine dans un bloc {@code finally}.
     */
    private void updateSetting(String adminToken, SettingKey key, String value) {
        restTemplate.exchange("/api/admin/settings/" + key.name(), HttpMethod.PUT,
                new HttpEntity<>("{\"value\":\"" + value + "\"}", jsonHeaders(adminToken)), String.class);
    }

    private org.springframework.http.HttpHeaders jsonHeaders(String token) {
        org.springframework.http.HttpHeaders headers = auth(token);
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return headers;
    }

    private void uploadSettlementProof(String adminToken, java.util.UUID settlementId) {
        org.springframework.util.MultiValueMap<String, Object> body = new org.springframework.util.LinkedMultiValueMap<>();
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10};
        body.add("file", new org.springframework.core.io.ByteArrayResource(jpeg) {
            @Override
            public String getFilename() {
                return "settlement-proof.jpg";
            }
        });
        org.springframework.http.HttpHeaders headers = auth(adminToken);
        headers.setContentType(org.springframework.http.MediaType.MULTIPART_FORM_DATA);
        restTemplate.exchange("/api/admin/settlements/" + settlementId + "/proofs", HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
    }
}
