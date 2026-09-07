package com.converter.pool.dto;

import com.converter.pool.domain.PoolStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * {@code viewerIsParticipant}/{@code viewerIsCreator} sont relatifs a l'appelant authentifie --
 * n'importe quel compte peut consulter une Ruee (previsualisation avant de rejoindre, via le code
 * partage), pas seulement ses participants.
 */
public record PoolResponse(
        UUID id,
        String code,
        UUID creatorId,
        String currencyPair,
        BigDecimal targetAmountXof,
        BigDecimal currentAmountXof,
        PoolStatus status,
        int participantCount,
        BigDecimal rewardMarginReductionPercentage,
        Instant createdAt,
        Instant expiresAt,
        Instant succeededAt,
        Instant expiredAt,
        Instant cancelledAt,
        boolean viewerIsParticipant,
        boolean viewerIsCreator
) {
}
