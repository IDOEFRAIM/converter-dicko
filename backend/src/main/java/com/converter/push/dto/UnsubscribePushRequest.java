package com.converter.push.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Desabonnement Web Push")
public record UnsubscribePushRequest(

        @NotBlank(message = "L'endpoint est obligatoire")
        String endpoint
) {
}
