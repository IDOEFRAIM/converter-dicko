package com.converter.rate.publicrate.domain;

import com.converter.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Une entree de l'historique public du taux client — <b>uniquement</b> {@code customerRate},
 * jamais {@code breakEvenRate}, la marge, les frais, ni aucune donnee de
 * {@code daily_cost_rate_configurations}. Append-only ({@code @Immutable}) : chaque publication
 * de configuration de cout cree une nouvelle ligne, les precedentes ne sont jamais modifiees ni
 * recalculees (voir {@code PublicRateSnapshotService}).
 */
@Entity
@Table(name = "public_rate_snapshots")
@Immutable
public class PublicRateSnapshot extends BaseEntity {

    @Column(name = "currency_pair", nullable = false, length = 10)
    private String currencyPair;

    @Column(name = "customer_rate", nullable = false, precision = 18, scale = 6)
    private BigDecimal customerRate;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected PublicRateSnapshot() {
        // Requis par JPA.
    }

    public PublicRateSnapshot(String currencyPair, BigDecimal customerRate, Instant recordedAt) {
        this.currencyPair = currencyPair;
        this.customerRate = customerRate;
        this.recordedAt = recordedAt;
    }

    public String getCurrencyPair() {
        return currencyPair;
    }

    public BigDecimal getCustomerRate() {
        return customerRate;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
