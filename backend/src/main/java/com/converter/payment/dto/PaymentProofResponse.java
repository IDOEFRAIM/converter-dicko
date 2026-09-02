package com.converter.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Preuve de paiement")
public record PaymentProofResponse(
        UUID id,
        String fileName,
        String contentType,
        long sizeBytes,
        Instant uploadedAt
) {
}
