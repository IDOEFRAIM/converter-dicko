package com.converter.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(description = "Creation d'un ordre a partir d'un devis deja accepte")
public record CreateOrderRequest(

        @NotNull(message = "L'identifiant du devis est obligatoire")
        UUID quoteId,

        @Valid
        @NotNull(message = "Le beneficiaire est obligatoire")
        BeneficiaryRequest beneficiary,

        @Size(max = 500)
        String note
) {
}
