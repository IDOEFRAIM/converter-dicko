package com.converter.rate.cost.web;

import com.converter.common.api.ApiResponse;
import com.converter.common.api.PageResponse;
import com.converter.rate.cost.dto.CostRateConfigurationResponse;
import com.converter.rate.cost.dto.PublishCostRateConfigurationRequest;
import com.converter.rate.cost.service.CostRateAdminService;
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
 * Gestion des parametres de cout de revient (XOF -&gt; USD -&gt; CNY),
 * reservee aux administrateurs.
 *
 * <p>Perimetre volontairement limite : publier la configuration du jour
 * (et son {@code breakEvenRate} calcule), consulter la configuration la
 * plus recente et l'historique complet. {@code breakEvenRate} et les
 * parametres qui le composent sont des donnees internes confidentielles
 * — cette API n'a aucun equivalent expose au client (voir
 * {@link com.converter.quote.web.QuoteController}, dont les reponses ne
 * portent jamais ces champs).
 */
@RestController
@RequestMapping("/api/admin/cost-rates")
@SecurityRequirement(name = "bearer-jwt")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Administration - Cout de revient", description = "Cout de revient XOF -> USD -> CNY (breakEvenRate)")
public class AdminCostRateController {

    private final CostRateAdminService costRateAdminService;

    public AdminCostRateController(CostRateAdminService costRateAdminService) {
        this.costRateAdminService = costRateAdminService;
    }

    @PostMapping
    @Operation(summary = "Publier la configuration de cout du jour",
            description = "Calcule et historise le breakEvenRate (XOF/CNY) pour le montant de reference donne. "
                    + "N'affecte pas le RateSource (ecran distinct 'Taux preferentiel'), mais EST directement "
                    + "consomme par la creation de tout nouveau Quote depuis la Phase 3.1 : publier une valeur "
                    + "erronee affecte immediatement le customerRate propose aux clients suivants.")
    public ResponseEntity<ApiResponse<CostRateConfigurationResponse>> publish(
            @Valid @RequestBody PublishCostRateConfigurationRequest request,
            @AuthenticatedUser CurrentUser actor) {
        CostRateConfigurationResponse result = costRateAdminService.publish(request, actor.getId());
        return ResponseEntity.ok(ApiResponse.of(result, "Configuration de cout publiee."));
    }

    @GetMapping("/current")
    @Operation(summary = "Derniere configuration de cout publiee")
    public ResponseEntity<ApiResponse<CostRateConfigurationResponse>> current() {
        return ResponseEntity.ok(ApiResponse.of(costRateAdminService.current()));
    }

    @GetMapping
    @Operation(summary = "Historique des configurations de cout publiees")
    public ResponseEntity<ApiResponse<PageResponse<CostRateConfigurationResponse>>> history(
            @Parameter(hidden = true) @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.of(PageResponse.from(costRateAdminService.history(pageable))));
    }
}
