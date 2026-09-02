package com.converter.preferredrate.domain;

import com.converter.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Operation declenchee lorsqu'une {@link PreferredRateRequest} atteint
 * son taux cible : conversion de la valeur du solde Wallet au taux
 * reellement atteint. Distinct d'un {@code Order}/{@code Settlement}
 * (qui exigent un beneficiaire en Chine, non demande par cette
 * fonctionnalite) -- ici, l'operation se limite au solde Wallet du
 * client.
 *
 * <p>Cycle de progression simule, MVP entierement interne (aucun
 * fournisseur externe) : demarrage (T0), progression a T+45min et
 * T+90min, terminaison au plus tard a T+2h. Les trois marqueurs
 * {@code progress45SentAt}/{@code progress90SentAt}/{@code completedAt}
 * garantissent qu'aucune notification n'est envoyee deux fois, quelle
 * que soit la frequence du scheduler qui appelle {@link #isProgress45Due}
 * etc.
 */
@Entity
@Table(name = "exchanges")
public class Exchange extends BaseEntity {

    public static final Duration PROGRESS_INTERVAL = Duration.ofMinutes(45);
    public static final Duration MAX_DURATION = Duration.ofHours(2);

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "preferred_rate_request_id", nullable = false)
    private UUID preferredRateRequestId;

    @Column(name = "amount_xof", nullable = false, precision = 19, scale = 2)
    private BigDecimal amountXof;

    @Column(name = "achieved_rate", nullable = false, precision = 18, scale = 6)
    private BigDecimal achievedRate;

    @Column(name = "amount_cny", nullable = false, precision = 19, scale = 2)
    private BigDecimal amountCny;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ExchangeStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "progress_45_sent_at")
    private Instant progress45SentAt;

    @Column(name = "progress_90_sent_at")
    private Instant progress90SentAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Exchange() {
        // Requis par JPA.
    }

    public Exchange(UUID userId, UUID preferredRateRequestId, BigDecimal amountXof,
                    BigDecimal achievedRate, BigDecimal amountCny, Instant startedAt) {
        this.userId = userId;
        this.preferredRateRequestId = preferredRateRequestId;
        this.amountXof = amountXof;
        this.achievedRate = achievedRate;
        this.amountCny = amountCny;
        this.status = ExchangeStatus.STARTED;
        this.startedAt = startedAt;
    }

    public boolean isProgress45Due(Instant now) {
        return status == ExchangeStatus.STARTED && progress45SentAt == null
                && !now.isBefore(startedAt.plus(PROGRESS_INTERVAL));
    }

    public boolean isProgress90Due(Instant now) {
        return status == ExchangeStatus.STARTED && progress90SentAt == null
                && !now.isBefore(startedAt.plus(PROGRESS_INTERVAL.multipliedBy(2)));
    }

    public boolean isCompletionDue(Instant now) {
        return status == ExchangeStatus.STARTED && !now.isBefore(startedAt.plus(MAX_DURATION));
    }

    public void markProgress45Sent(Instant now) {
        this.progress45SentAt = now;
    }

    public void markProgress90Sent(Instant now) {
        this.progress90SentAt = now;
    }

    public void complete(Instant now) {
        this.status = ExchangeStatus.COMPLETED;
        this.completedAt = now;
    }

    public void cancel(Instant now) {
        this.status = ExchangeStatus.CANCELLED;
        this.cancelledAt = now;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getPreferredRateRequestId() {
        return preferredRateRequestId;
    }

    public BigDecimal getAmountXof() {
        return amountXof;
    }

    public BigDecimal getAchievedRate() {
        return achievedRate;
    }

    public BigDecimal getAmountCny() {
        return amountCny;
    }

    public ExchangeStatus getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getProgress45SentAt() {
        return progress45SentAt;
    }

    public Instant getProgress90SentAt() {
        return progress90SentAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public long getVersion() {
        return version;
    }
}
