package com.converter.quote.dto;

import com.converter.quote.domain.QuoteDirection;
import com.converter.quote.domain.QuoteStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Devis tel qu'expose au client.
 *
 * <p><b>Ne porte deliberement pas {@code marketRate} ni
 * {@code marginPercentage}</b> : ce sont des donnees commercialement
 * sensibles (voir docs/ARCHITECTURE.md, Partie I, section P). Le
 * client voit le taux qui lui est reellement propose
 * ({@code customerRate}) et les frais qui lui sont factures
 * ({@code feeXof}), jamais leur decomposition interne.
 */
@Schema(description = "Devis client")
public record QuoteResponse(
        UUID id,
        QuoteDirection direction,
        BigDecimal amountXof,
        BigDecimal amountCny,
        BigDecimal customerRate,
        BigDecimal feeXof,
        BigDecimal netAmountXof,
        QuoteStatus status,
        Instant createdAt,
        Instant expiresAt
) {
}
