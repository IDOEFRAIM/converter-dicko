package com.converter.supportmessaging.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/** Ligne de la boite de reception admin — un fil par utilisateur, tries par activite recente. */
@Schema(description = "Ligne de la liste des fils de messagerie SAV (cote admin)")
public record SupportThreadSummaryResponse(
        UUID userId,
        String userDisplayName,
        String userPhone,
        String lastMessageBody,
        boolean lastMessageFromAdmin,
        Instant lastMessageAt,
        @Schema(description = "true si l'utilisateur a ecrit depuis la derniere consultation de l'admin")
        boolean hasUnreadFromUser
) {
}
