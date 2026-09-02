package com.converter.preferredrate.dto;

import com.converter.preferredrate.domain.ExchangeStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * {@code stage} distingue T0/T+45/T+90/termine pour l'affichage de la
 * progression ; {@code deadlineAt} (demarrage + 2h) et
 * {@code nextUpdateAt} (prochaine notification attendue, {@code null}
 * une fois termine) sont calcules cote backend -- jamais recalcules
 * cote frontend.
 */
public record ExchangeSummaryResponse(
        UUID id,
        BigDecimal amountXof,
        BigDecimal achievedRate,
        BigDecimal amountCny,
        ExchangeStatus status,
        String stage,
        Instant startedAt,
        Instant deadlineAt,
        Instant nextUpdateAt,
        Instant completedAt
) {
}
