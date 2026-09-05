package com.converter.order.domain;

import com.converter.common.domain.BaseEntity;
import com.converter.supplier.domain.Purpose;
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
 * Ordre client, cree exclusivement a partir d'un {@code Quote} deja
 * {@code ACCEPTED}.
 *
 * <p><b>Ne recalcule jamais le taux</b> : les colonnes financieres sont
 * une copie figee du {@code Quote} au moment de la creation, jamais
 * relues ni recalculees ensuite — le seul lien vivant vers le devis
 * est {@code quoteId}, uniquement pour la tracabilite (audit).
 *
 * <p>Les transitions de statut ne sont jamais decidees par cette
 * classe : {@link com.converter.order.service.OrderStateMachine} valide
 * la legalite d'une transition, {@link com.converter.order.service.OrderService}
 * l'orchestre sous verrou pessimiste, et {@link #applyStatus} se
 * contente d'appliquer le nouveau statut deja valide.
 */
@Entity
@Table(name = "orders")
public class Order extends BaseEntity {

    @Column(name = "reference", nullable = false, length = 24)
    private String reference;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "quote_id", nullable = false)
    private UUID quoteId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private OrderStatus status;

    @Column(name = "amount_xof", nullable = false, precision = 19, scale = 2)
    private BigDecimal amountXof;

    @Column(name = "amount_cny", nullable = false, precision = 19, scale = 2)
    private BigDecimal amountCny;

    @Column(name = "customer_rate", nullable = false, precision = 18, scale = 6)
    private BigDecimal customerRate;

    @Column(name = "fee_xof", nullable = false, precision = 19, scale = 2)
    private BigDecimal feeXof;

    @Column(name = "net_amount_xof", nullable = false, precision = 19, scale = 2)
    private BigDecimal netAmountXof;

    @Column(name = "note", length = 500)
    private String note;

    /**
     * Fournisseur enregistre eventuellement utilise pour construire le snapshot {@link
     * Beneficiary} de cet ordre — <b>purement tracable</b> ("quel fournisseur enregistre a ete
     * utilise pour cette transaction ?"), jamais relu pour reconstruire ou recalculer le
     * beneficiaire historique : ce dernier reste entierement porte par {@link Beneficiary},
     * copie figee au moment de la creation. Une modification ulterieure du fournisseur
     * (ou sa desactivation) n'a donc structurellement aucun effet sur cet ordre.
     */
    @Column(name = "supplier_id")
    private UUID supplierId;

    /** Motif du transfert, optionnel — voir {@link Purpose}. Nullable pour les ordres crees avant son introduction. */
    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", length = 24)
    private Purpose purpose;

    @Column(name = "purpose_details", length = 500)
    private String purposeDetails;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    /**
     * <b>Cache dénormalisé</b> (passe 2, §11), PAS la source de verite. La source de verite est
     * le ledger {@code treasury_transactions} (une ligne {@code RESERVATION} pour cet {@code id}
     * d'ordre non encore resolue par un {@code RELEASE}/{@code WITHDRAWAL}). Ce booleen est ecrit
     * dans la <b>meme transaction</b> que la ligne {@code RESERVATION} (creation) et que la ligne
     * {@code RELEASE} (annulation/rejet/expiration) : il ne peut jamais diverger au commit.
     * Conserve pour eviter une agregation sur le ledger a chaque lecture d'ordre.
     */
    @Column(name = "treasury_reserved", nullable = false)
    private boolean treasuryReserved;

    /**
     * Echeance de paiement, figee a la creation ({@code created_at + ORDER_PAYMENT_WINDOW_MINUTES}).
     * Stockee (et non derivee) : un changement ulterieur du parametre ne doit jamais reexpirer
     * retroactivement un ordre existant. Passe le cap, l'ordre est expire par
     * {@link com.converter.order.service.OrderExpirationService} et sa reservation CNY liberee.
     */
    @Column(name = "payment_deadline_at", nullable = false)
    private Instant paymentDeadlineAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Order() {
        // Requis par JPA.
    }

    public Order(String reference, UUID userId, UUID quoteId, BigDecimal amountXof, BigDecimal amountCny,
                BigDecimal customerRate, BigDecimal feeXof, BigDecimal netAmountXof, String note,
                Instant createdAt, Instant paymentDeadlineAt) {
        this(reference, userId, quoteId, amountXof, amountCny, customerRate, feeXof, netAmountXof, note,
                createdAt, paymentDeadlineAt, null, null, null);
    }

    /** Variante Phase 2 : {@code supplierId}/{@code purpose}/{@code purposeDetails}, tous optionnels. */
    public Order(String reference, UUID userId, UUID quoteId, BigDecimal amountXof, BigDecimal amountCny,
                BigDecimal customerRate, BigDecimal feeXof, BigDecimal netAmountXof, String note,
                Instant createdAt, Instant paymentDeadlineAt, UUID supplierId, Purpose purpose,
                String purposeDetails) {
        this.reference = reference;
        this.userId = userId;
        this.quoteId = quoteId;
        this.status = OrderStatus.AWAITING_PAYMENT;
        this.amountXof = amountXof;
        this.amountCny = amountCny;
        this.customerRate = customerRate;
        this.feeXof = feeXof;
        this.netAmountXof = netAmountXof;
        this.note = note;
        this.treasuryReserved = false;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
        this.paymentDeadlineAt = paymentDeadlineAt;
        this.supplierId = supplierId;
        this.purpose = purpose;
        this.purposeDetails = purposeDetails;
    }

    /** Vrai si l'echeance de paiement est atteinte a l'instant donne (ordre encore non paye). */
    public boolean isPaymentOverdue(Instant asOf) {
        return !asOf.isBefore(paymentDeadlineAt);
    }

    /**
     * Applique un statut deja valide par {@code OrderStateMachine}.
     * N'effectue aucune verification de legalite elle-meme.
     */
    public void applyStatus(OrderStatus newStatus, Instant now) {
        this.status = newStatus;
        this.updatedAt = now;
        if (newStatus == OrderStatus.COMPLETED) {
            this.completedAt = now;
        } else if (newStatus == OrderStatus.CANCELLED) {
            this.cancelledAt = now;
        }
    }

    public void markTreasuryReserved() {
        this.treasuryReserved = true;
    }

    public void markTreasuryReleased() {
        this.treasuryReserved = false;
    }

    public void setCancellationReason(String reason) {
        this.cancellationReason = reason;
    }

    public void setRejectionReason(String reason) {
        this.rejectionReason = reason;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getReference() {
        return reference;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getQuoteId() {
        return quoteId;
    }

    public BigDecimal getAmountXof() {
        return amountXof;
    }

    public BigDecimal getAmountCny() {
        return amountCny;
    }

    public BigDecimal getCustomerRate() {
        return customerRate;
    }

    public BigDecimal getFeeXof() {
        return feeXof;
    }

    public BigDecimal getNetAmountXof() {
        return netAmountXof;
    }

    public String getNote() {
        return note;
    }

    public UUID getSupplierId() {
        return supplierId;
    }

    public Purpose getPurpose() {
        return purpose;
    }

    public String getPurposeDetails() {
        return purposeDetails;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public boolean isTreasuryReserved() {
        return treasuryReserved;
    }

    public Instant getPaymentDeadlineAt() {
        return paymentDeadlineAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
