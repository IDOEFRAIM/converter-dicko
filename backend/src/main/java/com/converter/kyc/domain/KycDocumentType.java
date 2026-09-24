package com.converter.kyc.domain;

/**
 * Piece d'identite acceptee pour un dossier KYC (remarque produit #6). Stocke en {@code VARCHAR}
 * + {@code CHECK} cote base (V32), jamais un type SQL enum.
 */
public enum KycDocumentType {
    /** CNIB au Burkina Faso. */
    NATIONAL_ID,
    PASSPORT,
    /** Carte de sejour / titre de sejour. */
    RESIDENCE_PERMIT
}
