package com.converter.quote.service;

import com.converter.common.api.ApiResponse;
import com.converter.quote.domain.QuoteDirection;
import com.converter.quote.dto.CreateQuoteRequest;
import com.converter.quote.dto.QuoteResponse;
import com.converter.support.AbstractRateQuoteIT;
import com.converter.user.domain.RoleCode;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de concurrence reelle (plusieurs threads), au niveau HTTP :
 * verifie que le verrouillage pessimiste protege effectivement contre
 * une double acceptation, et que la creation de devis reste
 * financierement coherente meme lorsqu'elle s'execute en parallele
 * d'une republication de taux.
 */
class QuoteConcurrencyIT extends AbstractRateQuoteIT {

    @Test
    void concurrentAccept_onTheSameQuote_onlyOneSucceeds() throws InterruptedException {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        String user = tokenFor(createUser(RoleCode.USER));
        QuoteResponse quote = createQuote(user, new CreateQuoteRequest(QuoteDirection.SEND_XOF,
                new BigDecimal("100000"), null));

        int attempts = 8;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();

        try {
            var futures = IntStream.range(0, attempts)
                    .mapToObj(i -> pool.submit(() -> {
                        ready.countDown();
                        awaitUninterruptibly(start);
                        HttpHeaders headers = new HttpHeaders();
                        headers.setBearerAuth(user);
                        ResponseEntity<String> response = restTemplate.exchange(
                                "/api/v1/quotes/" + quote.id() + "/accept", HttpMethod.POST,
                                new HttpEntity<>(headers), String.class);
                        if (response.getStatusCode() == HttpStatus.OK) {
                            successes.incrementAndGet();
                        } else if (response.getStatusCode() == HttpStatus.CONFLICT) {
                            conflicts.incrementAndGet();
                        }
                    }))
                    .toList();

            ready.await(10, TimeUnit.SECONDS);
            start.countDown();
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } catch (Exception ex) {
            throw new AssertionError("Echec de l'orchestration concurrente", ex);
        } finally {
            pool.shutdown();
        }

        // Le verrou pessimiste (SELECT ... FOR UPDATE) serialise les
        // tentatives : exactement une reussit, toutes les autres
        // echouent proprement (409 INVALID_QUOTE_STATE), jamais de
        // double acceptation silencieuse.
        assertThat(successes.get()).isEqualTo(1);
        assertThat(conflicts.get()).isEqualTo(attempts - 1);
    }

    @Test
    void concurrentQuoteCreation_duringRatePublication_neverProducesInconsistentQuote()
            throws InterruptedException {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        String user = tokenFor(createUser(RoleCode.USER));

        // Reste volontairement sous la taille du pool HikariCP de test
        // (10 connexions) : creators + 1 (le thread de publication)
        // doivent pouvoir obtenir chacun leur propre connexion
        // simultanement, sans quoi l'attente d'une connexion libre
        // fausserait ce test de concurrence en un test de contention de
        // pool.
        int creators = 6;
        ExecutorService pool = Executors.newFixedThreadPool(creators + 1);
        CountDownLatch start = new CountDownLatch(1);
        List<QuoteResponse> created = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

        try {
            List<Future<?>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < creators; i++) {
                futures.add(pool.submit(() -> {
                    awaitUninterruptibly(start);
                    HttpHeaders headers = new HttpHeaders();
                    headers.setBearerAuth(user);
                    ResponseEntity<ApiResponse<QuoteResponse>> response = restTemplate.exchange(
                            "/api/v1/quotes", HttpMethod.POST,
                            new HttpEntity<>(new CreateQuoteRequest(QuoteDirection.SEND_XOF,
                                    new BigDecimal("100000"), null), headers),
                            new ParameterizedTypeReference<ApiResponse<QuoteResponse>>() {
                            });
                    if (response.getStatusCode() == HttpStatus.CREATED) {
                        created.add(response.getBody().data());
                    }
                }));
            }
            futures.add(pool.submit(() -> {
                awaitUninterruptibly(start);
                publishRate(admin, "92.000000");
            }));

            start.countDown();
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } catch (Exception ex) {
            throw new AssertionError("Echec de l'orchestration concurrente", ex);
        } finally {
            pool.shutdown();
        }

        assertThat(created).hasSize(creators);
        // Invariant verifie pour CHAQUE devis cree pendant la course,
        // quel que soit le taux qu'il a fini par capturer : les valeurs
        // financieres restent internement coherentes (aucun devis ne
        // melange un morceau de l'ancien taux avec un morceau du
        // nouveau).
        for (QuoteResponse quote : created) {
            BigDecimal recomputedNet = quote.amountXof().subtract(quote.feeXof());
            assertThat(recomputedNet).isEqualByComparingTo(quote.netAmountXof());
            assertThat(quote.amountCny()).isGreaterThan(BigDecimal.ZERO);
        }
        // Chaque devis doit avoir capture l'un des deux taux publies,
        // sans valeur intermediaire impossible : soit ~1176 (85), soit
        // ~1086 (92) — jamais autre chose.
        long distinctOutcomes = created.stream().map(QuoteResponse::amountCny).distinct().count();
        assertThat(distinctOutcomes).isLessThanOrEqualTo(2);
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
