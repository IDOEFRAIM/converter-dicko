package com.converter.settlement.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Preuve de reglement")
public record SettlementProofResponse(
        UUID id,
        String fileName,
        String contentType,
        long sizeBytes,
        Instant uploadedAt
) {
}
