package com.converter.treasury.dto;

import com.converter.treasury.domain.Currency;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Solde d'un compte de tresorerie")
public record TreasuryAccountResponse(
        UUID id,
        Currency currency,
        BigDecimal balance,
        BigDecimal reservedBalance,
        BigDecimal available,
        BigDecimal lowThreshold,
        Instant updatedAt
) {
}
