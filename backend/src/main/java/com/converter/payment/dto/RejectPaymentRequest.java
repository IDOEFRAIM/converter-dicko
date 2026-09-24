package com.converter.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Rejet d'un paiement soumis")
public record RejectPaymentRequest(

        @NotBlank(message = "Le motif du rejet est obligatoire")
        @Size(max = 500)
        String reason
) {
}
