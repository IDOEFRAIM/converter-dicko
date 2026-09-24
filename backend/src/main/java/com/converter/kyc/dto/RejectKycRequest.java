package com.converter.kyc.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corps du rejet d'un dossier KYC — un motif est toujours exige (l'utilisateur doit savoir quoi corriger). */
public record RejectKycRequest(
        @NotBlank @Size(max = 500) String reason
) {
}
