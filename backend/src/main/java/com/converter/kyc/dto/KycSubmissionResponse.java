package com.converter.kyc.dto;

import com.converter.kyc.domain.KycDocumentType;
import com.converter.kyc.domain.KycSubmission;
import com.converter.kyc.domain.KycSubmissionStatus;

import java.time.Instant;

/**
 * Vue utilisateur de son dernier dossier KYC (remarque produit #6) — jamais les cles de fichiers
 * ni l'identite de l'administrateur qui a revu.
 */
public record KycSubmissionResponse(
        KycSubmissionStatus status,
        KycDocumentType documentType,
        Instant submittedAt,
        Instant reviewedAt,
        String rejectionReason
) {
    public static KycSubmissionResponse of(KycSubmission submission) {
        return new KycSubmissionResponse(
                submission.getStatus(),
                submission.getDocumentType(),
                submission.getSubmittedAt(),
                submission.getReviewedAt(),
                submission.getRejectionReason());
    }
}
