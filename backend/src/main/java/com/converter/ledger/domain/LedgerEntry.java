package com.converter.ledger.domain;

import com.converter.common.domain.BaseEntity;
import com.converter.treasury.domain.Currency;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Ligne du grand livre interne : un mouvement de revenu ou de cout reel, distinct du ledger de
 * tresorerie ({@code treasury_transactions}, qui suit les reservations/liberations XOF/CNY) et
 * du ledger d'idempotence. Sert exclusivement au rapprochement financier (voir
 * {@code docs/TRANSFI_INTEGRATION.md}, section "Reconciliation").
 *
 * <p>{@code amount} est toujours positif : le signe economique (credit/cout) est porte par
 * {@link LedgerEntryType}, jamais par le signe de la valeur — une ligne {@code PROVIDER_FEE}
 * de 1500 XOF signifie "1500 XOF de frais prestataire", jamais "-1500".
 */
@Entity
@Table(name = "ledger_entries")
public class LedgerEntry extends BaseEntity {

    /** Nullable : un {@code ADJUSTMENT} administrateur peut ne concerner aucun ordre precis. */
    @Column(name = "order_id")
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 32)
    private LedgerEntryType entryType;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 3)
    private Currency currency;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LedgerEntry() {
        // Requis par JPA.
    }

    public LedgerEntry(UUID orderId, LedgerEntryType entryType, Currency currency, BigDecimal amount,
                       String description, Instant createdAt) {
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("LedgerEntry.amount doit toujours etre positif ou nul "
                    + "(le signe economique vient de LedgerEntryType, jamais de la valeur) : " + amount);
        }
        this.orderId = orderId;
        this.entryType = entryType;
        this.currency = currency;
        this.amount = amount;
        this.description = description;
        this.createdAt = createdAt;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public LedgerEntryType getEntryType() {
        return entryType;
    }

    public Currency getCurrency() {
        return currency;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
