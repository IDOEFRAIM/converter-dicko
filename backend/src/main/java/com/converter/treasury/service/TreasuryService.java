package com.converter.treasury.service;

import com.converter.audit.domain.AuditAction;
import com.converter.audit.service.AuditService;
import com.converter.common.exception.BusinessException;
import com.converter.common.exception.ErrorCode;
import com.converter.treasury.domain.Currency;
import com.converter.treasury.domain.TreasuryAccount;
import com.converter.treasury.domain.TreasuryTransaction;
import com.converter.treasury.domain.TreasuryTransactionType;
import com.converter.treasury.dto.TreasuryAccountResponse;
import com.converter.treasury.repository.TreasuryAccountRepository;
import com.converter.treasury.repository.TreasuryTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Point d'entree unique de toute mutation de solde de tresorerie.
 *
 * <p>Chaque operation : (1) charge le compte sous verrou pessimiste
 * ({@code SELECT ... FOR UPDATE}), (2) verifie les invariants, (3) mute
 * le solde, (4) ecrit une ligne {@link TreasuryTransaction} append-only
 * dans la <b>meme transaction</b>. Aucune autre classe du systeme
 * n'ecrit directement sur {@code TreasuryAccount}.
 *
 * <p>Vocabulaire {@code Available / Reserved / Consumed / Released}
 * (docs/ARCHITECTURE.md, Partie I, section I) :
 * <pre>
 * Available = balance - reserved_balance   (jamais negatif : CHECK SQL)
 * Reserved  = reserve()   — a la creation d'un Order
 * Consumed  = consume()   — a l'execution d'un Settlement
 * Released  = release()   — annulation/expiration/rejet d'un Order
 * </pre>
 */
@Service
public class TreasuryService {

    private static final Logger log = LoggerFactory.getLogger(TreasuryService.class);

    private final TreasuryAccountRepository accountRepository;
    private final TreasuryTransactionRepository transactionRepository;
    private final AuditService auditService;
    private final Clock clock;

