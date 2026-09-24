package com.converter.settlement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Execution manuelle d'un reglement")
public record ExecuteSettlementRequest(

        @NotBlank(message = "La reference du reglement est obligatoire")
        @Size(max = 100)
        @Schema(description = "Reference du virement/transfert execute manuellement en Chine")
        String settlementReference,

        @Size(max = 1000)
        String notes
) {
}
