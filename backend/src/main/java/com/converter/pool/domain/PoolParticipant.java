package com.converter.pool.domain;

import com.converter.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Un participant d'un {@link Pool} (le createur y compris, {@link #isCreator}). {@link
 * #contributedAmountXof} est la somme des montants des ordres que CE participant a crees en
 * reference au pool -- distinct de {@code Pool#currentAmountXof} (le total tous participants).
 *
 * <p>{@link #rewardMarginReductionPercentage}/{@link #rewardGrantedAt}/{@link #rewardConsumedAt}
 * ne sont jamais renseignes tant que le pool n'a pas {@link Pool#succeed reussi} -- et le sont
 * alors pour TOUS les participants, pas seulement les contributeurs (mission : "chaque
 * participant recoit un badge special"). Consomme une seule fois, par {@code QuoteService}, sur
 * le PROCHAIN devis du participant.
 */
@Entity
@Table(name = "pool_participants")
public class PoolParticipant extends BaseEntity {

    @Column(name = "pool_id", nullable = false)
    private UUID poolId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "is_creator", nullable = false)
    private boolean isCreator;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    @Column(name = "contributed_amount_xof", nullable = false, precision = 19, scale = 2)
    private BigDecimal contributedAmountXof;

    @Column(name = "reward_granted_at")
    private Instant rewardGrantedAt;

    @Column(name = "reward_margin_reduction_percentage", precision = 6, scale = 3)
    private BigDecimal rewardMarginReductionPercentage;

    @Column(name = "reward_consumed_at")
    private Instant rewardConsumedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected PoolParticipant() {
        // Requis par JPA.
    }

    public PoolParticipant(UUID poolId, UUID userId, boolean isCreator, Instant joinedAt) {
        this.poolId = poolId;
        this.userId = userId;
        this.isCreator = isCreator;
        this.joinedAt = joinedAt;
        this.contributedAmountXof = BigDecimal.ZERO.setScale(2);
    }

    public void addContribution(BigDecimal amountXof) {
        this.contributedAmountXof = this.contributedAmountXof.add(amountXof);
    }

    /** Applique la recompense d'un pool venant de reussir -- voir la Javadoc de classe. */
    public void grantReward(BigDecimal marginReductionPercentage, Instant now) {
        this.rewardGrantedAt = now;
        this.rewardMarginReductionPercentage = marginReductionPercentage;
    }

    public boolean hasUnconsumedReward() {
        return rewardGrantedAt != null && rewardConsumedAt == null;
    }

    /** A appeler une seule fois, par {@code QuoteService}, quand ce devis applique la reduction. */
    public void consumeReward(Instant now) {
        this.rewardConsumedAt = now;
    }

    public UUID getPoolId() {
        return poolId;
    }

    public UUID getUserId() {
        return userId;
    }

    public boolean isCreator() {
        return isCreator;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public BigDecimal getContributedAmountXof() {
        return contributedAmountXof;
    }

    public Instant getRewardGrantedAt() {
        return rewardGrantedAt;
    }

    public BigDecimal getRewardMarginReductionPercentage() {
        return rewardMarginReductionPercentage;
    }

    public Instant getRewardConsumedAt() {
        return rewardConsumedAt;
    }

    public long getVersion() {
        return version;
    }
}
