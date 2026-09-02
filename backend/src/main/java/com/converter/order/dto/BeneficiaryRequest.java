package com.converter.order.dto;

import com.converter.order.domain.BeneficiaryType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Beneficiaire chinois de l'ordre")
public record BeneficiaryRequest(

        @NotNull(message = "Le type de beneficiaire est obligatoire")
        BeneficiaryType type,

        @NotBlank(message = "Le nom du beneficiaire est obligatoire")
        @Size(max = 120)
        String fullName,

        @NotBlank(message = "L'identifiant du beneficiaire est obligatoire")
        @Size(max = 120)
        @Schema(description = "Compte Alipay / WeChat ou numero de compte bancaire")
        String identifier,

        @Size(max = 120)
        @Schema(description = "Obligatoire si type = CHINESE_BANK_ACCOUNT")
        String bankName,

        @Size(max = 120)
        String bankBranch
) {
}
