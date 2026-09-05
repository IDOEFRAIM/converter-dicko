package com.converter.treasury.dto;

import com.converter.treasury.domain.Currency;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(description = "Depot ou correction comptable manuelle")
public record TreasuryAdjustmentRequest(

        @NotNull(message = "La devise est obligatoire")
        Currency currency,

        @NotNull(message = "Le montant est obligatoire")
        @Digits(integer = 19, fraction = 2, message = "Le montant depasse la precision autorisee")
        @Schema(description = "Positif pour un depot/ajout, negatif pour un ajustement a la baisse")
        BigDecimal amount,

        @NotBlank(message = "Le motif est obligatoire")
        @Size(max = 500)
        String reason
) {
}
