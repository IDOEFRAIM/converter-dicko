package com.converter.treasury.domain;

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
 * Ecriture append-only du ledger de tresorerie.
 *
 * <p>Jamais modifiee ni supprimee ({@code @Immutable}) : une erreur se
 * corrige par une ecriture {@code ADJUSTMENT} compensatoire, jamais par
 * un {@code UPDATE}. {@code balanceAfter}/{@code reservedAfter}
 * capturent le solde resultant, ce qui permet une reconciliation sans
 * avoir a rejouer tout l'historique.
 */
@Entity
@Table(name = "treasury_transactions")
@Immutable
public class TreasuryTransaction extends BaseEntity {

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16)
    private TreasuryTransactionType type;

    @Column(name = "amount", nullable = false, precision = 21, scale = 2)
    private BigDecimal amount;

    @Column(name = "balance_after", nullable = false, precision = 21, scale = 2)
    private BigDecimal balanceAfter;

    @Column(name = "reserved_after", nullable = false, precision = 21, scale = 2)
    private BigDecimal reservedAfter;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "performed_by")
    private UUID performedBy;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TreasuryTransaction() {
        // Requis par JPA.
    }

    public TreasuryTransaction(UUID accountId,
                               TreasuryTransactionType type,
                               BigDecimal amount,
                               BigDecimal balanceAfter,
                               BigDecimal reservedAfter,
                               UUID orderId,
                               UUID performedBy,
                               String reason,
                               Instant createdAt) {
        this.accountId = accountId;
        this.type = type;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.reservedAfter = reservedAfter;
        this.orderId = orderId;
        this.performedBy = performedBy;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public TreasuryTransactionType getType() {
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

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getPerformedBy() {
        return performedBy;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
