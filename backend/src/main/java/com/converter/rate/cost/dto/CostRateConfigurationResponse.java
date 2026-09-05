package com.converter.rate.cost.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Configuration de cout exposee a l'administration.
 *
 * <p>Reponse strictement reservee aux endpoints {@code /api/admin/**} :
 * {@code breakEvenRate} et les parametres qui le composent sont des
 * donnees internes confidentielles (cout de revient), jamais exposees
 * au client — aucun DTO public ne porte ces champs.
 */
@Schema(description = "Configuration de cout de revient XOF -> USD -> CNY")
public record CostRateConfigurationResponse(
        UUID id,
        LocalDate businessDate,
        BigDecimal rateXofUsd,
        BigDecimal rateUsdCny,
        BigDecimal feeXofUsdPercent,
        BigDecimal feeUsdCnyFixedUsd,
        BigDecimal referenceAmountXof,
        BigDecimal breakEvenRate,
        String note,
        Instant createdAt
) {
}
