package com.converter.rate.publicrate.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Une entree de l'historique public du taux client. <b>Uniquement</b> ces trois champs —
 * jamais {@code breakEvenRate}, marge, frais, {@code costConfigurationId} ni aucune autre donnee
 * de pricing interne. Convention de sens identique a {@code RateEngine} et aux devis existants :
 * {@code 1 CNY = customerRate XOF}.
 */
@Schema(description = "Taux client historique, convention 1 CNY = customerRate XOF")
public record PublicRateHistoryEntry(
        String currencyPair,
        BigDecimal customerRate,
        Instant recordedAt
) {
}
