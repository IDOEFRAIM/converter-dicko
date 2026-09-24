package com.converter.kyc.domain;

/** Cycle de vie d'un dossier KYC : soumis -> approuve ou rejete (revue manuelle interne). */
public enum KycSubmissionStatus {
    PENDING,
    APPROVED,
    REJECTED
}
