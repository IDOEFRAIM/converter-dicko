package com.converter.ledger.dto;

import com.converter.ledger.domain.LedgerEntryType;
import com.converter.treasury.domain.Currency;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Mouvement du grand livre interne")
public record LedgerEntryResponse(

        UUID id,

        @Schema(description = "Ordre concerne, absent pour un ajustement manuel")
        UUID orderId,

        LedgerEntryType entryType,

        Currency currency,

        BigDecimal amount,

        String description,

        Instant createdAt
) {
}
