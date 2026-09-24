package com.converter.order.dto;

import com.converter.order.domain.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Une entree de la timeline de suivi.
 *
 * @param status uniquement renseigne lorsque cet evenement correspond reellement a une valeur de
 *               {@code OrderStatus} atteinte par l'ordre (derive d'une ligne
 *               {@code OrderStatusHistory}) — {@code null} pour les evenements d'enrichissement
 *               ({@code PAYMENT_REJECTED}, {@code SETTLEMENT_EXECUTED}, {@code REFUND_*}), qui ne
 *               representent jamais un statut d'ordre invente (voir {@code OrderTrackingService}).
 * @param label  commodite d'affichage uniquement — voir {@link TrackingEventCode}. Le frontend
 *               doit tester {@code code}, jamais {@code label}.
 */
@Schema(description = "Entree de la timeline de suivi d'un ordre")
public record TrackingEvent(
        TrackingEventCode code,
        OrderStatus status,
        Instant occurredAt,
        String label
) {
}
