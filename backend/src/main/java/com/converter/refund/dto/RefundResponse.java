package com.converter.refund.dto;

import com.converter.refund.domain.RefundStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Remboursement — reserve a l'administration")
public record RefundResponse(
        UUID id,
        UUID orderId,
        UUID paymentId,
        BigDecimal amountXof,
        RefundStatus status,
        String reason,
        String rejectionReason,
        String transactionReference,
        UUID createdBy,
        UUID processedBy,
        Instant createdAt,
        Instant processedAt
) {
}
