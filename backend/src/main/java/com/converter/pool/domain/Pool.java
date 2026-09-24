package com.converter.pool.domain;

import com.converter.common.domain.BaseEntity;
import com.converter.common.exception.BusinessException;
import com.converter.common.exception.ErrorCode;
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
 * "Ruee collective" (mission "differenciation marketing", Lot 3) : un participant fixe un
 * objectif de volume XOF et une echeance, invite des amis via {@link #code}, et si le volume
 * cumule des ordres crees en reference a ce pool atteint l'objectif avant l'echeance, TOUS les
 * participants recoivent une reduction de marge sur leur PROCHAIN devis (jamais retroactive --
 * voir {@code QuoteService}, l'immutabilite du pricing deja fige reste absolue).
 *
 * <p>{@link #rewardMarginReductionPercentage} est un <b>snapshot</b> du reglage {@code
 * SettingKey#POOL_REWARD_MARGIN_REDUCTION_PERCENTAGE} au moment de la creation -- une modification
 * ulterieure du reglage global n'affecte jamais une Ruee deja en cours (meme discipline que {@code
 * PreferredRateRequest#targetRate} ou {@code Order} figeant son pricing).
 *
 * <p>Trois issues possibles, chacune finale : {@link #succeed}, {@link #expire}, {@link #cancel}.
 * Verrou pessimiste attendu en amont par l'appelant (voir {@code PoolRepository#findByIdForUpdate})
 * avant toute transition, meme patron que {@code RateAlert}/{@code PreferredRateRequest}.
 */
@Entity
@Table(name = "pools")
public class Pool extends BaseEntity {

    @Column(name = "code", nullable = false, length = 8)
    private String code;

    @Column(name = "creator_id", nullable = false)
    private UUID creatorId;

    @Column(name = "currency_pair", nullable = false, length = 10)
    private String currencyPair;

    @Column(name = "target_amount_xof", nullable = false, precision = 19, scale = 2)
    private BigDecimal targetAmountXof;

    @Column(name = "current_amount_xof", nullable = false, precision = 19, scale = 2)
    private BigDecimal currentAmountXof;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private PoolStatus status;

    @Column(name = "reward_margin_reduction_percentage", nullable = false, precision = 6, scale = 3)
    private BigDecimal rewardMarginReductionPercentage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "succeeded_at")
    private Instant succeededAt;

    @Column(name = "expired_at")
    private Instant expiredAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Pool() {
        // Requis par JPA.
    }

    public Pool(String code, UUID creatorId, String currencyPair, BigDecimal targetAmountXof,
               BigDecimal rewardMarginReductionPercentage, Instant createdAt, Instant expiresAt) {
        this.code = code;
        this.creatorId = creatorId;
        this.currencyPair = currencyPair;
        this.targetAmountXof = targetAmountXof;
        this.currentAmountXof = BigDecimal.ZERO.setScale(2);
        this.status = PoolStatus.ACTIVE;
        this.rewardMarginReductionPercentage = rewardMarginReductionPercentage;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    // -----------------------------------------------------------------
    // Transitions -- chacune finale, verrou pessimiste attendu en amont.
    // -----------------------------------------------------------------

    /** Fait progresser le volume cumule -- ne declenche jamais {@link #succeed} elle-meme, voir {@link #hasReachedTarget}. */
    public void contribute(BigDecimal amountXof, Instant now) {
        requireActive();
        this.currentAmountXof = this.currentAmountXof.add(amountXof);
    }

    public boolean hasReachedTarget() {
        return currentAmountXof.compareTo(targetAmountXof) >= 0;
    }

    public void succeed(Instant now) {
        requireActive();
        this.status = PoolStatus.SUCCEEDED;
        this.succeededAt = now;
    }

    public void expire(Instant now) {
        requireActive();
        this.status = PoolStatus.EXPIRED;
        this.expiredAt = now;
    }

    public void cancel(Instant now) {
        requireActive();
        this.status = PoolStatus.CANCELLED;
        this.cancelledAt = now;
    }

    public boolean isPastDeadline(Instant now) {
        return !now.isBefore(expiresAt);
    }

    private void requireActive() {
        if (this.status != PoolStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.POOL_INACTIVE,
                    "Cette Ruee n'est plus active (statut actuel : " + this.status + ").");
        }
    }

    // -----------------------------------------------------------------

    public String getCode() {
        return code;
    }

    public UUID getCreatorId() {
        return creatorId;
    }

    public String getCurrencyPair() {
        return currencyPair;
    }

    public BigDecimal getTargetAmountXof() {
        return targetAmountXof;
    }

    public BigDecimal getCurrentAmountXof() {
        return currentAmountXof;
    }

    public PoolStatus getStatus() {
        return status;
    }

    public BigDecimal getRewardMarginReductionPercentage() {
        return rewardMarginReductionPercentage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getSucceededAt() {
        return succeededAt;
    }

    public Instant getExpiredAt() {
        return expiredAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public long getVersion() {
        return version;
    }
}
