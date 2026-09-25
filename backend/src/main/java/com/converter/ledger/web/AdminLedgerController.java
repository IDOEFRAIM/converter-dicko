package com.converter.ledger.web;

import com.converter.common.api.ApiResponse;
import com.converter.common.api.PageResponse;
import com.converter.ledger.dto.LedgerEntryResponse;
import com.converter.ledger.service.LedgerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Rapprochement financier, reserve a l'administration — lecture seule (voir
 * {@code docs/TRANSFI_INTEGRATION.md}, section "Reconciliation"). Aucune ecriture manuelle
 * exposee pour l'instant : un {@code ADJUSTMENT} se fait aujourd'hui via {@code LedgerService}
 * en base, une UI dediee viendra une fois le besoin reel observe.
 */
@RestController
@RequestMapping("/api/admin/ledger")
@SecurityRequirement(name = "bearer-jwt")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Administration - Ledger", description = "Grand livre interne (revenu de frais de service, couts prestataire)")
public class AdminLedgerController {

    private final LedgerService ledgerService;

    public AdminLedgerController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @GetMapping
    @Operation(summary = "Lister les mouvements du ledger, du plus recent au plus ancien")
    public ResponseEntity<ApiResponse<PageResponse<LedgerEntryResponse>>> list(
            @PageableDefault(size = 30) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.of(ledgerService.list(pageable)));
    }

    @GetMapping("/orders/{orderId}")
    @Operation(summary = "Mouvements du ledger pour un ordre precis")
    public ResponseEntity<ApiResponse<PageResponse<LedgerEntryResponse>>> listForOrder(
            @PathVariable UUID orderId, @PageableDefault(size = 30) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.of(ledgerService.listForOrder(orderId, pageable)));
    }
}
