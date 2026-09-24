package com.converter.order.dto;

import com.converter.order.domain.OrderStatus;
import com.converter.supplier.domain.Purpose;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Ligne de l'historique enrichi des ordres (Phase 8)")
public record OrderHistoryResponse(
        UUID id,
        String reference,
        OrderStatus status,
        BigDecimal amountXof,
        BigDecimal amountCny,
        BigDecimal feeXof,
        BigDecimal customerRate,
        Purpose purpose,
        UUID supplierId,
        Instant createdAt,
        Instant completedAt
) {
}
