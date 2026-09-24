package com.converter.pool.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * {@code firstName} uniquement (jamais le nom complet ni le telephone) -- meme minimisation que
 * partout ailleurs ou l'identite d'un tiers est exposee (ex. {@code SupplierService#mask}).
 */
public record PoolParticipantResponse(
        UUID userId,
        String firstName,
        boolean isCreator,
        Instant joinedAt,
        BigDecimal contributedAmountXof
) {
}
