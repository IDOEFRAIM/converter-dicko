package com.converter.transfi.web;

import com.converter.common.api.ApiResponse;
import com.converter.common.api.PageResponse;
import com.converter.security.AuthenticatedUser;
import com.converter.security.CurrentUser;
import com.converter.transfi.dto.TransfiOrderResponse;
import com.converter.transfi.service.TransfiOrchestrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Integration TransFi BizPay, Phase 1 (voir {@code docs/TRANSFI_INTEGRATION.md}) — reserve a
 * l'administration. {@link #routePayin} est un geste EXPLICITE : aucun ordre n'est jamais routé
 * automatiquement vers TransFi a sa creation, le flux manuel reste le chemin par defaut pour tous
 * les clients (voir {@code AdminSettlementController}).
 */
@RestController
@RequestMapping("/api/admin/transfi")
@SecurityRequirement(name = "bearer-jwt")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Administration - TransFi", description = "Payin/payout automatises TransFi BizPay (Phase 1)")
public class AdminTransfiController {

    private final TransfiOrchestrationService orchestrationService;

    public AdminTransfiController(TransfiOrchestrationService orchestrationService) {
        this.orchestrationService = orchestrationService;
    }

    @PostMapping("/orders/{orderId}/payin")
    @Operation(summary = "Router un ordre AWAITING_PAYMENT vers un payin TransFi",
            description = "Geste explicite d'un administrateur -- ne fait jamais passer l'ordre a un autre "
                    + "statut ici : c'est la confirmation TransFi (webhook) qui le fera. Echoue avec 503 si "
                    + "l'integration TransFi n'est pas activee (app.transfi.enabled=false ou identifiants absents).")
    public ResponseEntity<ApiResponse<TransfiOrderResponse>> routePayin(
            @PathVariable UUID orderId, @AuthenticatedUser CurrentUser actor) {
        TransfiOrderResponse response = TransfiOrderResponse.from(orchestrationService.routePayin(orderId, actor.getId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response, "Payin TransFi cree."));
    }

    @GetMapping("/orders")
    @Operation(summary = "Lister les ordres TransFi (payin/payout), du plus recent au plus ancien",
            description = "Vue de rapprochement -- statut cote TransFi tel que rapporte par le dernier webhook recu.")
    public ResponseEntity<ApiResponse<PageResponse<TransfiOrderResponse>>> list(
            @PageableDefault(size = 30) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.of(orchestrationService.list(pageable)));
    }
}
