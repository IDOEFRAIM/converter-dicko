package com.converter.order.dto;

import com.converter.order.domain.OrderStatus;
import com.converter.supplier.domain.Purpose;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Detail complet d'un ordre")
public record OrderDetailResponse(
        UUID id,
        String reference,
        UUID quoteId,
        OrderStatus status,
        BigDecimal amountXof,
        BigDecimal amountCny,
        BigDecimal customerRate,
        BigDecimal feeXof,
        BigDecimal netAmountXof,
        String note,
        String cancellationReason,
        String rejectionReason,
        BeneficiaryResponse beneficiary,
        List<OrderStatusHistoryResponse> statusHistory,
        Instant createdAt,
        Instant updatedAt,

        @Schema(description = "Echeance de paiement ; au-dela, l'ordre est expire et sa reservation liberee")
        Instant paymentDeadlineAt,

        Instant completedAt,
        Instant cancelledAt,

        @Schema(description = "Fournisseur enregistre utilise pour cet ordre, purement tracable — null si beneficiaire saisi directement")
        UUID supplierId,

        Purpose purpose,
        String purposeDetails
) {
}
