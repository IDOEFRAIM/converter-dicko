package com.converter.order.domain;

import com.converter.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/**
 * Snapshot immuable du beneficiaire chinois, en relation 1-1 avec un
 * {@code Order}. Jamais modifie apres creation ({@code @Immutable}) :
 * un ordre annule/rejete ne se corrige pas, un nouvel ordre se cree.
 */
@Entity
@Table(name = "beneficiaries")
@Immutable
public class Beneficiary extends BaseEntity {

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 24)
    private BeneficiaryType type;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(name = "identifier", nullable = false, length = 120)
    private String identifier;

    @Column(name = "bank_name", length = 120)
    private String bankName;

    @Column(name = "bank_branch", length = 120)
    private String bankBranch;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Beneficiary() {
        // Requis par JPA.
    }

    public Beneficiary(UUID orderId, BeneficiaryType type, String fullName, String identifier,
                       String bankName, String bankBranch, Instant createdAt) {
        if (type == BeneficiaryType.CHINESE_BANK_ACCOUNT && (bankName == null || bankName.isBlank())) {
            throw new IllegalArgumentException("bankName est obligatoire pour un CHINESE_BANK_ACCOUNT.");
        }
        this.orderId = orderId;
        this.type = type;
        this.fullName = fullName;
        this.identifier = identifier;
        this.bankName = bankName;
        this.bankBranch = bankBranch;
        this.createdAt = createdAt;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public BeneficiaryType getType() {
        return type;
    }

    public String getFullName() {
        return fullName;
    }

    public String getIdentifier() {
        return identifier;
    }

    public String getBankName() {
        return bankName;
    }

    public String getBankBranch() {
        return bankBranch;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
