package com.converter.rate.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.converter.common.domain.BaseEntity;

/**
 * Cotation brute publiee par une source de taux (append-only).
 *
 * <p>Cette table remplace, dans son usage, la table {@code exchange_rates}
 * prevue en Phase 1 : elle ne porte plus les frais de service (deplaces
 * vers {@code system_settings}) ni la marge (deplacee vers chaque
 * {@code Quote}) — uniquement la donnee de marche brute. Voir
 * docs/ARCHITECTURE.md, Partie I, section K.3.
 *
 * <p>{@code effectiveFrom}/{@code effectiveTo} suivent exactement le
 * pattern deja valide en Phase 1 pour {@code exchange_rates} : au plus
 * une ligne "courante" (`effectiveTo IS NULL`) par {@code (providerType,
 * currencyPair)}, garanti par l'index unique partiel
 * {@code uq_rate_source_current} (migration V7). Publier un nouveau
 * taux cloture l'ancien puis insere le nouveau, dans la meme
 * transaction — jamais de {@code UPDATE} du montant d'une cotation deja
 * publiee.
 *
 * <p>{@code @Immutable} : au-dela de la fermeture de periode geree
 * explicitement par {@link com.converter.rate.service.RateAdminService},
 * aucune autre colonne de cette entite n'est jamais modifiee par le
 * code applicatif.
 */
@Entity
@Table(name = "rate_sources")
@Immutable
public class RateSource extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false, length = 16)
    private RateProviderType providerType;

    @Column(name = "currency_pair", nullable = false, length = 10)
    private String currencyPair;

    @Column(name = "cfa_per_cny", nullable = false, precision = 18, scale = 6)
    private BigDecimal cfaPerCny;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "effective_to")
    private Instant effectiveTo;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RateSource() {
        // Requis par JPA.
    }

    public RateSource(RateProviderType providerType,
                      String currencyPair,
                      BigDecimal cfaPerCny,
                      Instant effectiveFrom,
                      String note,
                      UUID createdBy,
                      Instant createdAt) {
        this.providerType = providerType;
        this.currencyPair = currencyPair;
        this.cfaPerCny = cfaPerCny;
        this.effectiveFrom = effectiveFrom;
        this.note = note;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public MarketRate toMarketRate() {
        return new MarketRate(currencyPair, cfaPerCny, providerType, effectiveFrom, getId());
    }

    public RateProviderType getProviderType() {
        return providerType;
    }

    public String getCurrencyPair() {
        return currencyPair;
    }

    public BigDecimal getCfaPerCny() {
        return cfaPerCny;
    }

    public Instant getEffectiveFrom() {
        return effectiveFrom;
    }

    public Instant getEffectiveTo() {
        return effectiveTo;
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
