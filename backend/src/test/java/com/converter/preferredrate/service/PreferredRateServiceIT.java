package com.converter.preferredrate.service;

import com.converter.common.exception.BusinessException;
import com.converter.notification.repository.NotificationRepository;
import com.converter.preferredrate.domain.Exchange;
import com.converter.preferredrate.domain.ExchangeStatus;
import com.converter.preferredrate.domain.PreferredRateDirection;
import com.converter.preferredrate.domain.PreferredRateRequest;
import com.converter.preferredrate.domain.PreferredRateStatus;
import com.converter.preferredrate.dto.CreatePreferredRateRequest;
import com.converter.preferredrate.dto.PreferredRateRequestResponse;
import com.converter.preferredrate.repository.ExchangeRepository;
import com.converter.preferredrate.repository.PreferredRateRequestRepository;
import com.converter.support.AbstractRateQuoteIT;
import com.converter.user.domain.RoleCode;
import com.converter.wallet.dto.WalletResponse;
import com.converter.wallet.service.WalletService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifie {@link PreferredRateService} : creation (reservation Wallet),
 * taux non atteint, taux atteint (declenchement), expiration a J+3,
 * annulation, concurrence et non-reexecution.
 *
 * <p>Les cas d'expiration/concurrence de deadline ne font jamais
 * attendre 3 jours reels : ils construisent directement, via le
 * repository, une demande dont {@code expiresAt} est deja dans le
 * passe -- meme principe que {@code QuoteTest} pour l'expiration a 30
 * minutes, transpose au niveau integration puisque {@code processOne}
 * compare cette valeur au Clock reel du contexte.
 */
class PreferredRateServiceIT extends AbstractRateQuoteIT {

    @Autowired
    private PreferredRateService preferredRateService;

    @Autowired
    private PreferredRateRequestRepository requestRepository;

    @Autowired
    private ExchangeRepository exchangeRepository;

    @Autowired
    private WalletService walletService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    void create_reservesWalletAmountImmediately() {
        UUID userId = createUser(RoleCode.USER).getId();
        walletService.deposit(userId, new BigDecimal("1000"), "seed");

        PreferredRateRequestResponse response = preferredRateService.create(
                new CreatePreferredRateRequest(PreferredRateDirection.XOF_TO_CNY, new BigDecimal("400"), new BigDecimal("85")),
                userId);

        assertThat(response.status()).isEqualTo(PreferredRateStatus.ACTIVE);
        WalletResponse wallet = walletService.snapshot(userId);
        assertThat(wallet.reservedBalance()).isEqualByComparingTo("400.00");
        assertThat(wallet.available()).isEqualByComparingTo("600.00");
        assertThat(response.expiresAt()).isEqualTo(response.createdAt().plus(PreferredRateService.VALIDITY));
    }

    @Test
    void create_withoutSufficientWalletBalance_throwsAndDoesNotPersist() {
        UUID userId = createUser(RoleCode.USER).getId();

        assertThatThrownBy(() -> preferredRateService.create(
                new CreatePreferredRateRequest(PreferredRateDirection.XOF_TO_CNY, new BigDecimal("400"), new BigDecimal("85")),
                userId))
                .isInstanceOf(BusinessException.class);

        assertThat(requestRepository.findByUserIdOrderByCreatedAtDesc(userId, org.springframework.data.domain.PageRequest.of(0, 10)))
                .isEmpty();
    }

