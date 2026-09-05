package com.converter.refund;

import com.converter.order.dto.OrderDetailResponse;
import com.converter.payment.dto.PaymentResponse;
import com.converter.quote.dto.QuoteResponse;
import com.converter.refund.dto.RefundResponse;
import com.converter.support.AbstractOrderPipelineIT;
import com.converter.treasury.domain.Currency;
import com.converter.user.domain.RoleCode;
import org.junit.jupiter.api.Test;
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
 * Tests de concurrence reelle (plusieurs threads) sur {@code refund}, symetriques a
 * {@code OrderConcurrencyIT}/{@code PaymentConcurrencyIT}/{@code SettlementFlowIT}
 * (concurrentExecute) : un remboursement (creation puis traitement) ne doit jamais produire deux
 * effets financiers, meme sous course reelle.
 */
class RefundConcurrencyIT extends AbstractOrderPipelineIT {

    @Test
    void concurrentCreate_forTheSamePayment_producesExactlyOneRefund() throws InterruptedException {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));
        QuoteResponse quote = createAcceptedQuote(user, "100000");
        OrderDetailResponse order = createOrder(user, quote.id(), alipayBeneficiary());
        PaymentResponse payment = submitPayment(user, order.id(), "100000", "MM-REFUND-CONC-1");
        uploadProof(user, payment.id());
        confirmPayment(admin, payment.id());

        int attempts = 8;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger created = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();

        try {
            var futures = IntStream.range(0, attempts)
                    .mapToObj(i -> pool.submit(() -> {
                        awaitUninterruptibly(start);
                        ResponseEntity<String> response = restTemplate.exchange(
                                "/api/admin/payments/" + payment.id() + "/refunds", org.springframework.http.HttpMethod.POST,
                                new org.springframework.http.HttpEntity<>("{\"reason\":\"course concurrente " + i + "\"}",
                                        jsonHeaders(admin)), String.class);
                        if (response.getStatusCode() == HttpStatus.CREATED) {
                            created.incrementAndGet();
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

        assertThat(created.get()).isEqualTo(1);
        assertThat(conflicts.get()).isEqualTo(attempts - 1);
    }

    @Test
    void concurrentProcess_ofTheSameRefund_debitsTreasuryExactlyOnce() throws InterruptedException {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));
        QuoteResponse quote = createAcceptedQuote(user, "100000");
        OrderDetailResponse order = createOrder(user, quote.id(), alipayBeneficiary());
        PaymentResponse payment = submitPayment(user, order.id(), "100000", "MM-REFUND-CONC-2");
        uploadProof(user, payment.id());
        confirmPayment(admin, payment.id());
        RefundResponse refund = createRefund(admin, payment.id(), "test concurrence");

        BigDecimal xofBefore = treasurySnapshot(admin, Currency.XOF).balance();

        int attempts = 8;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger processed = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();

        try {
            var futures = IntStream.range(0, attempts)
                    .mapToObj(i -> pool.submit(() -> {
                        awaitUninterruptibly(start);
                        ResponseEntity<String> response = restTemplate.exchange(
                                "/api/admin/refunds/" + refund.id() + "/process", org.springframework.http.HttpMethod.POST,
                                new org.springframework.http.HttpEntity<>(
                                        "{\"transactionReference\":\"MM-OUT-CONC-" + i + "\"}", jsonHeaders(admin)),
                                String.class);
                        if (response.getStatusCode() == HttpStatus.OK) {
                            processed.incrementAndGet();
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

        assertThat(processed.get()).isEqualTo(1);
        assertThat(conflicts.get()).isEqualTo(attempts - 1);

        BigDecimal xofAfter = treasurySnapshot(admin, Currency.XOF).balance();
        assertThat(xofBefore.subtract(xofAfter)).isEqualByComparingTo(refund.amountXof());
    }

    private org.springframework.http.HttpHeaders jsonHeaders(String token) {
        org.springframework.http.HttpHeaders headers = auth(token);
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return headers;
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
