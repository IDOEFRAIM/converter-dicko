package com.converter.kyc.domain;

import com.converter.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/**
 * Dossier de verification d'identite soumis par un utilisateur (remarque produit #6).
 *
 * <p>Revue <b>manuelle interne</b> : un administrateur approuve ou rejette. L'approbation delegue
 * a {@code User#verifyKyc} — {@code users.kyc_verified} reste l'unique source de verite consommee
 * par {@code OrderService}. Les fichiers (recto/verso/selfie) ne transitent jamais par la base :
 * seules leurs cles de stockage ({@code FileStorageService}) sont conservees ici.
 */
@Entity
@Table(name = "kyc_submissions")
public class KycSubmission extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 24, updatable = false)
    private KycDocumentType documentType;

    @Column(name = "doc_front_key", nullable = false, updatable = false)
    private String docFrontKey;

    @Column(name = "doc_back_key", updatable = false)
    private String docBackKey;

    @Column(name = "selfie_key", nullable = false, updatable = false)
    private String selfieKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private KycSubmissionStatus status;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected KycSubmission() {
        // Requis par JPA.
    }

    public KycSubmission(UUID userId, KycDocumentType documentType, String docFrontKey, String docBackKey,
                         String selfieKey, Instant submittedAt) {
        this.userId = userId;
        this.documentType = documentType;
        this.docFrontKey = docFrontKey;
        this.docBackKey = docBackKey;
        this.selfieKey = selfieKey;
        this.status = KycSubmissionStatus.PENDING;
        this.submittedAt = submittedAt;
    }

    public void approve(UUID adminId, Instant at) {
        this.status = KycSubmissionStatus.APPROVED;
        this.reviewedBy = adminId;
        this.reviewedAt = at;
        this.rejectionReason = null;
    }

    public void reject(UUID adminId, Instant at, String reason) {
        this.status = KycSubmissionStatus.REJECTED;
        this.reviewedBy = adminId;
        this.reviewedAt = at;
        this.rejectionReason = reason;
    }

    public boolean isPending() {
        return status == KycSubmissionStatus.PENDING;
    }

    public UUID getUserId() {
        return userId;
    }

    public KycDocumentType getDocumentType() {
        return documentType;
    }

    public String getDocFrontKey() {
        return docFrontKey;
    }

    public String getDocBackKey() {
        return docBackKey;
    }

    public String getSelfieKey() {
        return selfieKey;
    }

    public KycSubmissionStatus getStatus() {
        return status;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public UUID getReviewedBy() {
        return reviewedBy;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }
}
