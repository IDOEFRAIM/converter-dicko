package com.converter.quote.domain;

import com.converter.common.domain.BaseEntity;
import com.converter.common.exception.BusinessException;
import com.converter.common.exception.ErrorCode;
import com.converter.rate.engine.PricingResult;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Devis client — snapshot financier immuable.
 *
 * <p><b>Invariant central de cette phase</b> : une fois cree, aucune
 * colonne financiere de cette entite ({@code breakEvenRate},
 * {@code marginPercentage}, {@code customerRate}, les champs de frais,
 * {@code amountXof}, {@code amountCny}, {@code netAmountXof}) n'est
 * <i>jamais</i> modifiee — il n'existe d'ailleurs aucun mutateur pour
 * ces champs. Seules trois methodes de transition existent
 * ({@link #accept}, {@link #cancel}, {@link #expire}), et elles ne
 * touchent que {@code status} et l'horodatage de la transition. Une
 * republication ulterieure de la configuration de cout ou de la marge
 * n'a donc structurellement aucun moyen d'atteindre un {@code Quote}
 * deja cree.
 *
 * <p><b>Phase 3.1</b> : le taux avant marge n'est plus une cotation de
 * marche saisie manuellement ({@code RateSource}), mais le cout de
 * revient calcule par {@code CostRateCalculator} a partir de la
 * derniere {@code DailyCostRateConfiguration} — voir
 * docs/ARCHITECTURE.md, Partie I, section G.7. {@code costConfigurationId}
 * remplace {@code rateSourceId} : il pointe desormais vers
 * {@code daily_cost_rate_configurations}, jamais vers {@code rate_sources}.
 * {@code RateSource}/{@code RateProvider} restent utilises ailleurs dans
 * le backend (notamment {@code preferredrate}), mais plus par ce flux.
 */
@Entity
@Table(name = "quotes")
public class Quote extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 16)
    private QuoteDirection direction;

    @Column(name = "amount_xof", nullable = false, precision = 19, scale = 2)
    private BigDecimal amountXof;

    @Column(name = "amount_cny", nullable = false, precision = 19, scale = 2)
    private BigDecimal amountCny;

    @Column(name = "break_even_rate", nullable = false, precision = 18, scale = 6)
    private BigDecimal breakEvenRate;

    @Column(name = "margin_percentage", nullable = false, precision = 6, scale = 4)
    private BigDecimal marginPercentage;

    @Column(name = "customer_rate", nullable = false, precision = 18, scale = 6)
    private BigDecimal customerRate;

    @Column(name = "fee_percentage", nullable = false, precision = 6, scale = 4)
    private BigDecimal feePercentage;

    @Column(name = "fixed_fee_xof", nullable = false, precision = 19, scale = 2)
    private BigDecimal fixedFeeXof;

    @Column(name = "fee_xof", nullable = false, precision = 19, scale = 2)
    private BigDecimal feeXof;

    @Column(name = "net_amount_xof", nullable = false, precision = 19, scale = 2)
    private BigDecimal netAmountXof;

    @Column(name = "cost_configuration_id", nullable = false)
    private UUID costConfigurationId;

    /**
     * Vrai si une reduction de marge issue d'une Ruee collective reussie (mission
     * "differenciation marketing", Lot 3) a ete appliquee a CE devis -- fige a la creation, jamais
     * recalcule (voir {@code QuoteService#create}, qui consomme la recompense dans la meme
     * transaction que ce devis).
     */
    @Column(name = "pool_reward_applied", nullable = false)
    private boolean poolRewardApplied;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private QuoteStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Quote() {
        // Requis par JPA.
    }

    public Quote(UUID userId,
                QuoteDirection direction,
                PricingResult pricing,
                UUID costConfigurationId,
                Instant createdAt,
                Instant expiresAt) {
        this(userId, direction, pricing, costConfigurationId, createdAt, expiresAt, false);
    }

    /** Variante Lot 3 : {@code poolRewardApplied}, voir sa Javadoc de champ. */
    public Quote(UUID userId,
                QuoteDirection direction,
                PricingResult pricing,
                UUID costConfigurationId,
                Instant createdAt,
                Instant expiresAt,
                boolean poolRewardApplied) {
        this.userId = userId;
        this.direction = direction;
        this.amountXof = pricing.amountXof();
        this.amountCny = pricing.amountCny();
        this.breakEvenRate = pricing.baseRate();
        this.marginPercentage = pricing.marginPercentage();
        this.customerRate = pricing.customerRate();
        this.feePercentage = pricing.feePercentage();
        this.fixedFeeXof = pricing.fixedFeeXof();
        this.feeXof = pricing.feeXof();
        this.netAmountXof = pricing.netAmountXof();
        this.costConfigurationId = costConfigurationId;
        this.status = QuoteStatus.ACTIVE;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.poolRewardApplied = poolRewardApplied;
    }

    // -----------------------------------------------------------------
    // Transitions — seules methodes autorisees a modifier cette entite
    // apres sa creation
    // -----------------------------------------------------------------

    /**
     * Confirme le devis.
     *
     * <p>Si les 30 minutes sont deja ecoulees, la transition echoue et
     * le statut est corrige en {@code EXPIRED} au passage (expiration
     * paresseuse : {@code expiresAt}, pas {@code status}, reste la
     * source de verite tant que personne n'a interagi avec ce devis).
     */
    public void accept(Instant now) {
        requireActive();
        if (!now.isBefore(expiresAt)) {
            this.status = QuoteStatus.EXPIRED;
            throw new BusinessException(ErrorCode.QUOTE_EXPIRED,
                    "Ce devis a expire. Demandez-en un nouveau.");
        }
        this.status = QuoteStatus.ACCEPTED;
        this.acceptedAt = now;
    }

    public void cancel(Instant now) {
        requireActive();
        if (!now.isBefore(expiresAt)) {
            this.status = QuoteStatus.EXPIRED;
            throw new BusinessException(ErrorCode.QUOTE_EXPIRED,
                    "Ce devis a deja expire.");
        }
        this.status = QuoteStatus.CANCELLED;
        this.cancelledAt = now;
    }

    /** Statut a afficher a l'instant donne, sans muter l'entite (usage lecture seule). */
    public QuoteStatus effectiveStatus(Instant now) {
        if (status == QuoteStatus.ACTIVE && !now.isBefore(expiresAt)) {
            return QuoteStatus.EXPIRED;
        }
        return status;
    }

    private void requireActive() {
        if (this.status != QuoteStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.INVALID_QUOTE_STATE,
                    "Ce devis n'est plus modifiable (statut actuel : " + this.status + ").");
        }
    }

    // -----------------------------------------------------------------
    // Accesseurs
    // -----------------------------------------------------------------

    public UUID getUserId() {
        return userId;
    }

    public QuoteDirection getDirection() {
        return direction;
    }

    public BigDecimal getAmountXof() {
        return amountXof;
    }

    public BigDecimal getAmountCny() {
        return amountCny;
    }

    public BigDecimal getBreakEvenRate() {
        return breakEvenRate;
    }

    public BigDecimal getMarginPercentage() {
        return marginPercentage;
    }

    public BigDecimal getCustomerRate() {
        return customerRate;
    }

    public BigDecimal getFeePercentage() {
        return feePercentage;
    }

    public BigDecimal getFixedFeeXof() {
        return fixedFeeXof;
    }

    public BigDecimal getFeeXof() {
        return feeXof;
    }

    public BigDecimal getNetAmountXof() {
        return netAmountXof;
    }

    public UUID getCostConfigurationId() {
        return costConfigurationId;
    }

    public boolean isPoolRewardApplied() {
        return poolRewardApplied;
    }

    public QuoteStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }
}
