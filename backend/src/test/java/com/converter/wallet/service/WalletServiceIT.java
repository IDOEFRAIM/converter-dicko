package com.converter.wallet.service;

import com.converter.common.exception.BusinessException;
import com.converter.common.exception.ErrorCode;
import com.converter.support.AbstractRateQuoteIT;
import com.converter.user.domain.RoleCode;
import com.converter.wallet.dto.WalletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
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
 * Verifie directement {@link WalletService} : credit, reservation,
 * liberation, debit, solde insuffisant, concurrence et double depense.
 * Meme structure de test que {@code TreasuryServiceIT} (Phase 6), le
 * Wallet en reprenant l'architecture (solde/reserve verrouilles par
 * ligne, ledger append-only).
 */
class WalletServiceIT extends AbstractRateQuoteIT {

    @Autowired
    private WalletService walletService;

    @Test
    void deposit_increasesBalanceAndAvailable() {
        UUID userId = createUser(RoleCode.USER).getId();

        WalletResponse after = walletService.deposit(userId, new BigDecimal("1000"), "seed de test");

        assertThat(after.balance()).isEqualByComparingTo("1000.00");
        assertThat(after.reservedBalance()).isEqualByComparingTo("0.00");
        assertThat(after.available()).isEqualByComparingTo("1000.00");
    }

    @Test
    void reserve_reducesAvailableButNotBalance() {
        UUID userId = createUser(RoleCode.USER).getId();
        walletService.deposit(userId, new BigDecimal("1000"), "seed");
        UUID referenceId = UUID.randomUUID();

        walletService.reserve(userId, new BigDecimal("400"), referenceId, "demande taux preferentiel");

        WalletResponse after = walletService.snapshot(userId);
        assertThat(after.balance()).isEqualByComparingTo("1000.00");
        assertThat(after.reservedBalance()).isEqualByComparingTo("400.00");
        assertThat(after.available()).isEqualByComparingTo("600.00");
    }

    @Test
    void release_restoresAvailableWithoutTouchingBalance() {
        UUID userId = createUser(RoleCode.USER).getId();
        walletService.deposit(userId, new BigDecimal("1000"), "seed");
        UUID referenceId = UUID.randomUUID();
        walletService.reserve(userId, new BigDecimal("400"), referenceId, "reservation");

        walletService.release(userId, new BigDecimal("400"), referenceId, "expiration");

        WalletResponse after = walletService.snapshot(userId);
        assertThat(after.balance()).isEqualByComparingTo("1000.00");
        assertThat(after.reservedBalance()).isEqualByComparingTo("0.00");
        assertThat(after.available()).isEqualByComparingTo("1000.00");
    }

    @Test
    void debit_reducesBothBalanceAndReserved() {
        UUID userId = createUser(RoleCode.USER).getId();
        walletService.deposit(userId, new BigDecimal("1000"), "seed");
        UUID referenceId = UUID.randomUUID();
        walletService.reserve(userId, new BigDecimal("400"), referenceId, "reservation");

        walletService.debit(userId, new BigDecimal("400"), referenceId, "echange declenche");

        WalletResponse after = walletService.snapshot(userId);
        assertThat(after.balance()).isEqualByComparingTo("600.00");
        assertThat(after.reservedBalance()).isEqualByComparingTo("0.00");
        assertThat(after.available()).isEqualByComparingTo("600.00");
    }

    @Test
    void reserve_beyondAvailable_throwsAndLeavesWalletUnchanged() {
        UUID userId = createUser(RoleCode.USER).getId();
        walletService.deposit(userId, new BigDecimal("100"), "seed");

        assertThatThrownBy(() -> walletService.reserve(userId, new BigDecimal("500"), UUID.randomUUID(), "trop"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).errorCode())
                        .isEqualTo(ErrorCode.INSUFFICIENT_WALLET_BALANCE));

