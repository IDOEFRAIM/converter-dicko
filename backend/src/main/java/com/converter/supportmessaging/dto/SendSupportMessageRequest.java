package com.converter.supportmessaging.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Nouveau message dans un fil de messagerie SAV")
public record SendSupportMessageRequest(

        @NotBlank(message = "Le message ne peut pas etre vide")
        @Size(max = 2000)
        String body
) {
}
