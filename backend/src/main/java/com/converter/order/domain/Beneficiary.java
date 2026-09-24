package com.converter.order.domain;

import com.converter.common.domain.BaseEntity;
import com.converter.common.exception.BusinessException;
import com.converter.common.exception.ErrorCode;
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

    /**
     * Numero de compte bancaire (CHINESE_BANK_ACCOUNT) ou reference libre complementaire au code
     * QR (ALIPAY/WECHAT_PAY, {@link #qrCodeStorageKey}) — jamais l'identifiant reel de ces deux
     * derniers types, qui est une image, pas un texte.
     */
    @Column(name = "identifier", length = 120)
    private String identifier;

    @Column(name = "bank_name", length = 120)
    private String bankName;

    @Column(name = "bank_branch", length = 120)
    private String bankBranch;

    @Column(name = "qr_code_storage_key")
    private String qrCodeStorageKey;

    @Column(name = "qr_code_file_name")
    private String qrCodeFileName;

    @Column(name = "qr_code_content_type", length = 100)
    private String qrCodeContentType;

    @Column(name = "qr_code_size_bytes")
    private Long qrCodeSizeBytes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Beneficiary() {
        // Requis par JPA.
    }

    public Beneficiary(UUID orderId, BeneficiaryType type, String fullName, String identifier,
                       String bankName, String bankBranch, String qrCodeStorageKey, String qrCodeFileName,
                       String qrCodeContentType, Long qrCodeSizeBytes, Instant createdAt) {
        if (type == BeneficiaryType.CHINESE_BANK_ACCOUNT && (bankName == null || bankName.isBlank())) {
            // BusinessException (jamais IllegalArgumentException, non capturee par un
            // @ExceptionHandler specifique -- tombait en 500 opaque au lieu d'un 400 exploitable).
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "bankName est obligatoire pour un CHINESE_BANK_ACCOUNT.");
        }
        this.orderId = orderId;
        this.type = type;
        this.fullName = fullName;
        this.identifier = identifier;
        this.bankName = bankName;
        this.bankBranch = bankBranch;
        this.qrCodeStorageKey = qrCodeStorageKey;
        this.qrCodeFileName = qrCodeFileName;
        this.qrCodeContentType = qrCodeContentType;
        this.qrCodeSizeBytes = qrCodeSizeBytes;
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

    public String getQrCodeStorageKey() {
        return qrCodeStorageKey;
    }

    public String getQrCodeFileName() {
        return qrCodeFileName;
    }

    public String getQrCodeContentType() {
        return qrCodeContentType;
    }

    public Long getQrCodeSizeBytes() {
        return qrCodeSizeBytes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
