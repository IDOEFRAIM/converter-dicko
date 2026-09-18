package com.converter.payment.domain;

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
 * Confirmation par le client de son paiement XOF hors plateforme
 * (MVP manuel — aucune integration Mobile Money/banque/Wave reelle).
 *
 * <p>Represente <b>uniquement</b> la reception/confirmation des XOF :
 * la suite (decaissement CNY) est le role de {@code Settlement}, une
 * entite distincte.
 */
@Entity
@Table(name = "payments")
public class Payment extends BaseEntity {

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 24)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private PaymentStatus status;

    @Column(name = "expected_amount_xof", nullable = false, precision = 19, scale = 2)
    private BigDecimal expectedAmountXof;

    @Column(name = "received_amount_xof", nullable = false, precision = 19, scale = 2)
    private BigDecimal receivedAmountXof;

    @Column(name = "payer_phone", length = 20)
    private String payerPhone;

    /**
     * Nullable en base (V35, ajoutee apres coup — voir la migration) : seule
     * la validation applicative ({@code SubmitPaymentRequest}) impose sa
     * presence pour toute NOUVELLE declaration, jamais une reecriture des
     * paiements deja soumis.
     */
    @Column(name = "payer_name", length = 160)
    private String payerName;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Payment() {
        // Requis par JPA.
    }

    public Payment(UUID orderId, PaymentMethod method, BigDecimal expectedAmountXof,
                   BigDecimal receivedAmountXof, String payerPhone,
                   String payerName, Instant submittedAt) {
        this.orderId = orderId;
        this.method = method;
        this.status = PaymentStatus.SUBMITTED;
        this.expectedAmountXof = expectedAmountXof;
        this.receivedAmountXof = receivedAmountXof;
        this.payerPhone = payerPhone;
        this.payerName = payerName;
        this.submittedAt = submittedAt;
        this.createdAt = submittedAt;
        this.updatedAt = submittedAt;
    }

    public void confirm(UUID reviewerId, Instant now) {
        requireSubmitted();
        this.status = PaymentStatus.CONFIRMED;
        this.confirmedAt = now;
        this.reviewedBy = reviewerId;
        this.updatedAt = now;
    }

    public void reject(UUID reviewerId, String reason, Instant now) {
        requireSubmitted();
        this.status = PaymentStatus.REJECTED;
        this.rejectedAt = now;
        this.reviewedBy = reviewerId;
        this.rejectionReason = reason;
        this.updatedAt = now;
    }

    /**
     * Un paiement rejete peut etre resoumis par le client -- meme {@code Order} (jamais un
     * nouvel ordre a recreer), meme {@code Payment} mis a jour a nouveau vers SUBMITTED. Les
     * preuves deja televersees pour la tentative rejetee restent attachees (jamais supprimees) :
     * l'admin voit l'historique complet a la revue suivante.
     */
    public void resubmit(PaymentMethod method, BigDecimal receivedAmountXof,
                         String payerPhone, String payerName, Instant now) {
        requireRejected();
        this.method = method;
        this.receivedAmountXof = receivedAmountXof;
        this.payerPhone = payerPhone;
        this.payerName = payerName;
        this.status = PaymentStatus.SUBMITTED;
        this.submittedAt = now;
        this.confirmedAt = null;
        this.rejectedAt = null;
        this.reviewedBy = null;
        this.rejectionReason = null;
        this.updatedAt = now;
    }

    private void requireSubmitted() {
        if (this.status != PaymentStatus.SUBMITTED) {
            throw new BusinessException(ErrorCode.INVALID_PAYMENT_STATE,
                    "Ce paiement a deja ete traite (statut actuel : " + this.status + ").");
        }
    }

    private void requireRejected() {
        if (this.status != PaymentStatus.REJECTED) {
            throw new BusinessException(ErrorCode.INVALID_PAYMENT_STATE,
                    "Seul un paiement rejete peut etre resoumis (statut actuel : " + this.status + ").");
        }
    }

    public UUID getOrderId() {
        return orderId;
    }

    public PaymentMethod getMethod() {
        return method;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public BigDecimal getExpectedAmountXof() {
        return expectedAmountXof;
    }

    public BigDecimal getReceivedAmountXof() {
        return receivedAmountXof;
    }

    public String getPayerPhone() {
        return payerPhone;
    }

    public String getPayerName() {
        return payerName;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public Instant getRejectedAt() {
        return rejectedAt;
    }

    public UUID getReviewedBy() {
        return reviewedBy;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
