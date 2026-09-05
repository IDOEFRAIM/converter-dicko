package com.converter.refund.domain;

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
 * Remboursement XOF au client — represente le fait que l'entreprise a rendu au client l'argent
 * qu'il avait paye pour un {@code Order}, independamment de l'etat du {@code Settlement} associe.
 *
 * <p><b>Ce qu'un {@code Refund} n'est PAS</b> : il n'annule jamais retroactivement un
 * {@code Settlement} deja execute (le paiement en Chine, une fois fait, reste fait — aucune
 * integration ne permet de le "reverse") ; il ne modifie jamais {@code Order.status} (voir
 * {@code RefundService}, section "Order status"). Un remboursement est une operation
 * financierement independante, tracee pour elle-meme.
 *
 * <p>{@code amountXof} n'est jamais saisi librement par l'admin : il est toujours une copie
 * exacte de {@code Payment.receivedAmountXof} au moment de la creation (voir {@code RefundService}
 * — le modele actuel ne connait pas de paiement partiel ni de sur-paiement au-dela de la
 * tolerance, donc pas de remboursement partiel a modeliser).
 */
@Entity
@Table(name = "refunds")
public class Refund extends BaseEntity {

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(name = "amount_xof", nullable = false, precision = 19, scale = 2)
    private BigDecimal amountXof;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private RefundStatus status;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "transaction_reference", length = 100)
    private String transactionReference;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "processed_by")
    private UUID processedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Refund() {
        // Requis par JPA.
    }

    public Refund(UUID orderId, UUID paymentId, BigDecimal amountXof, String reason, UUID createdBy, Instant createdAt) {
        this.orderId = orderId;
        this.paymentId = paymentId;
        this.amountXof = amountXof;
        this.status = RefundStatus.PENDING;
        this.reason = reason;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public void process(String transactionReference, UUID actorId, Instant now) {
        requirePending();
        this.status = RefundStatus.PROCESSED;
        this.transactionReference = transactionReference;
        this.processedBy = actorId;
        this.processedAt = now;
        this.updatedAt = now;
    }

    public void reject(String rejectionReason, UUID actorId, Instant now) {
        requirePending();
        this.status = RefundStatus.REJECTED;
        this.rejectionReason = rejectionReason;
        this.processedBy = actorId;
        this.processedAt = now;
        this.updatedAt = now;
    }

    private void requirePending() {
        if (this.status != RefundStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_REFUND_STATE,
                    "Ce remboursement a deja ete traite (statut actuel : " + this.status + ").");
        }
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public BigDecimal getAmountXof() {
        return amountXof;
    }

    public RefundStatus getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public String getTransactionReference() {
        return transactionReference;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public UUID getProcessedBy() {
        return processedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
