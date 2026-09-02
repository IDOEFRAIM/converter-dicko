package com.converter.settings.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Nouvelle valeur d'un parametre metier. */
@Schema(description = "Modification d'un parametre metier")
public record UpdateSettingRequest(

        @NotBlank(message = "La valeur est obligatoire")
        @Size(max = 255, message = "La valeur ne peut depasser 255 caracteres")
        @Schema(description = "Valeur textuelle, convertie selon le type du parametre",
                example = "15000")
        String value
) {
}
