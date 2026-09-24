package com.converter.admin;

import com.converter.audit.domain.AuditAction;
import com.converter.audit.dto.AuditLogResponse;
import com.converter.audit.service.AuditService;
import com.converter.common.api.ApiResponse;
import com.converter.common.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * Consultation du journal d'audit.
 *
 * <p>Lecture seule par construction : le journal n'expose aucune
 * operation d'ecriture ni de suppression a l'administration, y compris
 * a un compte ADMIN. Alterer l'historique detruirait sa valeur
 * probante.
 */
@RestController
@RequestMapping("/api/admin/audit-logs")
@SecurityRequirement(name = "bearer-jwt")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Administration - Audit", description = "Journal en lecture seule des operations sensibles")
public class AdminAuditLogController {

    private final AuditService auditService;

    public AdminAuditLogController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    @Operation(summary = "Rechercher dans le journal d'audit",
            description = "Filtrage combinable par acteur, type d'action, entite et periode.")
    public ResponseEntity<ApiResponse<PageResponse<AuditLogResponse>>> search(
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @Parameter(hidden = true) @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.of(
                auditService.search(actorId, action, entityType, entityId, from, to, pageable)));
    }
}
