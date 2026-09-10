package com.converter.pool.dto;

import com.converter.pool.domain.PoolStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * {@code viewerIsParticipant}/{@code viewerIsCreator} sont relatifs a l'appelant authentifie --
 * n'importe quel compte peut consulter une Ruee (previsualisation avant de rejoindre, via le code
 * partage), pas seulement ses participants.
 *
 * <p>{@code rewardMarginReductionPercentage} est la reduction <b>calculee a l'instant present</b>
 * a partir du nombre de participants et du volume deja echange (remarque produit #4) — pour une
 * Ruee reussie, c'est la valeur reellement accordee. Les champs {@code reward*} qui suivent
 * exposent la formule pour que le client l'explique ("base A% + B%/participant + C%/M XOF,
 * plafond D%").
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
        BigDecimal rewardBasePercentage,
        BigDecimal rewardPerParticipantPercentage,
        BigDecimal rewardPerMillionXofPercentage,
        BigDecimal rewardMaxPercentage,
        Instant createdAt,
        Instant expiresAt,
        Instant succeededAt,
        Instant expiredAt,
        Instant cancelledAt,
        boolean viewerIsParticipant,
        boolean viewerIsCreator
) {
}
