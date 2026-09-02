package com.converter.preferredrate.dto;

import com.converter.preferredrate.domain.PreferredRateDirection;
import com.converter.preferredrate.domain.PreferredRateStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * {@code currentRate} est le taux client courant (RateProvider + marge,
 * comme un devis), recalcule a chaque lecture -- {@code null} si aucune
 * cotation n'est disponible ou si la phase n'est plus {@code WAITING}.
 * {@code gap} ({@code currentRate - targetRate}, memes conditions) est
 * fourni pour eviter toute soustraction cote frontend. {@code phase}
 * indique quelle des deux vues afficher -- voir {@link PreferredRatePhase}.
 * Rien n'est jamais calcule cote frontend.
 */
public record PreferredRateRequestResponse(
        UUID id,
        PreferredRateDirection direction,
        BigDecimal amountXof,
        BigDecimal targetRate,
        BigDecimal currentRate,
        BigDecimal gap,
        PreferredRatePhase phase,
        PreferredRateStatus status,
        BigDecimal achievedRate,
        Instant createdAt,
        Instant expiresAt,
        Instant executedAt,
        Instant expiredAt,
        Instant cancelledAt,
        ExchangeSummaryResponse exchange
) {
}
