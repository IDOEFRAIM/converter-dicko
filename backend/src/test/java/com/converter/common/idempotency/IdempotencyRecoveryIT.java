package com.converter.common.idempotency;

import com.converter.common.api.ApiResponse;
import com.converter.common.exception.BusinessException;
import com.converter.common.exception.ErrorCode;
import com.converter.support.AbstractRateQuoteIT;
import com.converter.user.domain.RoleCode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Recuperation des cles d'idempotence restees "pending" apres un crash simule (voir
 * {@link IdempotencyService#reclaimStalePending} pour la preuve de securite transactionnelle, et
 * {@code IdempotencyGuardIT#keyStuckPendingAfterSimulatedCrash_blocksLegitimateRetryForever} pour
 * le probleme initial que cette classe resout).
 *
 * <p>Chaque test appelle {@link IdempotencyService}/{@link IdempotencyGuard} directement plutot
 * que de passer par une vraie panne JVM (impossible a provoquer proprement dans un test) : c'est
 * la maniere la plus fidele de simuler "capture reussie, jamais completee" sans rien inventer sur
 * le mecanisme reel.
 */
class IdempotencyRecoveryIT extends AbstractRateQuoteIT {

    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private IdempotencyGuard idempotencyGuard;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void reclaimStalePending_removesAbandonedKey_allowsCleanRetryWithoutDoubleEffect() throws Exception {
        UUID userId = createUser(RoleCode.USER).getId();
        String endpoint = "TEST /recovery/abandoned";
        String idemKey = "abandoned-" + UUID.randomUUID();
        Object body = new Object[] {"payload"};
        String hash = sha256(objectMapper.writeValueAsString(body));

        // Simule exactement ce qu'un crash laisse derriere lui : capture reussie (commit propre,
        // REQUIRES_NEW), jamais completee.
        idempotencyService.tryInsert(userId, endpoint, idemKey, hash);

        // Un delai nul suffit ici : la ligne vient d'etre inseree, donc deja "plus vieille" que
        // now - 0ms au moment ou la requete s'execute. Le balayage est global (pas filtre par
        // cle) : la base etant partagee par toute la suite, d'autres captures "pending"
        // residuelles peuvent legitimement etre recuperees dans le meme appel — seul le fait que
        // LA NOTRE en fasse partie compte ici, jamais un compte absolu (meme principe que
        // TreasuryServiceIT : raisonner en delta, jamais en valeur absolue partagee).
        int reclaimed = idempotencyService.reclaimStalePending(Duration.ZERO);
        assertThat(reclaimed).isGreaterThanOrEqualTo(1);

        AtomicInteger executions = new AtomicInteger();
        ResponseEntity<ApiResponse<String>> response = idempotencyGuard.guard(userId, endpoint, idemKey, body,
                new TypeReference<ApiResponse<String>>() {
                },
                () -> {
                    executions.incrementAndGet();
                    return ResponseEntity.ok(ApiResponse.of("done"));
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(executions.get()).isEqualTo(1);
    }

    @Test
    void afterRecovery_aThirdAttemptWithTheSameKey_replaysWithoutReExecuting() throws Exception {
        UUID userId = createUser(RoleCode.USER).getId();
        String endpoint = "TEST /recovery/replay-after-recovery";
        String idemKey = "replay-after-recovery-" + UUID.randomUUID();
        Object body = new Object[] {"payload"};
        String hash = sha256(objectMapper.writeValueAsString(body));

        idempotencyService.tryInsert(userId, endpoint, idemKey, hash);
        idempotencyService.reclaimStalePending(Duration.ZERO);

        AtomicInteger executions = new AtomicInteger();
        // Premier passage post-recuperation : execute reellement, comme le test precedent.
        ResponseEntity<ApiResponse<String>> first = idempotencyGuard.guard(userId, endpoint, idemKey, body,
                new TypeReference<ApiResponse<String>>() {
                },
                () -> {
                    executions.incrementAndGet();
                    return ResponseEntity.ok(ApiResponse.of("result-" + executions.get()));
                });

        // Un troisieme passage, meme cle, meme corps : doit rejouer LA REPONSE DU PREMIER
        // PASSAGE POST-RECUPERATION, jamais executer l'action une deuxieme fois — la semantique
        // de rejeu normale (§21, inchangee) s'applique de nouveau normalement une fois la cle
        // recapturee "fraiche".
        ResponseEntity<ApiResponse<String>> replay = idempotencyGuard.guard(userId, endpoint, idemKey, body,
                new TypeReference<ApiResponse<String>>() {
                },
                () -> {
                    executions.incrementAndGet();
                    return ResponseEntity.ok(ApiResponse.of("should-not-run"));
                });

        assertThat(executions.get()).isEqualTo(1);
        assertThat(replay.getBody().data()).isEqualTo(first.getBody().data()).isEqualTo("result-1");
    }

    /**
     * Preuve reproductible de la limite documentee dans {@link IdempotencyService#reclaimStalePending}
     * (audit de fermeture, Partie A) : la garantie transactionnelle protege uniquement contre la
     * suppression d'une operation deja COMMITTEE. Rien n'empeche, structurellement, un reclaim de
     * supprimer une capture dont la transaction metier est encore REELLEMENT en cours (pas
     * crashee, juste lente) — si cela arrive, une requete concurrente avec la meme cle peut
     * capturer une nouvelle ligne et executer une seconde fois. Ce test le demontre
     * deliberement avec {@code Duration.ZERO} (aucune marge de securite) pour rendre la fenetre de
     * course systematique plutot que rare ; en production, le seuil par defaut (15 minutes,
     * largement superieur a la duree reelle de ces operations, qui n'effectuent aucun I/O
     * externe) rend ce scenario improbable, jamais impossible. Voir le rapport d'audit —
     * cette limitation est ACCEPTEE, pas cachee : ce test sert de preuve, pas de regression a
     * corriger.
     */
    @Test
    void knownLimitation_reclaimDuringAGenuinelyStillRunningAction_canProduceADoubleExecution() throws Exception {
        UUID userId = createUser(RoleCode.USER).getId();
        String endpoint = "TEST /recovery/known-limitation";
        String idemKey = "known-limitation-" + UUID.randomUUID();
        Object body = new Object[] {"payload"};

        // Pas de tryInsert() manuel prealable ici (contrairement aux autres tests de cette
        // classe) : la premiere capture doit venir du guard() du thread "lent" lui-meme, pour que
        // son action tourne reellement pendant la fenetre de course, au lieu d'echouer
        // immediatement en IDEMPOTENT_REQUEST_IN_PROGRESS sur une cle deja capturee ailleurs.
        AtomicInteger executions = new AtomicInteger();
        CountDownLatch actionStarted = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            Future<?> slowOriginalRequest = pool.submit(() -> idempotencyGuard.guard(userId, endpoint, idemKey, body,
                    new TypeReference<ApiResponse<String>>() {
                    },
                    () -> {
                        executions.incrementAndGet();
                        actionStarted.countDown();
                        try {
                            // Simule une transaction metier reellement en cours (pas crashee) au
                            // moment ou le reclaim s'execute ci-dessous.
                            Thread.sleep(400);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return ResponseEntity.ok(ApiResponse.of("original"));
                    }));

            // Attend que l'action lente ait reellement demarre avant de reclaim : la fenetre de
            // course est ainsi garantie, pas dependante du timing.
            assertThat(actionStarted.await(5, TimeUnit.SECONDS)).isTrue();
            idempotencyService.reclaimStalePending(Duration.ZERO);

            Future<?> retryRequest = pool.submit(() -> idempotencyGuard.guard(userId, endpoint, idemKey, body,
                    new TypeReference<ApiResponse<String>>() {
                    },
                    () -> {
                        executions.incrementAndGet();
                        return ResponseEntity.ok(ApiResponse.of("retry"));
                    }));

            slowOriginalRequest.get(5, TimeUnit.SECONDS);
            retryRequest.get(5, TimeUnit.SECONDS);
        } finally {
            pool.shutdown();
        }

        // Demonstration deliberee de la limite : avec un seuil nul, l'action s'execute deux fois.
        // C'est exactement pourquoi le seuil de production reste tres largement superieur a la
        // duree reelle de ces operations plutot que nul ou court.
        assertThat(executions.get()).isEqualTo(2);
    }

    @Test
    void reclaimStalePending_neverTouchesACompletedKey() throws Exception {
        UUID userId = createUser(RoleCode.USER).getId();
        String endpoint = "TEST /recovery/completed";
        String idemKey = "completed-" + UUID.randomUUID();
        Object body = new Object[] {"payload"};
        String hash = sha256(objectMapper.writeValueAsString(body));

        idempotencyService.tryInsert(userId, endpoint, idemKey, hash);
        String storedResponseBody = objectMapper.writeValueAsString(ApiResponse.of("already done"));
        idempotencyService.complete(userId, endpoint, idemKey, 200, storedResponseBody);

        int reclaimed = idempotencyService.reclaimStalePending(Duration.ZERO);

        assertThat(reclaimed).isZero();
        // Preuve que la ligne existe toujours et reste rejouable (pas juste "non comptee") :
        // un guard() ulterieur rejoue la reponse d'origine, sans jamais executer l'action.
        AtomicInteger executions = new AtomicInteger();
        ResponseEntity<ApiResponse<String>> replay = idempotencyGuard.guard(userId, endpoint, idemKey, body,
                new TypeReference<ApiResponse<String>>() {
                },
                () -> {
                    executions.incrementAndGet();
                    return ResponseEntity.ok(ApiResponse.of("should not run"));
                });
        assertThat(executions.get()).isZero();
        assertThat(replay.getBody().data()).isEqualTo("already done");
    }

    @Test
    void reclaimStalePending_underProductionDefaultTimeout_neverTouchesAFreshPendingKey() throws Exception {
        UUID userId = createUser(RoleCode.USER).getId();
        String endpoint = "TEST /recovery/fresh";
        String idemKey = "fresh-" + UUID.randomUUID();
        String hash = sha256(objectMapper.writeValueAsString(new Object[] {"payload"}));

        idempotencyService.tryInsert(userId, endpoint, idemKey, hash);

        // Meme delai que le defaut de production (15 min) : une capture qui vient de se produire
        // ne doit jamais etre consideree comme abandonnee.
        int reclaimed = idempotencyService.reclaimStalePending(Duration.ofMinutes(15));

        assertThat(reclaimed).isZero();
        // Toujours "pending" : un rejeu immediat avec la meme cle reste bloque, comme avant cette
        // mission -- le contrat existant (section 21) n'a pas change pour une cle genuinement recente.
        assertThatCode(() -> idempotencyGuard.guard(userId, endpoint, idemKey, new Object[] {"payload"},
                new TypeReference<ApiResponse<String>>() {
                },
                () -> {
                    throw new AssertionError("Ne doit jamais s'executer : la cle est encore pending et fraiche.");
                }))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).errorCode())
                        .isEqualTo(ErrorCode.IDEMPOTENT_REQUEST_IN_PROGRESS));
    }

    /**
     * Section 20 de la mission : cleanup + N tentatives concurrentes sur la MEME cle ne doivent
     * jamais produire plus d'une execution metier reelle. Le reclaim est effectue avant le
     * lancement des threads (capture prealablement abandonnee, aucune execution associee) -- voir
     * la Javadoc de {@link IdempotencyService#reclaimStalePending} pour pourquoi un reclaim ne
     * peut etre prouve sur que dans cette configuration, jamais pendant qu'une action tourne
     * reellement.
     */
    @Test
    void afterReclaim_concurrentRetriesWithTheSameKey_produceAtMostOneBusinessExecution() throws Exception {
        UUID userId = createUser(RoleCode.USER).getId();
        String endpoint = "TEST /recovery/concurrent";
        String idemKey = "concurrent-" + UUID.randomUUID();
        Object body = new Object[] {"payload"};
        String hash = sha256(objectMapper.writeValueAsString(body));

        idempotencyService.tryInsert(userId, endpoint, idemKey, hash);
        assertThat(idempotencyService.reclaimStalePending(Duration.ZERO)).isGreaterThanOrEqualTo(1);

        int attempts = 8;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger executions = new AtomicInteger();
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger inProgress = new AtomicInteger();

        try {
            var futures = IntStream.range(0, attempts)
                    .mapToObj(i -> pool.submit(() -> {
                        awaitUninterruptibly(start);
                        try {
                            idempotencyGuard.guard(userId, endpoint, idemKey, body,
                                    new TypeReference<ApiResponse<String>>() {
                                    },
                                    () -> {
                                        executions.incrementAndGet();
                                        return ResponseEntity.ok(ApiResponse.of("done"));
                                    });
                            successes.incrementAndGet();
                        } catch (BusinessException ex) {
                            if (ex.errorCode() == ErrorCode.IDEMPOTENT_REQUEST_IN_PROGRESS) {
                                inProgress.incrementAndGet();
                            } else {
                                throw ex;
                            }
                        }
                    }))
                    .toList();
            start.countDown();
            for (var f : futures) {
                f.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdown();
        }

        // Que les concurrents perdants aient ete rejoues (reponse deja completee par le gagnant)
        // ou bloques en "in progress" (arrives avant que le gagnant ait complete), l'invariant
        // central tient : l'action metier elle-meme ne s'est executee qu'une seule fois.
        assertThat(executions.get()).isEqualTo(1);
        assertThat(successes.get() + inProgress.get()).isEqualTo(attempts);
    }

    private static String sha256(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
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