        WalletResponse after = walletService.snapshot(userId);
        assertThat(after.balance()).isEqualByComparingTo("100.00");
        assertThat(after.reservedBalance()).isEqualByComparingTo("0.00");
    }

    /**
     * Garde d'invariant applicative, symetrique de
     * {@code TreasuryServiceIT#consume_moreThanReserved_isRejectedAndLeavesAccountUnchanged}
     * (voir {@code docs/AUDIT_BUSINESS_LOGIC.md} §18) : un utilisateur ne doit jamais pouvoir
     * etre debite au-dela de ce qui est effectivement reserve.
     */
    @Test
    void debit_moreThanReserved_isRejectedAndLeavesWalletUnchanged() {
        UUID userId = createUser(RoleCode.USER).getId();
        walletService.deposit(userId, new BigDecimal("1000"), "seed");
        UUID referenceId = UUID.randomUUID();
        walletService.reserve(userId, new BigDecimal("100"), referenceId, "reservation");

        assertThatThrownBy(() -> walletService.debit(userId, new BigDecimal("500"), referenceId, "trop"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).errorCode())
                        .isEqualTo(ErrorCode.INSUFFICIENT_WALLET_BALANCE));

        WalletResponse after = walletService.snapshot(userId);
        assertThat(after.balance()).isEqualByComparingTo("1000.00");
        assertThat(after.reservedBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void release_moreThanReserved_isRejectedAndLeavesWalletUnchanged() {
        UUID userId = createUser(RoleCode.USER).getId();
        walletService.deposit(userId, new BigDecimal("1000"), "seed");
        UUID referenceId = UUID.randomUUID();
        walletService.reserve(userId, new BigDecimal("100"), referenceId, "reservation");

        assertThatThrownBy(() -> walletService.release(userId, new BigDecimal("500"), referenceId, "trop"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).errorCode())
                        .isEqualTo(ErrorCode.INSUFFICIENT_WALLET_BALANCE));

        WalletResponse after = walletService.snapshot(userId);
        assertThat(after.balance()).isEqualByComparingTo("1000.00");
        assertThat(after.reservedBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void snapshot_forNewUser_lazilyCreatesZeroBalanceWallet() {
        UUID userId = createUser(RoleCode.USER).getId();

        WalletResponse wallet = walletService.snapshot(userId);

        assertThat(wallet.balance()).isEqualByComparingTo("0.00");
        assertThat(wallet.available()).isEqualByComparingTo("0.00");
    }

    @Test
    void transactions_returnsLedgerNewestFirst() {
        UUID userId = createUser(RoleCode.USER).getId();
        walletService.deposit(userId, new BigDecimal("100"), "premier");
        walletService.deposit(userId, new BigDecimal("50"), "second");

        var page = walletService.transactions(userId, PageRequest.of(0, 10));

        assertThat(page.content()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(page.content().get(0).reason()).isEqualTo("second");
        assertThat(page.content().get(1).reason()).isEqualTo("premier");
    }

    /**
     * Concurrence reelle, meme protocole que
     * {@code TreasuryServiceIT#concurrentReservations_neverExceedAvailableBalance} :
     * plusieurs threads tentent de reserver, sur le meme wallet,
     * davantage au total que le disponible ne le permet. Le verrou
     * pessimiste serialise les tentatives ; la somme des reservations
     * reussies ne doit jamais depasser le disponible initial -- un meme
     * montant n'est jamais depense deux fois.
     */
    @Test
    void concurrentReservations_neverExceedAvailableBalance() throws InterruptedException {
        UUID userId = createUser(RoleCode.USER).getId();
        walletService.deposit(userId, new BigDecimal("1000"), "seed");
        int attempts = 8;
        BigDecimal perAttempt = new BigDecimal("200"); // 8 x 200 = 1600 > 1000 disponible

        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger insufficientFailures = new AtomicInteger();

        try {
            var futures = IntStream.range(0, attempts)
                    .mapToObj(i -> pool.submit(() -> {
                        awaitUninterruptibly(start);
                        try {
                            walletService.reserve(userId, perAttempt, UUID.randomUUID(), "concurrence");
                            successes.incrementAndGet();
                        } catch (BusinessException ex) {
                            if (ex.errorCode() == ErrorCode.INSUFFICIENT_WALLET_BALANCE) {
                                insufficientFailures.incrementAndGet();
                            } else {
                                throw ex;
                            }
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

        assertThat(successes.get()).isEqualTo(5); // 5 x 200 = 1000, le 6e echoue
        assertThat(insufficientFailures.get()).isEqualTo(3);

        WalletResponse after = walletService.snapshot(userId);
        assertThat(after.reservedBalance()).isEqualByComparingTo("1000.00");
        assertThat(after.available()).isEqualByComparingTo("0.00");
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
