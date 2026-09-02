package com.converter.treasury.service;

import com.converter.common.exception.BusinessException;
import com.converter.common.exception.ErrorCode;
import com.converter.order.domain.Order;
import com.converter.order.repository.OrderRepository;
import com.converter.quote.domain.Quote;
import com.converter.quote.domain.QuoteDirection;
import com.converter.quote.repository.QuoteRepository;
import com.converter.rate.domain.RateProviderType;
import com.converter.rate.domain.RateSource;
import com.converter.rate.engine.PricingResult;
import com.converter.rate.repository.RateSourceRepository;
import com.converter.support.AbstractRateQuoteIT;
import com.converter.treasury.domain.Currency;
import com.converter.treasury.dto.TreasuryAccountResponse;
import com.converter.user.domain.RoleCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
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
 * Verifie directement {@link TreasuryService} : reservation,
 * consommation, liberation, solde insuffisant, double operation,
 * concurrence et atomicite.
 *
 * <p>Le conteneur PostgreSQL est partage par toute la suite
 * (voir {@code AbstractIntegrationTest}) : les comptes de tresorerie
 * accumulent donc l'effet de tous les tests executes avant celui-ci.
 * Chaque test ci-dessous raisonne exclusivement en <b>delta</b>
 * avant/apres, jamais en valeur absolue — c'est la seule maniere de
 * rester correct independamment de l'ordre d'execution de la suite.
 *
 * <p>{@code treasury_transactions.order_id} porte une contrainte de
 * cle etrangere vers {@code orders} : chaque test cree donc un
 * {@code Order} minimal (via les repositories, sans passer par le
 * pipeline HTTP complet) pour disposer d'un identifiant reellement
 * valide, plutot qu'un {@code UUID.randomUUID()} arbitraire.
 */
class TreasuryServiceIT extends AbstractRateQuoteIT {

    @Autowired
    private TreasuryService treasuryService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private RateSourceRepository rateSourceRepository;

    @Autowired
    private QuoteRepository quoteRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void reserve_reducesAvailableButNotBalance() {
        UUID actor = createUser(RoleCode.ADMIN).getId();
        UUID orderId = createDummyOrder(actor);
        seedCny(actor);
        TreasuryAccountResponse before = treasuryService.snapshot(Currency.CNY);

        treasuryService.reserve(Currency.CNY, new BigDecimal("400"), orderId, actor);

        TreasuryAccountResponse after = treasuryService.snapshot(Currency.CNY);
        assertThat(after.balance()).isEqualByComparingTo(before.balance());
        assertThat(after.reservedBalance().subtract(before.reservedBalance())).isEqualByComparingTo("400.00");
        assertThat(before.available().subtract(after.available())).isEqualByComparingTo("400.00");
    }

    @Test
    void consume_reducesBothBalanceAndReserved() {
        UUID actor = createUser(RoleCode.ADMIN).getId();
        UUID orderId = createDummyOrder(actor);
        seedCny(actor);
        treasuryService.reserve(Currency.CNY, new BigDecimal("400"), orderId, actor);
        TreasuryAccountResponse before = treasuryService.snapshot(Currency.CNY);

        treasuryService.consume(Currency.CNY, new BigDecimal("400"), orderId, actor);

        TreasuryAccountResponse after = treasuryService.snapshot(Currency.CNY);
        assertThat(before.balance().subtract(after.balance())).isEqualByComparingTo("400.00");
        assertThat(before.reservedBalance().subtract(after.reservedBalance())).isEqualByComparingTo("400.00");
        // La consommation touche balance ET reserved a parts egales :
        // le disponible (deja net de la reservation) ne bouge pas.
        assertThat(after.available()).isEqualByComparingTo(before.available());
    }

    @Test
    void release_restoresAvailableWithoutTouchingBalance() {
        UUID actor = createUser(RoleCode.ADMIN).getId();
        UUID orderId = createDummyOrder(actor);
        seedCny(actor);
        treasuryService.reserve(Currency.CNY, new BigDecimal("400"), orderId, actor);
        TreasuryAccountResponse before = treasuryService.snapshot(Currency.CNY);

        treasuryService.release(Currency.CNY, new BigDecimal("400"), orderId, actor, "annulation");

        TreasuryAccountResponse after = treasuryService.snapshot(Currency.CNY);
        assertThat(after.balance()).isEqualByComparingTo(before.balance());
        assertThat(before.reservedBalance().subtract(after.reservedBalance())).isEqualByComparingTo("400.00");
        assertThat(after.available().subtract(before.available())).isEqualByComparingTo("400.00");
    }

