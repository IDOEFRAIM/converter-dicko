package com.converter.settings.dto;

import com.converter.settings.domain.SettingType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/** Parametre metier tel qu'expose a l'administration. */
@Schema(description = "Parametre metier administrable")
public record SettingResponse(
        String key,
        String value,
        SettingType type,
        String description,

        @Schema(description = "Vrai si le parametre est expose aux clients anonymes")
        boolean isPublic,

        Instant updatedAt
) {
}
