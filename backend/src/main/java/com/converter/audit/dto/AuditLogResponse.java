package com.converter.audit.dto;

import com.converter.audit.domain.AuditAction;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/** Entree du journal d'audit exposee a l'administration. */
@Schema(description = "Entree du journal d'audit")
public record AuditLogResponse(
        UUID id,

        @Schema(description = "Identifiant de l'acteur, null si l'action est systeme")
        UUID actorId,

        @Schema(description = "Numero de l'acteur au moment de l'action")
        String actorPhone,

        AuditAction action,
        String entityType,
        String entityId,

        @Schema(description = "Contexte de l'action, au format JSON")
        String metadata,

        String ipAddress,
        Instant createdAt
) {
}
