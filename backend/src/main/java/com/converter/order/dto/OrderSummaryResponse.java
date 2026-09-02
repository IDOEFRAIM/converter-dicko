package com.converter.order.dto;

import com.converter.order.domain.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Ligne de la liste des ordres")
public record OrderSummaryResponse(
        UUID id,
        String reference,
        OrderStatus status,
        BigDecimal amountXof,
        BigDecimal amountCny,
        Instant createdAt
) {
}
