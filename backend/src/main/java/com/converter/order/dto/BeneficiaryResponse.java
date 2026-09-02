package com.converter.order.dto;

import com.converter.order.domain.BeneficiaryType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Beneficiaire tel qu'enregistre")
public record BeneficiaryResponse(
        BeneficiaryType type,
        String fullName,
        String identifier,
        String bankName,
        String bankBranch
) {
}
