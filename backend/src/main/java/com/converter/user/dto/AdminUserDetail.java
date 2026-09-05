package com.converter.user.dto;

import com.converter.user.domain.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Fiche complete d'un compte, cote administration. */
@Schema(description = "Detail d'un compte pour l'espace d'administration")
public record AdminUserDetail(
        UUID id,
        String phone,
        String firstName,
        String lastName,
        String email,
        UserStatus status,
        Set<String> roles,
        Instant createdAt,
        Instant lastLoginAt,

        @Schema(description = "Horodatage du blocage, null si le compte est actif")
        Instant blockedAt,

        @Schema(description = "Motif du blocage, null si le compte est actif")
        String blockedReason,

        @Schema(description = "Identite verifiee (KYC) -- necessaire pour un ordre au-dela du seuil configure")
        boolean kycVerified,

        @Schema(description = "Horodatage de la verification KYC, null si non verifie")
        Instant kycVerifiedAt,

        long orderCount,
        BigDecimal totalAmountCfa
) {
}
