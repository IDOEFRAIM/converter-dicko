package com.converter.user.dto;

import com.converter.user.domain.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Ligne de la liste des comptes, cote administration. */
@Schema(description = "Resume d'un compte pour l'espace d'administration")
public record AdminUserSummary(
        UUID id,
        String phone,
        String fullName,
        UserStatus status,
        Instant createdAt,

        @Schema(description = "Nombre total d'ordres crees par ce compte")
        long orderCount,

        @Schema(description = "Volume cumule en CFA, tous ordres confondus")
        BigDecimal totalAmountCfa
) {
}
