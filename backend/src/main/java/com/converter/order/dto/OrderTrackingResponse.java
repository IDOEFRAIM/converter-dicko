package com.converter.order.dto;

import com.converter.order.domain.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Vue agregee de progression d'un ordre — projection en lecture seule de l'etat existant
 * ({@code OrderStatus}/{@code OrderStatusHistory}/{@code Payment}/{@code Settlement}/
 * {@code Refund}), jamais une seconde machine d'etat ni une nouvelle source de verite.
 *
 * @param currentStatus lu directement depuis {@code Order.status}, jamais recalcule depuis la
 *                      timeline (voir {@code OrderTrackingService}).
 * @param completedAt   lu directement depuis {@code Order.completedAt}, {@code null} tant que
 *                      l'ordre n'est pas {@code COMPLETED}.
 */
@Schema(description = "Timeline de suivi d'un ordre")
public record OrderTrackingResponse(
        UUID orderId,
        OrderStatus currentStatus,
        Instant createdAt,
        Instant completedAt,
        List<TrackingEvent> timeline
) {
}
