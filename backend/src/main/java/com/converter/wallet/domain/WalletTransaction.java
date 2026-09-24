package com.converter.wallet.domain;

import com.converter.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Ecriture append-only du ledger Wallet.
 *
 * <p>Jamais modifiee ni supprimee ({@code @Immutable}) : une erreur se
 * corrige par une ecriture compensatoire, jamais par un {@code UPDATE}.
 * {@code balanceAfter}/{@code reservedAfter} capturent le solde
 * resultant, ce qui permet une reconciliation sans avoir a rejouer tout
 * l'historique -- meme principe que {@code TreasuryTransaction}.
 */
@Entity
@Table(name = "wallet_transactions")
@Immutable
public class WalletTransaction extends BaseEntity {

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16)
    private WalletTransactionType type;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "balance_after", nullable = false, precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    @Column(name = "reserved_after", nullable = false, precision = 19, scale = 2)
    private BigDecimal reservedAfter;

    /** Reference libre vers l'operation a l'origine du mouvement (ex. PreferredRateRequest). */
    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected WalletTransaction() {
        // Requis par JPA.
    }

    public WalletTransaction(UUID walletId,
                             WalletTransactionType type,
                             BigDecimal amount,
                             BigDecimal balanceAfter,
                             BigDecimal reservedAfter,
                             UUID referenceId,
                             String reason,
                             Instant createdAt) {
        this.walletId = walletId;
        this.type = type;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.reservedAfter = reservedAfter;
        this.referenceId = referenceId;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public UUID getWalletId() {
        return walletId;
    }

    public WalletTransactionType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public BigDecimal getReservedAfter() {
        return reservedAfter;
    }

    public UUID getReferenceId() {
        return referenceId;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
