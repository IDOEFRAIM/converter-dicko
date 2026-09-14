package com.converter.payment.service;

import com.converter.order.dto.OrderDetailResponse;
import com.converter.payment.dto.PaymentResponse;
import com.converter.quote.dto.QuoteResponse;
import com.converter.support.AbstractOrderPipelineIT;
import com.converter.treasury.domain.Currency;
import com.converter.user.domain.RoleCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de concurrence reelle (plusieurs threads) sur {@code payment}, symetriques a
 * {@code OrderConcurrencyIT} (creation d'ordre) et
 * {@code SettlementFlowIT#concurrentExecute_ofTheSameSettlement_consumesTreasuryExactlyOnce}
 * (execution de reglement) : ces deux derniers verifient deja, sous course reelle, qu'un double
 * effet est impossible a leur etape respective. {@code Payment.confirm()} declenche lui aussi un
 * mouvement de tresorerie (depot XOF) et une transition d'ordre irreversibles ; il n'existait
 * jusqu'ici qu'un test <b>sequentiel</b> ({@code confirm_anAlreadyConfirmedPayment_returnsConflict})
 * pour cette etape — cette classe ferme cet ecart de couverture avec de vraies courses.
 */
class PaymentConcurrencyIT extends AbstractOrderPipelineIT {

    @Test
    void concurrentSubmitPayment_forTheSameOrder_producesExactlyOnePayment() throws InterruptedException {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));
        QuoteResponse quote = createAcceptedQuote(user, "100000");
        OrderDetailResponse order = createOrder(user, quote.id(), alipayBeneficiary());

        int attempts = 8;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger submitted = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();

        try {
            var futures = IntStream.range(0, attempts)
                    .mapToObj(i -> pool.submit(() -> {
                        awaitUninterruptibly(start);
                        // References distinctes : la course doit se decider sur uq_payments_order,
                        // pas accidentellement sur uq_payments_txref.
                        ResponseEntity<String> response = submitPaymentRawAsString(user, order.id(), "100000",
                                "MM-CONC-" + i);
                        if (response.getStatusCode() == HttpStatus.CREATED) {
                            submitted.incrementAndGet();
                        } else if (response.getStatusCode() == HttpStatus.CONFLICT) {
                            conflicts.incrementAndGet();
                        }
                    }))
                    .toList();
            start.countDown();
            for (var f : futures) {
                f.get(30, TimeUnit.SECONDS);
            }
        } catch (Exception ex) {
            throw new AssertionError("Echec de l'orchestration concurrente", ex);
        } finally {
            pool.shutdown();
        }

        assertThat(submitted.get()).isEqualTo(1);
        assertThat(conflicts.get()).isEqualTo(attempts - 1);
    }

    @Test
    void concurrentConfirm_ofTheSamePayment_depositsTreasuryExactlyOnceAndCompletesOrderOnce()
            throws InterruptedException {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));
        QuoteResponse quote = createAcceptedQuote(user, "100000");
        OrderDetailResponse order = createOrder(user, quote.id(), alipayBeneficiary());
        PaymentResponse payment = submitPayment(user, order.id(), "100000", "MM-CONC-CONFIRM-1");
        uploadProof(user, payment.id());

        BigDecimal xofBefore = treasurySnapshot(admin, Currency.XOF).balance();

        int attempts = 8;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger confirmed = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();

        try {
            var futures = IntStream.range(0, attempts)
                    .mapToObj(i -> pool.submit(() -> {
                        awaitUninterruptibly(start);
                        HttpHeaders headers = auth(admin);
                        ResponseEntity<String> response = restTemplate.exchange(
                                "/api/admin/payments/" + payment.id() + "/confirm", HttpMethod.POST,
                                new HttpEntity<>(headers), String.class);
                        if (response.getStatusCode() == HttpStatus.OK) {
                            confirmed.incrementAndGet();
                        } else if (response.getStatusCode() == HttpStatus.CONFLICT) {
                            conflicts.incrementAndGet();
                        }
                    }))
                    .toList();
            start.countDown();
            for (var f : futures) {
                f.get(30, TimeUnit.SECONDS);
            }
        } catch (Exception ex) {
            throw new AssertionError("Echec de l'orchestration concurrente", ex);
        } finally {
            pool.shutdown();
        }

        // Exactement une confirmation reussit : le verrou pessimiste (findByIdForUpdate) serialise
        // les tentatives, et Payment.requireSubmitted() rejette toutes les suivantes (statut deja
        // CONFIRMED des que la premiere a commit) — jamais de double depot, jamais de double
        // transition d'ordre.
        assertThat(confirmed.get()).isEqualTo(1);
        assertThat(conflicts.get()).isEqualTo(attempts - 1);

        BigDecimal xofAfter = treasurySnapshot(admin, Currency.XOF).balance();
        assertThat(xofAfter.subtract(xofBefore)).isEqualByComparingTo("100000.00");
    }

    private ResponseEntity<String> submitPaymentRawAsString(String userToken, java.util.UUID orderId,
                                                             String receivedAmountXof, String reference) {
        HttpHeaders headers = auth(userToken);
        String body = "{\"method\":\"MOBILE_MONEY\",\"receivedAmountXof\":" + receivedAmountXof
                + ",\"transactionReference\":\"" + reference
                + "\",\"payerPhone\":\"+2250700000000\",\"payerName\":\"Payeur Test\"}";
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return restTemplate.exchange("/api/v1/orders/" + orderId + "/payments", HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
