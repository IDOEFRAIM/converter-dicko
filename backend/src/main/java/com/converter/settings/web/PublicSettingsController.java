package com.converter.settings.web;

import com.converter.common.api.ApiResponse;
import com.converter.settings.dto.PublicSettingsResponse;
import com.converter.settings.service.SettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Parametres metier accessibles sans authentification.
 *
 * <p>Uniquement les cles marquees {@code is_public = true} (migration
 * V4) sont exposees ici. Elles permettent au frontend d'afficher des
 * bornes ou une duree de verrouillage avant meme la connexion — un
 * confort d'affichage, jamais un controle : chaque regle est
 * revalidee cote serveur au moment de l'action reelle.
 */
@RestController
@RequestMapping("/api/settings")
@Tag(name = "Parametres publics", description = "Bornes et options visibles sans authentification")
public class PublicSettingsController {

    private final SettingsService settingsService;

    public PublicSettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping("/public")
    @Operation(summary = "Parametres metier publics")
    public ResponseEntity<ApiResponse<PublicSettingsResponse>> getPublicSettings() {
        return ResponseEntity.ok(ApiResponse.of(settingsService.findPublic()));
    }
}
