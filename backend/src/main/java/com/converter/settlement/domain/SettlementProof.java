package com.converter.settlement.domain;

import com.converter.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/** Metadonnees d'une preuve de reglement (capture d'ecran, recu). */
@Entity
@Table(name = "settlement_proofs")
@Immutable
public class SettlementProof extends BaseEntity {

    @Column(name = "settlement_id", nullable = false)
    private UUID settlementId;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;

    @Column(name = "storage_provider", nullable = false, length = 16)
    private String storageProvider;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "checksum_sha256", nullable = false, length = 64)
    private String checksumSha256;

    @Column(name = "uploaded_by", nullable = false)
    private UUID uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    protected SettlementProof() {
        // Requis par JPA.
    }

    public SettlementProof(UUID settlementId, String fileName, String contentType, String storageKey,
                           String storageProvider, long sizeBytes, String checksumSha256,
                           UUID uploadedBy, Instant uploadedAt) {
        this.settlementId = settlementId;
        this.fileName = fileName;
        this.contentType = contentType;
        this.storageKey = storageKey;
        this.storageProvider = storageProvider;
        this.sizeBytes = sizeBytes;
        this.checksumSha256 = checksumSha256;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = uploadedAt;
    }

    public UUID getSettlementId() {
        return settlementId;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getStorageProvider() {
        return storageProvider;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getChecksumSha256() {
        return checksumSha256;
    }

    public UUID getUploadedBy() {
        return uploadedBy;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }
}
