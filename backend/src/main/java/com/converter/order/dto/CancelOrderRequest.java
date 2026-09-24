package com.converter.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Annulation d'un ordre")
public record CancelOrderRequest(

        @NotBlank(message = "Le motif d'annulation est obligatoire")
        @Size(max = 500)
        String reason
) {
}
