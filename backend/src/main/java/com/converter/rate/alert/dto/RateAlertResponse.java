package com.converter.rate.alert.dto;

import com.converter.preferredrate.domain.PreferredRateDirection;
import com.converter.rate.alert.domain.RateAlertStatus;
import com.converter.rate.alert.domain.RateComparison;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Volontairement minimal : ni {@code currentRate} ni {@code gap} (contrairement a
 * {@code PreferredRateRequestResponse}) — cette alerte n'a pas de raison metier de recalculer une
 * cotation a chaque lecture, ce qui garde {@code list}/{@code get} strictement en lecture seule,
 * sans le contournement de verrouillage que {@code PreferredRateService} doit faire pour la meme
 * raison.
 */
public record RateAlertResponse(
        UUID id,
        String currencyPair,
        PreferredRateDirection direction,
        BigDecimal targetRate,
        RateComparison comparison,
        RateAlertStatus status,
        Instant createdAt,
        Instant expiresAt,
        Instant triggeredAt,
        Instant cancelledAt,
        Instant expiredAt
) {
}
