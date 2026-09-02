package com.converter.wallet.service;

import com.converter.common.api.PageResponse;
import com.converter.common.exception.BusinessException;
import com.converter.common.exception.ErrorCode;
import com.converter.wallet.domain.Wallet;
import com.converter.wallet.domain.WalletTransaction;
import com.converter.wallet.domain.WalletTransactionType;
import com.converter.wallet.dto.WalletResponse;
import com.converter.wallet.dto.WalletTransactionResponse;
import com.converter.wallet.repository.WalletRepository;
import com.converter.wallet.repository.WalletTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Point d'entree unique de toute mutation de solde Wallet.
 *
 * <p>Chaque operation : (1) charge le wallet sous verrou pessimiste
 * ({@code SELECT ... FOR UPDATE}), (2) verifie les invariants, (3) mute
 * le solde, (4) ecrit une ligne {@link WalletTransaction} append-only
 * dans la <b>meme transaction</b>. Aucune autre classe du systeme
 * n'ecrit directement sur {@link Wallet}. Meme architecture que
 * {@code TreasuryService} (Phase 6), adaptee a un solde par utilisateur
 * plutot que par devise globale.
 *
 * <p>Le Wallet n'est PAS un compte bancaire : {@link #deposit} n'est
 * relie a aucun fournisseur de paiement reel (Mobile Money, banque...)
 * -- hors perimetre explicite de cette phase. C'est une brique de
 * service, exercee ici et testee, en attendant qu'un canal
 * d'alimentation reel soit branche dans une phase ulterieure.
 */
@Service
public class WalletService {

    private static final Logger log = LoggerFactory.getLogger(WalletService.class);

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;
    private final Clock clock;

    public WalletService(WalletRepository walletRepository,
                         WalletTransactionRepository transactionRepository,
                         Clock clock) {
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.clock = clock;
    }

    /** Alimentation du wallet. MVP : aucun fournisseur de paiement reel branche ici. */
    @Transactional
    public WalletResponse deposit(UUID userId, BigDecimal amount, String reason) {
        requirePositive(amount);
        Wallet wallet = loadOrCreateForUpdate(userId);
        wallet.deposit(amount, clock.instant());
        walletRepository.save(wallet);
        record(wallet, WalletTransactionType.CREDIT, amount, null, reason);
        log.info("Wallet {} credite de {} XOF", wallet.getId(), amount);
        return toResponse(wallet);
    }

    /**
     * Immobilise {@code amount} sur le wallet de {@code userId}. Echoue
     * avec {@code 409 INSUFFICIENT_WALLET_BALANCE} si le disponible est
     * insuffisant.
     */
    @Transactional
    public void reserve(UUID userId, BigDecimal amount, UUID referenceId, String reason) {
        requirePositive(amount);
        Wallet wallet = loadForUpdate(userId);
        if (wallet.available().compareTo(amount) < 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_WALLET_BALANCE,
                    "Solde Wallet disponible insuffisant : disponible " + wallet.available()
                            + ", demande " + amount + ".");
        }
        wallet.reserve(amount, clock.instant());
        walletRepository.save(wallet);
        record(wallet, WalletTransactionType.RESERVE, amount, referenceId, reason);
        log.info("Wallet {} : reservation de {} XOF ({})", wallet.getId(), amount, referenceId);
    }

    /** Libere une reservation existante (annulation, expiration). */
    @Transactional
    public void release(UUID userId, BigDecimal amount, UUID referenceId, String reason) {
        requirePositive(amount);
        Wallet wallet = loadForUpdate(userId);
        wallet.release(amount, clock.instant());
        walletRepository.save(wallet);
        record(wallet, WalletTransactionType.RELEASE, amount, referenceId, reason);
        log.info("Wallet {} : liberation de {} XOF ({})", wallet.getId(), amount, referenceId);
    }

    /** Consomme une reservation : decaissement reel (ex. declenchement d'un echange). */
    @Transactional
    public void debit(UUID userId, BigDecimal amount, UUID referenceId, String reason) {
        requirePositive(amount);
        Wallet wallet = loadForUpdate(userId);
        wallet.consume(amount, clock.instant());
        walletRepository.save(wallet);
        record(wallet, WalletTransactionType.DEBIT, amount, referenceId, reason);
        log.info("Wallet {} : debit de {} XOF ({})", wallet.getId(), amount, referenceId);
    }

    @Transactional
    public WalletResponse snapshot(UUID userId) {
        Wallet wallet = loadOrCreateForUpdate(userId);
        return toResponse(wallet);
    }

    @Transactional(readOnly = true)
    public PageResponse<WalletTransactionResponse> transactions(UUID userId, Pageable pageable) {
        Wallet wallet = walletRepository.findByUserId(userId).orElse(null);
        if (wallet == null) {
            return new PageResponse<>(java.util.List.of(), pageable.getPageNumber(), pageable.getPageSize(), 0, 0, true, true);
        }
        return PageResponse.from(
                transactionRepository.findByWalletIdOrderByCreatedAtDesc(wallet.getId(), pageable),
                WalletService::toResponse);
    }

    // -----------------------------------------------------------------

    private Wallet loadForUpdate(UUID userId) {
        return walletRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Wallet introuvable pour cet utilisateur."));
    }

    /**
     * Charge le wallet de {@code userId} sous verrou, en le creant s'il
     * n'existe pas encore (premier acces). La creation elle-meme reste
     * protegee par la contrainte {@code uq_wallets_user} : deux creations
     * concurrentes pour le meme utilisateur, l'une echoue et retente la
     * lecture, jamais deux wallets pour un meme utilisateur.
     */
    private Wallet loadOrCreateForUpdate(UUID userId) {
        return walletRepository.findByUserIdForUpdate(userId)
                .orElseGet(() -> {
                    try {
                        return walletRepository.save(new Wallet(userId, clock.instant()));
                    } catch (RuntimeException ex) {
                        return walletRepository.findByUserIdForUpdate(userId)
                                .orElseThrow(() -> ex);
                    }
                });
    }

    private void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Le montant doit etre strictement positif.");
        }
    }

    private void record(Wallet wallet, WalletTransactionType type, BigDecimal amount, UUID referenceId, String reason) {
        WalletTransaction transaction = new WalletTransaction(
                wallet.getId(), type, amount, wallet.getBalance(), wallet.getReservedBalance(),
                referenceId, reason, clock.instant());
        transactionRepository.save(transaction);
    }

    private WalletResponse toResponse(Wallet wallet) {
        return new WalletResponse(wallet.getId(), wallet.getBalance(), wallet.getReservedBalance(),
                wallet.available(), wallet.getUpdatedAt());
    }

    private static WalletTransactionResponse toResponse(WalletTransaction tx) {
        return new WalletTransactionResponse(tx.getId(), tx.getType(), tx.getAmount(), tx.getBalanceAfter(),
                tx.getReservedAfter(), tx.getReferenceId(), tx.getReason(), tx.getCreatedAt());
    }
}
