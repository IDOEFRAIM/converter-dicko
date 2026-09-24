package com.converter.rate.alert.web;

import com.converter.common.api.ApiResponse;
import com.converter.common.api.PageResponse;
import com.converter.rate.alert.domain.RateAlertStatus;
import com.converter.rate.alert.dto.CreateRateAlertRequest;
import com.converter.rate.alert.dto.RateAlertResponse;
import com.converter.rate.alert.service.RateAlertService;
import com.converter.security.AuthenticatedUser;
import com.converter.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Alertes de taux : "previens-moi quand le taux client public atteint mon objectif". Le
 * declenchement est exclusivement pilote par {@code RateAlertScheduler} — cette API n'expose que
 * creation, consultation et annulation, jamais un declenchement manuel.
 */
@RestController
@RequestMapping("/api/v1/rate-alerts")
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Alertes de taux", description = "Notification lorsque le taux client public atteint un objectif")
public class RateAlertController {

    private final RateAlertService rateAlertService;

    public RateAlertController(RateAlertService rateAlertService) {
        this.rateAlertService = rateAlertService;
    }

    @PostMapping
    @Operation(summary = "Creer une alerte de taux",
            description = "Aucune transaction financiere : ni Quote, ni Order, ni reservation Wallet/Treasury.")
    public ResponseEntity<ApiResponse<RateAlertResponse>> create(
            @Valid @RequestBody CreateRateAlertRequest request,
            @AuthenticatedUser CurrentUser currentUser) {
        RateAlertResponse response = rateAlertService.create(request, currentUser.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response, "Alerte de taux creee."));
    }

    @GetMapping
    @Operation(summary = "Lister mes alertes de taux", description = "Filtre optionnel par statut (?status=ACTIVE).")
    public ResponseEntity<ApiResponse<PageResponse<RateAlertResponse>>> list(
            @RequestParam(required = false) RateAlertStatus status,
            @AuthenticatedUser CurrentUser currentUser,
            @Parameter(hidden = true) @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.of(rateAlertService.listMine(currentUser.getId(), status, pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detail d'une alerte de taux")
    public ResponseEntity<ApiResponse<RateAlertResponse>> get(
            @PathVariable UUID id,
            @AuthenticatedUser CurrentUser currentUser) {
        return ResponseEntity.ok(ApiResponse.of(rateAlertService.get(id, currentUser.getId())));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Annuler une alerte active", description = "Transition metier ACTIVE -> CANCELLED, jamais une suppression physique.")
    public ResponseEntity<ApiResponse<RateAlertResponse>> cancel(
            @PathVariable UUID id,
            @AuthenticatedUser CurrentUser currentUser) {
        return ResponseEntity.ok(ApiResponse.of(rateAlertService.cancel(id, currentUser.getId()), "Alerte annulee."));
    }
}
