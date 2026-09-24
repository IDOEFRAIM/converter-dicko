package com.converter.supportmessaging.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Un message du fil de messagerie SAV")
public record SupportMessageResponse(
        UUID id,
        @Schema(description = "true si envoye par un administrateur -- jamais lequel precisement, "
                + "le fil est un support unique, pas une conversation nominative")
        boolean fromAdmin,
        String body,
        Instant createdAt
) {
}
