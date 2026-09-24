package com.converter.preferredrate.domain;

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
 * Demande de taux preferentiel : "echanger {@code amountXof} XOF
 * uniquement lorsque le taux atteint {@code targetRate} XOF/CNY".
 *
 * <p>Le montant est immobilise (RESERVE) sur le {@code Wallet} du
 * client des la creation -- voir {@code PreferredRateService#create} --
 * jamais debite tant que le taux cible n'est pas atteint. Trois issues
 * possibles, chacune finale : {@link #execute}, {@link #expire},
 * {@link #cancel}.
 *
 * <p>{@code targetRate} et {@code achievedRate} suivent la meme
 * convention que {@code Quote.customerRate} : 1 CNY = X XOF. Pour
 * SEND_XOF, le taux cible est "atteint" quand le taux client COURANT
 * devient superieur ou egal au taux cible ({@code currentRate >=
 * targetRate}) -- jamais avant. Voir {@code PreferredRateService#processOne}.
 */
@Entity
@Table(name = "preferred_rate_requests")
public class PreferredRateRequest extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 16)
    private PreferredRateDirection direction;

    @Column(name = "amount_xof", nullable = false, precision = 19, scale = 2)
    private BigDecimal amountXof;

    @Column(name = "target_rate", nullable = false, precision = 18, scale = 6)
    private BigDecimal targetRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private PreferredRateStatus status;

    @Column(name = "achieved_rate", precision = 18, scale = 6)
    private BigDecimal achievedRate;

    @Column(name = "exchange_id")
    private UUID exchangeId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "executed_at")
    private Instant executedAt;

    @Column(name = "expired_at")
    private Instant expiredAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected PreferredRateRequest() {
        // Requis par JPA.
    }

    public PreferredRateRequest(UUID userId,
                                PreferredRateDirection direction,
                                BigDecimal amountXof,
                                BigDecimal targetRate,
                                Instant createdAt,
                                Instant expiresAt) {
        this.userId = userId;
        this.direction = direction;
        this.amountXof = amountXof;
        this.targetRate = targetRate;
        this.status = PreferredRateStatus.ACTIVE;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    // -----------------------------------------------------------------
    // Transitions -- chacune finale, une seule ne peut jamais s'appliquer
    // qu'une fois (garde requireActive), verrou pessimiste attendu en
    // amont par l'appelant (voir PreferredRateRepository#findByIdForUpdate).
    // -----------------------------------------------------------------

    public void execute(Instant now, BigDecimal achievedRate, UUID exchangeId) {
        requireActive();
        this.status = PreferredRateStatus.EXECUTED;
        this.achievedRate = achievedRate;
        this.exchangeId = exchangeId;
        this.executedAt = now;
    }

    public void expire(Instant now) {
        requireActive();
        this.status = PreferredRateStatus.EXPIRED;
        this.expiredAt = now;
    }

    public void cancel(Instant now) {
        requireActive();
        this.status = PreferredRateStatus.CANCELLED;
        this.cancelledAt = now;
    }

    /** Vrai si la fenetre de 3 jours est depassee, independamment du statut persiste (expiration paresseuse). */
    public boolean isPastDeadline(Instant now) {
        return !now.isBefore(expiresAt);
    }

    private void requireActive() {
        if (this.status != PreferredRateStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.INVALID_PREFERRED_RATE_STATE,
                    "Cette demande n'est plus active (statut actuel : " + this.status + ").");
        }
    }

    // -----------------------------------------------------------------

    public UUID getUserId() {
        return userId;
    }

    public PreferredRateDirection getDirection() {
        return direction;
    }

    public BigDecimal getAmountXof() {
        return amountXof;
    }

    public BigDecimal getTargetRate() {
        return targetRate;
    }

    public PreferredRateStatus getStatus() {
        return status;
    }

    public BigDecimal getAchievedRate() {
        return achievedRate;
    }

    public UUID getExchangeId() {
        return exchangeId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getExecutedAt() {
        return executedAt;
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
