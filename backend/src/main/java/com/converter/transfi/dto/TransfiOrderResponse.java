package com.converter.transfi.dto;

import com.converter.transfi.domain.TransfiOrder;
import com.converter.transfi.domain.TransfiOrderDirection;
import com.converter.transfi.domain.TransfiOrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Ordre payin/payout TransFi -- vue de rapprochement (admin)")
public record TransfiOrderResponse(

        UUID id,

        UUID orderId,

        TransfiOrderDirection direction,

        String providerOrderId,

        TransfiOrderStatus status,

        String payUrl,

        Instant createdAt,

        Instant updatedAt
) {

    public static TransfiOrderResponse from(TransfiOrder entity) {
        return new TransfiOrderResponse(entity.getId(), entity.getOrderId(), entity.getDirection(),
                entity.getProviderOrderId(), entity.getStatus(), entity.getPayUrl(), entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
