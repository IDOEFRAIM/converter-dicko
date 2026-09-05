package com.converter.supplier.dto;

import com.converter.order.domain.BeneficiaryType;
import com.converter.supplier.domain.Purpose;
import com.converter.treasury.domain.Currency;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Enregistrement d'un fournisseur/beneficiaire reutilisable")
public record CreateSupplierRequest(

        @NotNull(message = "Le type de fournisseur est obligatoire")
        BeneficiaryType type,

        @NotBlank(message = "Le nom d'affichage est obligatoire")
        @Size(max = 120)
        String displayName,

        @Size(max = 160)
        String legalName,

        @Size(max = 30)
        String phone,

        @Email(message = "Adresse email invalide")
        @Size(max = 160)
        String email,

        @Size(max = 100)
        String country,

        @Size(max = 100)
        String city,

        @Size(max = 100)
        String province,

        @Size(max = 120)
        @Schema(description = "Obligatoire si type = CHINESE_BANK_ACCOUNT")
        String bankName,

        @Size(max = 120)
        String bankBranch,

        @Size(max = 120)
        String accountName,

        @NotBlank(message = "L'identifiant de compte est obligatoire")
        @Size(max = 120)
        @Schema(description = "Compte Alipay / WeChat ou numero de compte bancaire")
        String accountNumber,

        @Size(max = 255)
        String bankAddress,

        @Size(max = 20)
        String swiftCode,

        @NotNull(message = "La devise est obligatoire")
        Currency currency,

        @Schema(description = "Classification par defaut, jamais copiee aveuglement sur un ordre")
        Purpose purpose,

        @Size(max = 1000)
        String notes
) {
}
