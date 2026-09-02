package com.converter.preferredrate.web;

import com.converter.common.api.ApiResponse;
import com.converter.common.api.PageResponse;
import com.converter.preferredrate.dto.CreatePreferredRateRequest;
import com.converter.preferredrate.dto.PreferredRateRequestResponse;
import com.converter.preferredrate.service.PreferredRateService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Demandes de taux preferentiel. Le declenchement automatique est
 * exclusivement pilote par {@code PreferredRateScheduler} -- cette API
 * n'expose que creation, consultation et annulation.
 */
@RestController
@RequestMapping("/api/v1/preferred-rates")
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Taux preferentiel", description = "Echange automatique lorsque le taux cible est atteint")
public class PreferredRateController {

    private final PreferredRateService preferredRateService;

    public PreferredRateController(PreferredRateService preferredRateService) {
        this.preferredRateService = preferredRateService;
    }

    @PostMapping
    @Operation(summary = "Creer une demande de taux preferentiel",
            description = "Immobilise immediatement le montant sur le Wallet du client. Duree de validite : 3 jours.")
    public ResponseEntity<ApiResponse<PreferredRateRequestResponse>> create(
            @Valid @RequestBody CreatePreferredRateRequest request,
            @AuthenticatedUser CurrentUser currentUser) {
        PreferredRateRequestResponse response = preferredRateService.create(request, currentUser.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response, "Demande de taux preferentiel creee."));
    }

    @GetMapping
    @Operation(summary = "Lister mes demandes de taux preferentiel")
    public ResponseEntity<ApiResponse<PageResponse<PreferredRateRequestResponse>>> list(
            @AuthenticatedUser CurrentUser currentUser,
            @Parameter(hidden = true) @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.of(preferredRateService.listMine(currentUser.getId(), pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detail d'une demande de taux preferentiel")
    public ResponseEntity<ApiResponse<PreferredRateRequestResponse>> get(
            @PathVariable UUID id,
            @AuthenticatedUser CurrentUser currentUser) {
        return ResponseEntity.ok(ApiResponse.of(preferredRateService.get(id, currentUser.getId())));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Annuler une demande active", description = "Libere immediatement le montant reserve sur le Wallet.")
    public ResponseEntity<ApiResponse<PreferredRateRequestResponse>> cancel(
            @PathVariable UUID id,
            @AuthenticatedUser CurrentUser currentUser) {
        return ResponseEntity.ok(ApiResponse.of(preferredRateService.cancel(id, currentUser.getId()), "Demande annulee."));
    }
}