    @Test
    void processOne_whenTargetNotReached_staysActiveAndFundsRemainReserved() {
        UUID userId = createUser(RoleCode.USER).getId();
        walletService.deposit(userId, new BigDecimal("1000"), "seed");
        resetMarginToZero();
        publishRate(adminToken(), "85.000000");

        // Cible superieure au taux courant (85) : currentRate < targetRate,
        // donc en attente -- ne declenche jamais avant que le taux
        // courant n'atteigne 90.
        PreferredRateRequestResponse created = preferredRateService.create(
                new CreatePreferredRateRequest(PreferredRateDirection.XOF_TO_CNY, new BigDecimal("400"), new BigDecimal("90")),
                userId);

        preferredRateService.processOne(created.id());

        PreferredRateRequest reloaded = requestRepository.findById(created.id()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(PreferredRateStatus.ACTIVE);
        WalletResponse wallet = walletService.snapshot(userId);
        assertThat(wallet.reservedBalance()).isEqualByComparingTo("400.00");
    }

    @Test
    void processOne_whenTargetReached_triggersExchangeAndDebitsWallet() {
        UUID userId = createUser(RoleCode.USER).getId();
        walletService.deposit(userId, new BigDecimal("1000"), "seed");
        resetMarginToZero();
        publishRate(adminToken(), "85.000000");

        // Cible <= taux courant (85) : currentRate >= targetRate, declenche des le premier passage.
        PreferredRateRequestResponse created = preferredRateService.create(
                new CreatePreferredRateRequest(PreferredRateDirection.XOF_TO_CNY, new BigDecimal("400"), new BigDecimal("80")),
                userId);

        preferredRateService.processOne(created.id());

        PreferredRateRequest reloaded = requestRepository.findById(created.id()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(PreferredRateStatus.EXECUTED);
        assertThat(reloaded.getAchievedRate()).isEqualByComparingTo("85.000000");
        assertThat(reloaded.getExchangeId()).isNotNull();

        Exchange exchange = exchangeRepository.findById(reloaded.getExchangeId()).orElseThrow();
        assertThat(exchange.getStatus()).isEqualTo(ExchangeStatus.STARTED);
        assertThat(exchange.getAmountXof()).isEqualByComparingTo("400.00");

        WalletResponse wallet = walletService.snapshot(userId);
        assertThat(wallet.balance()).isEqualByComparingTo("600.00");
        assertThat(wallet.reservedBalance()).isEqualByComparingTo("0.00");

        assertThat(notificationRepository.countByUserIdAndReadAtIsNull(userId)).isGreaterThanOrEqualTo(2);
    }

    /**
     * Verifie exactement la matrice de bornes demandee : cible fixe a
     * 90, taux courant variable (marge remise a zero pour que
     * {@code currentRate == marketRate} exactement, sans arrondi de
     * marge a gerer). Chaque cas utilise sa propre demande.
     */
    @Test
    void boundaryComparison_onlyTriggersAtOrAboveTarget() {
        UUID userId = createUser(RoleCode.USER).getId();
        walletService.deposit(userId, new BigDecimal("10000"), "seed");
        resetMarginToZero();
        BigDecimal target = new BigDecimal("90");

        assertWaiting(userId, target, "86.275000");
        assertWaiting(userId, target, "89.999000");
        assertTriggered(userId, target, "90.000000");
        assertTriggered(userId, target, "90.000001");
    }

    private void assertWaiting(UUID userId, BigDecimal target, String marketRate) {
        publishRate(adminToken(), marketRate);
        PreferredRateRequestResponse created = preferredRateService.create(
                new CreatePreferredRateRequest(PreferredRateDirection.XOF_TO_CNY, new BigDecimal("100"), target), userId);

        preferredRateService.processOne(created.id());

        PreferredRateRequest reloaded = requestRepository.findById(created.id()).orElseThrow();
        assertThat(reloaded.getStatus())
                .as("taux courant %s vs cible %s doit rester en attente", marketRate, target)
                .isEqualTo(PreferredRateStatus.ACTIVE);
        preferredRateService.cancel(created.id(), userId); // libere la reservation pour le cas suivant
    }

    private void assertTriggered(UUID userId, BigDecimal target, String marketRate) {
        publishRate(adminToken(), marketRate);
        PreferredRateRequestResponse created = preferredRateService.create(
                new CreatePreferredRateRequest(PreferredRateDirection.XOF_TO_CNY, new BigDecimal("100"), target), userId);

        preferredRateService.processOne(created.id());

        PreferredRateRequest reloaded = requestRepository.findById(created.id()).orElseThrow();
        assertThat(reloaded.getStatus())
                .as("taux courant %s vs cible %s doit declencher", marketRate, target)
                .isEqualTo(PreferredRateStatus.EXECUTED);
    }

    /** Une demande deja EXECUTED n'est jamais reexecutee, meme si le scheduler la reevalue. */
    @Test
    void processOne_onAlreadyExecutedRequest_isNoOp() {
        UUID userId = createUser(RoleCode.USER).getId();
        walletService.deposit(userId, new BigDecimal("1000"), "seed");
        resetMarginToZero();
        publishRate(adminToken(), "85.000000");
        PreferredRateRequestResponse created = preferredRateService.create(
                new CreatePreferredRateRequest(PreferredRateDirection.XOF_TO_CNY, new BigDecimal("400"), new BigDecimal("80")),
                userId);
        preferredRateService.processOne(created.id());
        WalletResponse afterFirstTrigger = walletService.snapshot(userId);

        preferredRateService.processOne(created.id());

        WalletResponse afterSecondCall = walletService.snapshot(userId);
        assertThat(afterSecondCall.balance()).isEqualByComparingTo(afterFirstTrigger.balance());
        PreferredRateRequest reloaded = requestRepository.findById(created.id()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(PreferredRateStatus.EXECUTED);
    }

    @Test
    void processOne_pastDeadline_expiresAndReleasesFunds() {
        UUID userId = createUser(RoleCode.USER).getId();
        walletService.deposit(userId, new BigDecimal("1000"), "seed");
        Instant past = Instant.now().minus(4, ChronoUnit.DAYS);
        PreferredRateRequest request = requestRepository.save(new PreferredRateRequest(
                userId, PreferredRateDirection.XOF_TO_CNY, new BigDecimal("400"), new BigDecimal("85"),
                past, past.plus(PreferredRateService.VALIDITY)));
        walletService.reserve(userId, new BigDecimal("400"), request.getId(), "fixture");

        preferredRateService.processOne(request.getId());

        PreferredRateRequest reloaded = requestRepository.findById(request.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(PreferredRateStatus.EXPIRED);
        WalletResponse wallet = walletService.snapshot(userId);
        assertThat(wallet.reservedBalance()).isEqualByComparingTo("0.00");
        assertThat(wallet.balance()).isEqualByComparingTo("1000.00");
    }

    @Test
    void cancel_releasesReservedFunds() {
        UUID userId = createUser(RoleCode.USER).getId();
        walletService.deposit(userId, new BigDecimal("1000"), "seed");
        resetMarginToZero();
        publishRate(adminToken(), "85.000000");
        PreferredRateRequestResponse created = preferredRateService.create(
                new CreatePreferredRateRequest(PreferredRateDirection.XOF_TO_CNY, new BigDecimal("400"), new BigDecimal("90")),
                userId);

        PreferredRateRequestResponse cancelled = preferredRateService.cancel(created.id(), userId);

        assertThat(cancelled.status()).isEqualTo(PreferredRateStatus.CANCELLED);
        WalletResponse wallet = walletService.snapshot(userId);
        assertThat(wallet.reservedBalance()).isEqualByComparingTo("0.00");
        assertThat(wallet.available()).isEqualByComparingTo("1000.00");
    }

    /**
     * Concurrence reelle : deux threads appellent {@code processOne} en
     * meme temps sur la meme demande, une fois le taux atteint. Le
     * verrou pessimiste serialise les deux tentatives ; une seule
     * declenche effectivement l'echange (un seul Exchange cree, un seul
     * debit) -- un meme montant n'est jamais depense deux fois.
     */
    @Test
    void concurrentProcessOne_triggersExactlyOnce() throws InterruptedException {
        UUID userId = createUser(RoleCode.USER).getId();
        walletService.deposit(userId, new BigDecimal("1000"), "seed");
        resetMarginToZero();
        publishRate(adminToken(), "85.000000");
        PreferredRateRequestResponse created = preferredRateService.create(
                new CreatePreferredRateRequest(PreferredRateDirection.XOF_TO_CNY, new BigDecimal("400"), new BigDecimal("80")),
                userId);

        int attempts = 6;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger errors = new AtomicInteger();
        try {
            var futures = IntStream.range(0, attempts)
                    .mapToObj(i -> pool.submit(() -> {
                        awaitUninterruptibly(start);
                        try {
                            preferredRateService.processOne(created.id());
                        } catch (RuntimeException ex) {
                            errors.incrementAndGet();
                        }
                    }))
                    .toList();
            start.countDown();
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } catch (Exception ex) {
            throw new AssertionError("Echec de l'orchestration concurrente", ex);
        } finally {
            pool.shutdown();
        }

        assertThat(errors.get()).isZero();
        PreferredRateRequest reloaded = requestRepository.findById(created.id()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(PreferredRateStatus.EXECUTED);

        WalletResponse wallet = walletService.snapshot(userId);
        // Un seul debit de 400 a eu lieu : solde = 1000 - 400, jamais moins.
        assertThat(wallet.balance()).isEqualByComparingTo("600.00");
        assertThat(wallet.reservedBalance()).isEqualByComparingTo("0.00");
    }

    /** Notifications de progression T+45/T+90/fin : jamais de doublon, meme si le scheduler repasse plusieurs fois. */
    @Test
    void progressOne_sendsEachProgressNotificationAtMostOnce() {
        UUID userId = createUser(RoleCode.USER).getId();
        UUID requestId = persistDummyPreferredRateRequest(userId);
        Instant start = Instant.now().minusSeconds(46 * 60);
        Exchange exchange = exchangeRepository.save(new Exchange(userId, requestId,
                new BigDecimal("400.00"), new BigDecimal("85.000000"), new BigDecimal("4.70"), start));

        long unreadBefore = notificationRepository.countByUserIdAndReadAtIsNull(userId);
        preferredRateService.progressOne(exchange.getId());
        preferredRateService.progressOne(exchange.getId());
        preferredRateService.progressOne(exchange.getId());
        long unreadAfter = notificationRepository.countByUserIdAndReadAtIsNull(userId);

        assertThat(unreadAfter - unreadBefore).isEqualTo(1);
        Exchange reloaded = exchangeRepository.findById(exchange.getId()).orElseThrow();
        assertThat(reloaded.getProgress45SentAt()).isNotNull();
        assertThat(reloaded.getProgress90SentAt()).isNull();
    }

    @Test
    void progressOne_at2Hours_completesExchangeAndNotifies() {
        UUID userId = createUser(RoleCode.USER).getId();
        UUID requestId = persistDummyPreferredRateRequest(userId);
        Instant start = Instant.now().minusSeconds(121 * 60);
        Exchange exchange = exchangeRepository.save(new Exchange(userId, requestId,
                new BigDecimal("400.00"), new BigDecimal("85.000000"), new BigDecimal("4.70"), start));

        preferredRateService.progressOne(exchange.getId());

        Exchange reloaded = exchangeRepository.findById(exchange.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ExchangeStatus.COMPLETED);
        assertThat(reloaded.getCompletedAt()).isNotNull();
    }

    /** Satisfait la contrainte de cle etrangere {@code fk_exchanges_preferred_rate_request} pour les fixtures d'Exchange isole. */
    private UUID persistDummyPreferredRateRequest(UUID userId) {
        Instant now = Instant.now();
        return requestRepository.save(new PreferredRateRequest(userId, PreferredRateDirection.XOF_TO_CNY,
                new BigDecimal("400.00"), new BigDecimal("85.000000"), now, now.plus(PreferredRateService.VALIDITY)))
                .getId();
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
