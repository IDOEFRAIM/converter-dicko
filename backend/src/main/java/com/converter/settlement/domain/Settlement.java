package com.converter.settlement.domain;

import com.converter.common.domain.BaseEntity;
import com.converter.common.exception.BusinessException;
import com.converter.common.exception.ErrorCode;
import com.converter.order.domain.BeneficiaryType;
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
 * Execution du reglement CNY au beneficiaire — MVP entierement manuel.
 *
 * <p>Distinct d'un {@code Payment} : {@code Payment} = le client a paye
 * en XOF ; {@code Settlement} = l'entreprise a execute/organise le
 * decaissement en Chine. Le beneficiaire est duplique ici au moment de
 * la creation (snapshot), pour qu'un enregistrement de reglement reste
 * lisible seul, sans jointure vers {@code order}/{@code beneficiaries}.
 */
@Entity
@Table(name = "settlements")
public class Settlement extends BaseEntity {

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private SettlementStatus status;

    @Column(name = "amount_cny", nullable = false, precision = 19, scale = 2)
    private BigDecimal amountCny;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 24)
    private BeneficiaryType method;

    @Column(name = "beneficiary_full_name", nullable = false, length = 120)
    private String beneficiaryFullName;

    @Column(name = "beneficiary_identifier", nullable = false, length = 120)
    private String beneficiaryIdentifier;

    @Column(name = "beneficiary_bank_name", length = 120)
    private String beneficiaryBankName;

    @Column(name = "beneficiary_bank_branch", length = 120)
    private String beneficiaryBankBranch;

    @Column(name = "settlement_reference", length = 100)
    private String settlementReference;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "executed_by")
    private UUID executedBy;

    @Column(name = "executed_at")
    private Instant executedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Settlement() {
        // Requis par JPA.
    }

    public Settlement(UUID orderId, BigDecimal amountCny, BeneficiaryType method, String beneficiaryFullName,
                      String beneficiaryIdentifier, String beneficiaryBankName, String beneficiaryBankBranch,
                      Instant createdAt) {
        this.orderId = orderId;
        this.status = SettlementStatus.PENDING;
        this.amountCny = amountCny;
        this.method = method;
        this.beneficiaryFullName = beneficiaryFullName;
        this.beneficiaryIdentifier = beneficiaryIdentifier;
        this.beneficiaryBankName = beneficiaryBankName;
        this.beneficiaryBankBranch = beneficiaryBankBranch;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public void execute(String reference, String notes, UUID actorId, Instant now) {
        if (this.status != SettlementStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_SETTLEMENT_STATE,
                    "Ce reglement a deja ete execute (statut actuel : " + this.status + ").");
        }
        this.status = SettlementStatus.EXECUTED;
        this.settlementReference = reference;
        this.notes = notes;
        this.executedBy = actorId;
        this.executedAt = now;
        this.updatedAt = now;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public SettlementStatus getStatus() {
        return status;
    }

    public BigDecimal getAmountCny() {
        return amountCny;
    }

    public BeneficiaryType getMethod() {
        return method;
    }

    public String getBeneficiaryFullName() {
        return beneficiaryFullName;
    }

    public String getBeneficiaryIdentifier() {
        return beneficiaryIdentifier;
    }

    public String getBeneficiaryBankName() {
        return beneficiaryBankName;
    }

    public String getBeneficiaryBankBranch() {
        return beneficiaryBankBranch;
    }

    public String getSettlementReference() {
        return settlementReference;
    }

    public String getNotes() {
        return notes;
    }

    public UUID getExecutedBy() {
        return executedBy;
    }

    public Instant getExecutedAt() {
        return executedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
