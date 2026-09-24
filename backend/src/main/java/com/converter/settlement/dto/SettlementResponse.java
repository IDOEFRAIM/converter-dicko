package com.converter.settlement.dto;

import com.converter.order.domain.BeneficiaryType;
import com.converter.settlement.domain.SettlementStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Reglement CNY d'un ordre")
public record SettlementResponse(
        UUID id,
        UUID orderId,
        SettlementStatus status,
        BigDecimal amountCny,
        BeneficiaryType method,
        String beneficiaryFullName,
        String beneficiaryIdentifier,
        String beneficiaryBankName,
        String beneficiaryBankBranch,
        String settlementReference,
        String notes,
        UUID executedBy,
        List<SettlementProofResponse> proofs,
        Instant createdAt,
        Instant executedAt
) {
}
