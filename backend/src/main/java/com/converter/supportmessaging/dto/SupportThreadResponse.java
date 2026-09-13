package com.converter.supportmessaging.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Schema(description = "Fil de messagerie SAV complet, dans l'ordre chronologique")
public record SupportThreadResponse(
        UUID userId,
        List<SupportMessageResponse> messages
) {
}
