package com.converter.supplier.dto;

import com.converter.order.domain.BeneficiaryType;
import com.converter.supplier.domain.Purpose;
import com.converter.supplier.domain.SupplierStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/** Ligne de liste — {@code maskedAccountNumber} masque tout sauf les 4 derniers caracteres. */
@Schema(description = "Ligne de la liste des fournisseurs")
public record SupplierSummaryResponse(
        UUID id,
        BeneficiaryType type,
        String displayName,
        String country,
        String city,

        @Schema(description = "Identifiant de compte masque, ex. ******1234", example = "******1234")
        String maskedAccountNumber,

        Purpose purpose,
        boolean favorite,
        SupplierStatus status,
        Instant createdAt
) {
}
