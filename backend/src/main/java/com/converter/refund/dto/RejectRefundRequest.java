package com.converter.refund.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Rejet d'un remboursement en attente")
public record RejectRefundRequest(

        @NotBlank(message = "Le motif du rejet est obligatoire")
        @Size(max = 500)
        String reason
) {
}
