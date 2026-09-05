package com.converter.order.receipt.model;

import com.converter.order.domain.BeneficiaryType;

/**
 * Projection documentaire du snapshot {@code Beneficiary} de l'ordre — jamais du {@code Supplier}
 * actuel (voir {@code OrderReceiptService}). {@code maskedIdentifier} est deja masque au moment de
 * la construction du modele (jamais dans {@code ReceiptPdfGenerator}, qui ne doit connaitre aucune
 * regle de securite) : meme convention que {@code SupplierService} (derniers 4 caracteres visibles).
 */
public record ReceiptBeneficiary(
        String fullName,
        BeneficiaryType type,
        String maskedIdentifier,
        String bankName,
        String bankBranch
) {
}
