package com.converter.order.dto;

import com.converter.order.domain.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Entree de l'historique des statuts d'un ordre")
public record OrderStatusHistoryResponse(
        OrderStatus fromStatus,
        OrderStatus toStatus,
        UUID changedBy,
        String reason,
        Instant createdAt
) {
}
