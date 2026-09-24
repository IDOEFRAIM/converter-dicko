package com.converter.rate.cost.domain;

import com.converter.common.domain.BaseEntity;
import com.converter.rate.cost.BreakEvenResult;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Parametres de cout de la chaine XOF -&gt; USD -&gt; CNY pour une journee
 * donnee, et le {@code breakEvenRate} qui en decoule (append-only).
 *
 * <p>Distincte de {@link com.converter.rate.domain.RateSource} : cette
 * table ne porte jamais une cotation publiee aux clients, uniquement
 * la base de cout interne utilisee par l'administration pour calibrer
 * {@code marketRate}/{@code marginPercentage}. Les deux entites ne se
 * referencent pas l'une l'autre.
 *
 * <p>{@code referenceAmountXof} et {@code breakEvenRate} sont
 * conserves avec les parametres qui les ont produits : {@code
 * feeUsdCnyFixedUsd} etant un cout fixe, le cout de revient depend du
 * montant, il n'existe donc pas de "taux du jour" unique independant
 * du montant — chaque publication fige le montant de reference utilise
 * pour son propre calcul.
 *
 * <p>{@code @Immutable} : comme {@code RateSource}, aucune ligne n'est
 * jamais modifiee ni supprimee par le code applicatif. Une nouvelle
 * publication insere toujours une nouvelle ligne, meme pour corriger
 * une saisie erronee — l'historique reste une trace fidele de ce qui a
 * ete effectivement publie a chaque instant.
 */
@Entity
@Table(name = "daily_cost_rate_configurations")
@Immutable
public class DailyCostRateConfiguration extends BaseEntity {

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Column(name = "rate_xof_usd", nullable = false, precision = 18, scale = 6)
    private BigDecimal rateXofUsd;

    @Column(name = "rate_usd_cny", nullable = false, precision = 18, scale = 6)
    private BigDecimal rateUsdCny;

    @Column(name = "fee_xof_usd_percent", nullable = false, precision = 8, scale = 6)
    private BigDecimal feeXofUsdPercent;

    @Column(name = "fee_usd_cny_fixed_usd", nullable = false, precision = 12, scale = 2)
    private BigDecimal feeUsdCnyFixedUsd;

    @Column(name = "reference_amount_xof", nullable = false, precision = 19, scale = 2)
    private BigDecimal referenceAmountXof;

    @Column(name = "break_even_rate", nullable = false, precision = 18, scale = 6)
    private BigDecimal breakEvenRate;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DailyCostRateConfiguration() {
        // Requis par JPA.
    }

    public DailyCostRateConfiguration(LocalDate businessDate,
                                      BreakEvenResult result,
                                      BigDecimal rateXofUsd,
                                      BigDecimal rateUsdCny,
                                      BigDecimal feeXofUsdPercent,
                                      BigDecimal feeUsdCnyFixedUsd,
                                      String note,
                                      UUID createdBy,
                                      Instant createdAt) {
        this.businessDate = businessDate;
        this.rateXofUsd = rateXofUsd;
        this.rateUsdCny = rateUsdCny;
        this.feeXofUsdPercent = feeXofUsdPercent;
        this.feeUsdCnyFixedUsd = feeUsdCnyFixedUsd;
        this.referenceAmountXof = result.amountXof();
        this.breakEvenRate = result.breakEvenRate();
        this.note = note;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public LocalDate getBusinessDate() {
        return businessDate;
    }

    public BigDecimal getRateXofUsd() {
        return rateXofUsd;
    }

    public BigDecimal getRateUsdCny() {
        return rateUsdCny;
    }

    public BigDecimal getFeeXofUsdPercent() {
        return feeXofUsdPercent;
    }

    public BigDecimal getFeeUsdCnyFixedUsd() {
        return feeUsdCnyFixedUsd;
    }

    public BigDecimal getReferenceAmountXof() {
        return referenceAmountXof;
    }

    public BigDecimal getBreakEvenRate() {
        return breakEvenRate;
    }

    public String getNote() {
        return note;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
