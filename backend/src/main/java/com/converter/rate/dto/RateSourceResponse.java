package com.converter.rate.dto;

import com.converter.rate.domain.RateProviderType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Cotation exposee a l'administration.
 *
 * <p>Reponse strictement reservee aux endpoints {@code /api/admin/**} :
 * le taux de marche brut est une donnee commercialement sensible,
 * jamais exposee telle quelle au client (voir docs/ARCHITECTURE.md,
 * Partie I, section P).
 */
@Schema(description = "Cotation de taux manuel")
public record RateSourceResponse(
        UUID id,
        RateProviderType providerType,
        String currencyPair,
        BigDecimal cfaPerCny,
        Instant effectiveFrom,
        Instant effectiveTo,
        String note,
        Instant createdAt
) {
}