    @Test
    void reserve_beyondCurrentlyAvailable_throwsAndLeavesAccountUnchanged() {
        UUID actor = createUser(RoleCode.ADMIN).getId();
        UUID orderId = createDummyOrder(actor);
        // Calcule dynamiquement un montant garanti superieur au
        // disponible actuel, quel que soit l'etat accumule par les
        // tests precedents.
        TreasuryAccountResponse before = treasuryService.snapshot(Currency.CNY);
        BigDecimal tooMuch = before.available().add(new BigDecimal("1000000"));

        assertThatThrownBy(() -> treasuryService.reserve(Currency.CNY, tooMuch, orderId, actor))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).errorCode())
                        .isEqualTo(ErrorCode.INSUFFICIENT_TREASURY));

        TreasuryAccountResponse after = treasuryService.snapshot(Currency.CNY);
        assertThat(after.balance()).isEqualByComparingTo(before.balance());
        assertThat(after.reservedBalance()).isEqualByComparingTo(before.reservedBalance());
    }

    @Test
    void doubleReservation_forTheSameOrder_isRejectedByUniqueConstraint() {
        UUID actor = createUser(RoleCode.ADMIN).getId();
        UUID orderId = createDummyOrder(actor);
        seedCny(actor);
        treasuryService.reserve(Currency.CNY, new BigDecimal("100"), orderId, actor);

        // La contrainte uq_treasury_tx_order_type (migration V2) interdit
        // une seconde ecriture RESERVATION pour le meme ordre.
        assertThatThrownBy(() -> treasuryService.reserve(Currency.CNY, new BigDecimal("100"), orderId, actor))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void doubleConsumption_forTheSameOrder_isRejectedByUniqueConstraint() {
        UUID actor = createUser(RoleCode.ADMIN).getId();
        UUID orderId = createDummyOrder(actor);
        seedCny(actor);
        treasuryService.reserve(Currency.CNY, new BigDecimal("300"), orderId, actor);
        treasuryService.consume(Currency.CNY, new BigDecimal("300"), orderId, actor);

        assertThatThrownBy(() -> treasuryService.consume(Currency.CNY, new BigDecimal("1"), orderId, actor))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * Garde d'invariant applicative (voir {@code docs/AUDIT_BUSINESS_LOGIC.md} §15-16) :
     * {@code reserved_balance} est un solde <b>agrege</b> par devise. Tenter de consommer plus
     * que la totalite du pool reserve doit echouer explicitement en {@code INSUFFICIENT_TREASURY}
     * (jamais en violation de contrainte SQL {@code reserved_balance >= 0} traduite en
     * {@code 409 DUPLICATE_RESOURCE} peu explicite), et sans muter le compte.
     *
     * <p>Le montant "trop grand" est calcule dynamiquement au-dela du pool reserve du moment,
     * quel que soit l'etat accumule par les tests precedents dans le conteneur partage.
     */
    @Test
    void consume_moreThanTotalReserved_isRejectedAndLeavesAccountUnchanged() {
        UUID actor = createUser(RoleCode.ADMIN).getId();
        UUID orderId = createDummyOrder(actor);
        seedCny(actor);
        treasuryService.reserve(Currency.CNY, new BigDecimal("100"), orderId, actor);
        TreasuryAccountResponse before = treasuryService.snapshot(Currency.CNY);
        BigDecimal moreThanPool = before.reservedBalance().add(new BigDecimal("1000000"));

        assertThatThrownBy(() -> treasuryService.consume(Currency.CNY, moreThanPool, orderId, actor))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).errorCode())
                        .isEqualTo(ErrorCode.INSUFFICIENT_TREASURY));

        TreasuryAccountResponse after = treasuryService.snapshot(Currency.CNY);
        assertThat(after.balance()).isEqualByComparingTo(before.balance());
        assertThat(after.reservedBalance()).isEqualByComparingTo(before.reservedBalance());
    }

    /** Meme garde, cote liberation. */
    @Test
    void release_moreThanTotalReserved_isRejectedAndLeavesAccountUnchanged() {
        UUID actor = createUser(RoleCode.ADMIN).getId();
        UUID orderId = createDummyOrder(actor);
        seedCny(actor);
        treasuryService.reserve(Currency.CNY, new BigDecimal("100"), orderId, actor);
        TreasuryAccountResponse before = treasuryService.snapshot(Currency.CNY);
        BigDecimal moreThanPool = before.reservedBalance().add(new BigDecimal("1000000"));

        assertThatThrownBy(() -> treasuryService.release(Currency.CNY, moreThanPool, orderId, actor, "test"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).errorCode())
                        .isEqualTo(ErrorCode.INSUFFICIENT_TREASURY));

        TreasuryAccountResponse after = treasuryService.snapshot(Currency.CNY);
        assertThat(after.balance()).isEqualByComparingTo(before.balance());
        assertThat(after.reservedBalance()).isEqualByComparingTo(before.reservedBalance());
    }

    @Test
    void adjust_negativeBeyondBalance_isRejected() {
        UUID actor = createUser(RoleCode.ADMIN).getId();
        TreasuryAccountResponse before = treasuryService.snapshot(Currency.CNY);

        assertThatThrownBy(() -> treasuryService.adjust(Currency.CNY,
                before.balance().add(BigDecimal.ONE).negate(), actor, "correction"))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * Passe 2, P2-5 : un ajustement a la baisse ne doit jamais descendre le solde total sous la
     * part deja reservee (invariant {@code reserved_balance <= balance}). Doit echouer en
     * {@code INSUFFICIENT_TREASURY} — un code metier explicite, jamais un {@code 409
     * DUPLICATE_RESOURCE} issu de la contrainte SQL.
     */
    @Test
    void adjust_belowReservedBalance_isRejectedWithClearCode() {
        UUID actor = createUser(RoleCode.ADMIN).getId();
        UUID orderId = createDummyOrder(actor);
        seedCny(actor);
        treasuryService.reserve(Currency.CNY, new BigDecimal("500"), orderId, actor);
        TreasuryAccountResponse before = treasuryService.snapshot(Currency.CNY);
        // Ramener le solde juste sous la part reservee : delta = -(balance - reserved + 1).
        BigDecimal deltaBelowReserved =
                before.balance().subtract(before.reservedBalance()).add(BigDecimal.ONE).negate();

        assertThatThrownBy(() -> treasuryService.adjust(Currency.CNY, deltaBelowReserved, actor, "correction"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).errorCode())
                        .isEqualTo(ErrorCode.INSUFFICIENT_TREASURY));

        TreasuryAccountResponse after = treasuryService.snapshot(Currency.CNY);
        assertThat(after.balance()).isEqualByComparingTo(before.balance());
        assertThat(after.reservedBalance()).isEqualByComparingTo(before.reservedBalance());
    }

    /**
     * Atomicite : si une operation posterieure a la reservation echoue
     * dans la MEME transaction, la reservation elle-meme doit etre
     * annulee — jamais de reservation "orpheline" en cas d'echec en
     * aval (meme principe que la creation d'un Order : reservation et
     * insertion de l'ordre reussissent ou echouent ensemble).
     */
    @Test
    void reserveInsideAFailingTransaction_isFullyRolledBack() {
        UUID actor = createUser(RoleCode.ADMIN).getId();
        UUID orderId = createDummyOrder(actor);
        seedCny(actor);
        TreasuryAccountResponse before = treasuryService.snapshot(Currency.CNY);

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            treasuryService.reserve(Currency.CNY, new BigDecimal("400"), orderId, actor);
            throw new RuntimeException("echec simule en aval");
        })).isInstanceOf(RuntimeException.class);

        TreasuryAccountResponse after = treasuryService.snapshot(Currency.CNY);
        assertThat(after.balance()).isEqualByComparingTo(before.balance());
        assertThat(after.reservedBalance()).isEqualByComparingTo(before.reservedBalance());
    }

    /**
     * Concurrence reelle : plusieurs threads tentent de reserver, sur
     * des ordres distincts, davantage au total que la liquidite
     * disponible ne le permet. Le verrou pessimiste sur
     * {@code TreasuryAccount} serialise les tentatives ; la somme des
     * reservations reussies ne doit jamais depasser le disponible
     * mesure juste avant le lancement des threads.
     */
    @Test
    void concurrentReservations_neverExceedAvailableBalance() throws InterruptedException {
        UUID actor = createUser(RoleCode.ADMIN).getId();
        int attempts = 8;
        UUID[] orderIds = new UUID[attempts];
        for (int i = 0; i < attempts; i++) {
            orderIds[i] = createDummyOrder(actor);
        }

        TreasuryAccountResponse before = treasuryService.snapshot(Currency.CNY);
        // Un montant par tentative tel que la somme totale depasse tout
        // juste le disponible mesure a l'instant present, quel que soit
        // son niveau accumule.
        BigDecimal perAttempt = before.available()
                .divide(BigDecimal.valueOf(attempts - 2), 2, java.math.RoundingMode.DOWN)
                .max(new BigDecimal("1.00"));

        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger insufficientFailures = new AtomicInteger();

        try {
            var futures = IntStream.range(0, attempts)
                    .mapToObj(i -> pool.submit(() -> {
                        awaitUninterruptibly(start);
                        try {
                            treasuryService.reserve(Currency.CNY, perAttempt, orderIds[i], actor);
                            successes.incrementAndGet();
                        } catch (BusinessException ex) {
                            if (ex.errorCode() == ErrorCode.INSUFFICIENT_TREASURY) {
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

        // Par construction (attempts - 2 tentatives suffisent a epuiser
        // le disponible), au moins deux tentatives echouent et au moins
        // une reussit ; la somme reservee ne depasse jamais le disponible
        // initial — c'est l'invariant verifie ci-dessous, independamment
        // du nombre exact de succes (qui peut varier d'une unite pres de
        // 0,01 a cause des arrondis).
        assertThat(successes.get()).isGreaterThan(0);
        assertThat(insufficientFailures.get()).isGreaterThanOrEqualTo(1);
        assertThat(successes.get() + insufficientFailures.get()).isEqualTo(attempts);

        TreasuryAccountResponse after = treasuryService.snapshot(Currency.CNY);
        BigDecimal actuallyReserved = after.reservedBalance().subtract(before.reservedBalance());
        assertThat(actuallyReserved).isEqualByComparingTo(perAttempt.multiply(BigDecimal.valueOf(successes.get())));
        assertThat(actuallyReserved).isLessThanOrEqualTo(before.available());
    }

    // -----------------------------------------------------------------

    /**
     * Garantit un disponible CNY largement suffisant pour ce seul test,
     * independamment de ce que d'autres tests de cette classe ont pu
     * consommer avant lui (le conteneur Postgres, et donc le solde de
     * tresorerie, est partage par toute la suite — voir le commentaire
     * de classe).
     */
    private void seedCny(UUID actor) {
        treasuryService.deposit(Currency.CNY, new BigDecimal("1000000"), actor, "seed de test");
    }

    /**
     * Insere une chaine RateSource -> Quote -> Order minimale
     * directement via les repositories, uniquement pour disposer d'un
     * {@code orderId} qui satisfait la contrainte de cle etrangere de
     * {@code treasury_transactions} — sans passer par le pipeline HTTP
     * complet, hors de propos pour ce test cible sur la tresorerie.
     */
    private UUID createDummyOrder(UUID userId) {
        Instant now = Instant.now();
        // currency_pair est VARCHAR(10) : un suffixe court garantit une
        // paire "courante" distincte a chaque appel (uq_rate_source_current
        // est scope par paire), sans jamais entrer en collision avec la
        // vraie paire XOF/CNY utilisee par les autres tests.
        String fakePair = "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        RateSource rateSource = rateSourceRepository.save(new RateSource(
                RateProviderType.MANUAL, fakePair, new BigDecimal("85.000000"),
                now, "treasury test fixture", userId, now));

        PricingResult pricing = new PricingResult(
                new BigDecimal("85.000000"), BigDecimal.ZERO, new BigDecimal("85.000000"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("1000.00"), new BigDecimal("1000.00"), new BigDecimal("11.76"));
        Quote quote = quoteRepository.save(new Quote(userId, QuoteDirection.SEND_XOF, pricing,
                rateSource.getId(), now, now.plusSeconds(1800)));

        // reference est VARCHAR(24) : un suffixe court suffit, l'unicite
        // n'a besoin de tenir que sur la duree de ce test.
        String reference = "TRSY-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Order order = orderRepository.save(new Order(reference, userId, quote.getId(),
                new BigDecimal("1000.00"), new BigDecimal("11.76"), new BigDecimal("85.000000"),
                BigDecimal.ZERO, new BigDecimal("1000.00"), null, now, now.plusSeconds(43200)));
        return order.getId();
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
