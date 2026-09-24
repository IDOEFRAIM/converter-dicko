package com.converter.rate.web;

import com.converter.common.api.ApiResponse;
import com.converter.common.api.PageResponse;
import com.converter.rate.dto.PublishRateRequest;
import com.converter.rate.dto.RateSourceResponse;
import com.converter.rate.provider.RateProvider;
import com.converter.rate.service.RateAdminService;
import com.converter.security.AuthenticatedUser;
import com.converter.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gestion du taux manuel courant, reservee aux administrateurs.
 *
 * <p>Perimetre volontairement minimal (Phase 3, section 8) : publier un
 * nouveau taux et consulter l'historique. Aucune autre operation
 * (suppression, modification d'une cotation deja publiee) n'existe —
 * {@code rate_sources} est append-only.
 */
@RestController
@RequestMapping("/api/admin/rates")
@SecurityRequirement(name = "bearer-jwt")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Administration - Taux", description = "Publication et historique du taux manuel XOF/CNY")
public class AdminRateController {

    private final RateAdminService rateAdminService;

    public AdminRateController(RateAdminService rateAdminService) {
        this.rateAdminService = rateAdminService;
    }

    @PostMapping
    @Operation(summary = "Publier un nouveau taux manuel courant",
            description = "Cloture la cotation courante puis publie la nouvelle, dans la meme transaction. "
                    + "Ne modifie jamais un devis deja emis (snapshot immuable).")
    public ResponseEntity<ApiResponse<RateSourceResponse>> publish(
            @Valid @RequestBody PublishRateRequest request,
            @AuthenticatedUser CurrentUser actor) {
        RateSourceResponse result = rateAdminService.publishManualRate(
                request.cfaPerCny(), request.note(), RateProvider.DEFAULT_CURRENCY_PAIR, actor.getId());
        return ResponseEntity.ok(ApiResponse.of(result, "Taux publie."));
    }

    @GetMapping
    @Operation(summary = "Historique des taux manuels publies")
    public ResponseEntity<ApiResponse<PageResponse<RateSourceResponse>>> history(
            @Parameter(hidden = true) @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.of(PageResponse.from(
                rateAdminService.history(RateProvider.DEFAULT_CURRENCY_PAIR, pageable))));
    }
}
