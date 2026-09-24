package com.converter.wallet.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WalletResponse(
        UUID id,
        BigDecimal balance,
        BigDecimal reservedBalance,
        BigDecimal available,
        Instant updatedAt
) {
}
