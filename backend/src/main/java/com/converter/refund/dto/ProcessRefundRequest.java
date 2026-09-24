package com.converter.refund.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Enregistrement du decaissement reel d'un remboursement")
public record ProcessRefundRequest(

        @NotBlank(message = "La reference de transaction est obligatoire")
        @Size(max = 100)
        String transactionReference
) {
}
