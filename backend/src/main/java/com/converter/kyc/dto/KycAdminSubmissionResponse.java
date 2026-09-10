package com.converter.kyc.dto;

import com.converter.kyc.domain.KycDocumentType;
import com.converter.kyc.domain.KycSubmissionStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Vue administrateur d'un dossier KYC a examiner. Les fichiers ne sont pas inlines : l'admin les
 * recupere via {@code GET /api/admin/kyc/submissions/{id}/files/{kind}}.
 */
public record KycAdminSubmissionResponse(
        UUID id,
        UUID userId,
        String userFullName,
        String userPhone,
        KycSubmissionStatus status,
        KycDocumentType documentType,
        boolean hasBack,
        Instant submittedAt
) {
}
