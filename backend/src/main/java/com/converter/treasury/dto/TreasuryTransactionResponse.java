package com.converter.treasury.dto;

import com.converter.treasury.domain.TreasuryTransactionType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Ecriture du ledger de tresorerie")
public record TreasuryTransactionResponse(
        UUID id,
        UUID accountId,
        TreasuryTransactionType type,
        BigDecimal amount,
        BigDecimal balanceAfter,
        BigDecimal reservedAfter,
        UUID orderId,
        UUID performedBy,
        String reason,
        Instant createdAt
) {
}
