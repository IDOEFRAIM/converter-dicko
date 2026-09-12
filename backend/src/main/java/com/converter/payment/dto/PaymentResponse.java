package com.converter.payment.dto;

import com.converter.payment.domain.PaymentMethod;
import com.converter.payment.domain.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Paiement declare pour un ordre")
public record PaymentResponse(
        UUID id,
        UUID orderId,
        PaymentMethod method,
        PaymentStatus status,
        BigDecimal expectedAmountXof,
        BigDecimal receivedAmountXof,
        String transactionReference,
        String payerPhone,
        String payerName,
        String rejectionReason,
        List<PaymentProofResponse> proofs,
        Instant submittedAt,
        Instant confirmedAt,
        Instant rejectedAt
) {
}
