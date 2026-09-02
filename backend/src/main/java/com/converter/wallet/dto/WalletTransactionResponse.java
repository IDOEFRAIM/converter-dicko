package com.converter.wallet.dto;

import com.converter.wallet.domain.WalletTransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WalletTransactionResponse(
        UUID id,
        WalletTransactionType type,
        BigDecimal amount,
        BigDecimal balanceAfter,
        BigDecimal reservedAfter,
        UUID referenceId,
        String reason,
        Instant createdAt
) {
}