    public TreasuryService(TreasuryAccountRepository accountRepository,
                           TreasuryTransactionRepository transactionRepository,
                           AuditService auditService,
                           Clock clock) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    /**
     * Immobilise {@code amount} sur le compte {@code currency}, lie a un
     * ordre. Echoue avec {@code 409 INSUFFICIENT_TREASURY} si le solde
     * disponible est insuffisant — l'ordre appelant ne doit alors pas
     * etre cree (voir {@code OrderService}, meme transaction).
     */
    @Transactional
    public void reserve(Currency currency, BigDecimal amount, UUID orderId, UUID performedBy) {
        TreasuryAccount account = loadForUpdate(currency);
        if (account.available().compareTo(amount) < 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_TREASURY,
                    "Liquidite " + currency + " insuffisante : disponible " + account.available()
                            + ", demande " + amount + ".");
        }
        account.reserve(amount, clock.instant());
        accountRepository.save(account);
        record(account, TreasuryTransactionType.RESERVATION, amount, orderId, performedBy, null);
        auditService.record(performedBy, null, AuditAction.TREASURY_RESERVED,
                "TreasuryAccount", account.getId().toString(),
                "{\"currency\":\"" + currency + "\",\"amount\":\"" + amount + "\",\"orderId\":\"" + orderId + "\"}");
    }

    /** Libere une reservation existante (annulation, expiration, rejet). */
    @Transactional
    public void release(Currency currency, BigDecimal amount, UUID orderId, UUID performedBy, String reason) {
        TreasuryAccount account = loadForUpdate(currency);
        account.release(amount, clock.instant());
        accountRepository.save(account);
        record(account, TreasuryTransactionType.RELEASE, amount, orderId, performedBy, reason);
        auditService.record(performedBy, null, AuditAction.TREASURY_RELEASED,
                "TreasuryAccount", account.getId().toString(),
                "{\"currency\":\"" + currency + "\",\"amount\":\"" + amount + "\",\"orderId\":\"" + orderId + "\"}");
    }

    /**
     * Rembourse le client : decaissement direct du solde XOF, hors reservation (le montant
     * rembourse n'a jamais ete reserve — seule la liquidite CNY l'est, a la creation de l'Order).
     * Distinct de {@link #consume} par construction : {@code REFUND} ne touche jamais
     * {@code reservedBalance} ni la reservation CNY de l'ordre, et n'a donc aucun effet sur un
     * {@code Settlement} deja execute ou a venir (voir {@code RefundService}).
     */
    @Transactional
    public void refund(Currency currency, BigDecimal amount, UUID orderId, UUID performedBy, String reason) {
        TreasuryAccount account = loadForUpdate(currency);
        account.withdrawUnreserved(amount, clock.instant());
        accountRepository.save(account);
        record(account, TreasuryTransactionType.REFUND, amount, orderId, performedBy, reason);
        auditService.record(performedBy, null, AuditAction.TREASURY_REFUNDED,
                "TreasuryAccount", account.getId().toString(),
                "{\"currency\":\"" + currency + "\",\"amount\":\"" + amount + "\",\"orderId\":\"" + orderId + "\"}");
    }

    /** Consomme une reservation : decaissement reel a l'execution d'un Settlement. */
    @Transactional
    public void consume(Currency currency, BigDecimal amount, UUID orderId, UUID performedBy) {
        TreasuryAccount account = loadForUpdate(currency);
        account.consume(amount, clock.instant());
        accountRepository.save(account);
        record(account, TreasuryTransactionType.WITHDRAWAL, amount, orderId, performedBy, null);
        auditService.record(performedBy, null, AuditAction.TREASURY_CONSUMED,
                "TreasuryAccount", account.getId().toString(),
                "{\"currency\":\"" + currency + "\",\"amount\":\"" + amount + "\",\"orderId\":\"" + orderId + "\"}");
    }

    /** Alimentation manuelle, hors reservation (encaissement XOF, apport de liquidite CNY). */
    @Transactional
    public TreasuryAccountResponse deposit(Currency currency, BigDecimal amount, UUID performedBy, String reason) {
        TreasuryAccount account = loadForUpdate(currency);
        account.deposit(amount, clock.instant());
        accountRepository.save(account);
        record(account, TreasuryTransactionType.DEPOSIT, amount, null, performedBy, reason);
        auditService.record(performedBy, null, AuditAction.TREASURY_DEPOSIT,
                "TreasuryAccount", account.getId().toString(),
                "{\"currency\":\"" + currency + "\",\"amount\":\"" + amount + "\"}");
        return toResponse(account);
    }

    /** Correction comptable administrative, motif obligatoire. {@code delta} peut etre negatif. */
    @Transactional
    public TreasuryAccountResponse adjust(Currency currency, BigDecimal delta, UUID performedBy, String reason) {
        TreasuryAccount account = loadForUpdate(currency);
        BigDecimal newBalance = account.getBalance().add(delta);
        if (newBalance.signum() < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Cet ajustement rendrait le solde " + currency + " negatif.");
        }
        // Un ajustement a la baisse ne doit jamais faire passer le solde total sous la part
        // deja reservee (invariant reserved_balance <= balance) : garde applicative explicite,
        // en amont de la contrainte SQL ck_treasury_accounts_reserved_le_balance qui, sinon,
        // remonterait un 409 DUPLICATE_RESOURCE peu explicite (passe 2, P2-5).
        if (newBalance.compareTo(account.getReservedBalance()) < 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_TREASURY,
                    "Cet ajustement descendrait le solde " + currency + " (" + newBalance
                            + ") sous la part deja reservee (" + account.getReservedBalance() + ").");
        }
        account.adjust(delta, clock.instant());
        accountRepository.save(account);
        record(account, TreasuryTransactionType.ADJUSTMENT, delta.abs(), null, performedBy, reason);
        auditService.record(performedBy, null, AuditAction.TREASURY_ADJUSTMENT,
                "TreasuryAccount", account.getId().toString(),
                "{\"currency\":\"" + currency + "\",\"delta\":\"" + delta + "\",\"reason\":\"" + reason + "\"}");
        return toResponse(account);
    }

    @Transactional(readOnly = true)
    public TreasuryAccountResponse snapshot(Currency currency) {
        TreasuryAccount account = accountRepository.findByCurrency(currency)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Compte de tresorerie introuvable : " + currency));
        return toResponse(account);
    }

    // -----------------------------------------------------------------

    private TreasuryAccount loadForUpdate(Currency currency) {
        return accountRepository.findByCurrencyForUpdate(currency)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Compte de tresorerie introuvable : " + currency));
    }

    private void record(TreasuryAccount account, TreasuryTransactionType type, BigDecimal amount,
                        UUID orderId, UUID performedBy, String reason) {
        TreasuryTransaction transaction = new TreasuryTransaction(
                account.getId(), type, amount, account.getBalance(), account.getReservedBalance(),
                orderId, performedBy, reason, clock.instant());
        transactionRepository.save(transaction);
        log.info("Tresorerie {} {} {} (order={}, par={})", account.getCurrency(), type, amount, orderId, performedBy);
    }

    private static TreasuryAccountResponse toResponse(TreasuryAccount account) {
        return new TreasuryAccountResponse(
                account.getId(),
                account.getCurrency(),
                account.getBalance(),
                account.getReservedBalance(),
                account.available(),
                account.getLowThreshold(),
                account.getUpdatedAt());
    }
}
