package com.converter.order.service;

import com.converter.quote.dto.QuoteResponse;
import com.converter.support.AbstractOrderPipelineIT;
import com.converter.user.domain.RoleCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Passe 2, §16 : plusieurs threads tentent de creer un ordre a partir du <b>meme</b> devis
 * accepte. Exactement un doit reussir ; tous les autres recoivent {@code 409 QUOTE_ALREADY_USED}
 * (code metier precis, jamais un {@code DUPLICATE_RESOURCE} generique — passe 2, P2-6).
 *
 * <p>Puisqu'un seul ordre est cree et que la reservation CNY est ecrite <b>dans la meme
 * transaction</b> que l'insertion de l'ordre (et protegee par {@code uq_treasury_tx_reservation_
 * per_order}), il ne peut exister qu'une seule reservation : « exactement un ordre » implique
 * « exactement une reservation ».
 */
class OrderConcurrencyIT extends AbstractOrderPipelineIT {

    @Test
    void concurrentOrderCreation_fromTheSameQuote_producesExactlyOneOrder() throws InterruptedException {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));
        QuoteResponse quote = createAcceptedQuote(user, "100000");

        int attempts = 8;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger created = new AtomicInteger();
        AtomicInteger alreadyUsed = new AtomicInteger();
        AtomicInteger otherOutcome = new AtomicInteger();

        try {
            var futures = IntStream.range(0, attempts)
                    .mapToObj(i -> pool.submit(() -> {
                        ready.countDown();
                        awaitUninterruptibly(start);
                        HttpHeaders headers = auth(user);
                        headers.setContentType(MediaType.APPLICATION_JSON);
                        ResponseEntity<String> response = restTemplate.exchange(
                                "/api/v1/orders", HttpMethod.POST,
                                new HttpEntity<>("{\"quoteId\":\"" + quote.id() + "\",\"beneficiary\":{"
                                        + "\"type\":\"ALIPAY\",\"fullName\":\"Zhang San\","
                                        + "\"identifier\":\"zhang.san@example.com\"},\"note\":\"c\"}", headers),
                                String.class);
                        if (response.getStatusCode() == HttpStatus.CREATED) {
                            created.incrementAndGet();
                        } else if (response.getStatusCode() == HttpStatus.CONFLICT
                                && response.getBody() != null && response.getBody().contains("QUOTE_ALREADY_USED")) {
                            alreadyUsed.incrementAndGet();
                        } else {
                            otherOutcome.incrementAndGet();
                        }
                    }))
                    .toList();
            ready.await(10, TimeUnit.SECONDS);
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
        assertThat(alreadyUsed.get()).isEqualTo(attempts - 1);
        assertThat(otherOutcome.get()).isZero();
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
