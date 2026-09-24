package com.converter.settings.web;

import com.converter.common.api.ApiResponse;
import com.converter.settings.domain.SettingKey;
import com.converter.settings.dto.SettingResponse;
import com.converter.settings.dto.UpdateSettingRequest;
import com.converter.settings.service.SettingsService;
import com.converter.security.AuthenticatedUser;
import com.converter.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Consultation et modification des parametres metier.
 *
 * <p>Une modification prend effet immediatement (le cache de
 * {@link SettingsService} est invalide a l'ecriture) et ne touche
 * jamais les ordres deja crees : chaque ordre conserve son propre
 * instantane du taux et des plafonds en vigueur au moment de sa
 * creation (module {@code order}, livre en Phase 4).
 */
@RestController
@RequestMapping("/api/admin/settings")
@SecurityRequirement(name = "bearer-jwt")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Administration - Parametres", description = "Bornes de montants, delais et options metier")
public class AdminSettingsController {

    private final SettingsService settingsService;

    public AdminSettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    @Operation(summary = "Lister tous les parametres metier")
    public ResponseEntity<ApiResponse<List<SettingResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.of(settingsService.findAll()));
    }

    @PutMapping("/{key}")
    @Operation(summary = "Modifier un parametre",
            description = "La valeur est validee selon le type declare du parametre "
                    + "(STRING, INTEGER, DECIMAL, BOOLEAN) avant ecriture.")
    public ResponseEntity<ApiResponse<SettingResponse>> update(
            @PathVariable SettingKey key,
            @Valid @RequestBody UpdateSettingRequest request,
            @AuthenticatedUser CurrentUser actor) {
        SettingResponse result = settingsService.update(key, request.value(), actor.getId());
        return ResponseEntity.ok(ApiResponse.of(result, "Parametre mis a jour."));
    }
}
