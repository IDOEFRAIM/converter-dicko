package com.converter.supplier.dto;

import com.converter.order.domain.BeneficiaryType;
import com.converter.supplier.domain.Purpose;
import com.converter.supplier.domain.SupplierStatus;
import com.converter.treasury.domain.Currency;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * Detail complet d'un fournisseur, y compris l'identifiant de compte en clair — reserve a une
 * consultation deliberee par son proprietaire (jamais expose dans une liste, voir {@link
 * SupplierSummaryResponse}).
 */
@Schema(description = "Detail complet d'un fournisseur enregistre")
public record SupplierDetailResponse(
        UUID id,
        BeneficiaryType type,
        String displayName,
        String legalName,
        String phone,
        String email,
        String country,
        String city,
        String province,
        String bankName,
        String bankBranch,
        String accountName,
        String accountNumber,
        String bankAddress,
        String swiftCode,
        Currency currency,
        Purpose purpose,
        String notes,
        boolean favorite,
        SupplierStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
