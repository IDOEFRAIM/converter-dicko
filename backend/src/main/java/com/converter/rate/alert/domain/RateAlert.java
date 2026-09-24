package com.converter.rate.alert.domain;

import com.converter.common.domain.BaseEntity;
import com.converter.common.exception.BusinessException;
import com.converter.common.exception.ErrorCode;
import com.converter.preferredrate.domain.PreferredRateDirection;
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
 * Intention utilisateur persistante : "previens-moi quand le taux client public de
 * {@code currencyPair} satisfait {@code comparison targetRate}". Une {@code RateAlert}
 * n'est <b>jamais</b> une transaction financiere — contrairement a
 * {@code PreferredRateRequest} (qui immobilise un montant et declenche un echange reel), elle
 * n'a ni montant ni impact Wallet/Treasury : voir {@code RateAlertService}, qui ne depend que de
 * {@code PublicRateSnapshotService} et de {@code NotificationService}.
 *
 * <p>{@code direction} reutilise {@link PreferredRateDirection} plutot que
 * {@code QuoteDirection} : ce dernier decrit quel montant le client fournit pour un devis
 * (non pertinent ici, une alerte ne porte aucun montant), alors que {@code PreferredRateDirection}
 * decrit deja exactement le meme concept — "quel sens de conversion", extensible plus tard —
 * sans montant associe. Une seule valeur existe aujourd'hui ({@code XOF_TO_CNY}), coherente avec
 * {@code currencyPair = "XOF/CNY"} : les deux ne peuvent jamais se contredire.
 *
 * <p>Trois transitions possibles depuis {@code ACTIVE}, chacune finale : {@link #trigger},
 * {@link #expire}, {@link #cancel}. Verrou pessimiste attendu en amont par l'appelant (voir
 * {@code RateAlertRepository#findByIdForUpdate}) — meme patron que {@code PreferredRateRequest}.
 */
@Entity
@Table(name = "rate_alerts")
public class RateAlert extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "currency_pair", nullable = false, length = 10)
    private String currencyPair;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 16)
    private PreferredRateDirection direction;

    @Column(name = "target_rate", nullable = false, precision = 18, scale = 6)
    private BigDecimal targetRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "comparison", nullable = false, length = 24)
    private RateComparison comparison;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private RateAlertStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "triggered_at")
    private Instant triggeredAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "expired_at")
    private Instant expiredAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected RateAlert() {
        // Requis par JPA.
    }

    public RateAlert(UUID userId, String currencyPair, PreferredRateDirection direction, BigDecimal targetRate,
                     RateComparison comparison, Instant createdAt, Instant expiresAt) {
        this.userId = userId;
        this.currencyPair = currencyPair;
        this.direction = direction;
        this.targetRate = targetRate;
        this.comparison = comparison;
        this.status = RateAlertStatus.ACTIVE;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    // -----------------------------------------------------------------

    /** Vrai si {@code currentRate} satisfait la regle de declenchement — jamais un {@code if} ailleurs. */
    public boolean isSatisfiedBy(BigDecimal currentRate) {
        return comparison.isSatisfied(currentRate, targetRate);
    }

    /** Vrai si une expiration est definie et depassee, independamment du statut persiste (expiration paresseuse). */
    public boolean isPastDeadline(Instant now) {
        return expiresAt != null && !now.isBefore(expiresAt);
    }

    public void trigger(Instant now) {
        requireActive();
        this.status = RateAlertStatus.TRIGGERED;
        this.triggeredAt = now;
    }

    public void expire(Instant now) {
        requireActive();
        this.status = RateAlertStatus.EXPIRED;
        this.expiredAt = now;
    }

    public void cancel(Instant now) {
        requireActive();
        this.status = RateAlertStatus.CANCELLED;
        this.cancelledAt = now;
    }

    private void requireActive() {
        if (this.status != RateAlertStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.RATE_ALERT_INACTIVE,
                    "Cette alerte n'est plus active (statut actuel : " + this.status + ").");
        }
    }

    // -----------------------------------------------------------------

    public UUID getUserId() {
        return userId;
    }

    public String getCurrencyPair() {
        return currencyPair;
    }

    public PreferredRateDirection getDirection() {
        return direction;
    }

    public BigDecimal getTargetRate() {
        return targetRate;
    }

    public RateComparison getComparison() {
        return comparison;
    }

    public RateAlertStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getTriggeredAt() {
        return triggeredAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public Instant getExpiredAt() {
        return expiredAt;
    }

    public long getVersion() {
        return version;
    }
}
