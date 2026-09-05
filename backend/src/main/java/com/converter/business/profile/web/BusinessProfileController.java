package com.converter.business.profile.web;

import com.converter.business.profile.dto.BusinessProfileResponse;
import com.converter.business.profile.dto.UpsertBusinessProfileRequest;
import com.converter.business.profile.service.BusinessProfileService;
import com.converter.common.api.ApiResponse;
import com.converter.security.AuthenticatedUser;
import com.converter.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Profil professionnel de l'utilisateur authentifie — ressource singleton par utilisateur,
 * jamais adressee par identifiant dans l'URL. Absence de profil : comportement {@code PERSONAL}
 * (voir {@code BusinessProfileService#isBusinessUser}), jamais un {@code accountType} sur
 * {@code User}.
 */
@RestController
@RequestMapping("/api/v1/business-profile")
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Profil professionnel", description = "Identite Business de l'utilisateur authentifie")
public class BusinessProfileController {

    private final BusinessProfileService businessProfileService;

    public BusinessProfileController(BusinessProfileService businessProfileService) {
        this.businessProfileService = businessProfileService;
    }

    @GetMapping
    @Operation(summary = "Mon profil professionnel",
            description = "404 BUSINESS_PROFILE_NOT_FOUND si aucun profil n'existe -- l'utilisateur est alors PERSONAL.")
    public ResponseEntity<ApiResponse<BusinessProfileResponse>> get(@AuthenticatedUser CurrentUser currentUser) {
        return ResponseEntity.ok(ApiResponse.of(businessProfileService.get(currentUser.getId())));
    }

    @PutMapping
    @Operation(summary = "Creer ou mettre a jour mon profil professionnel",
            description = "Idempotent : cree le profil s'il n'existe pas encore, le met a jour sinon. "
                    + "Le proprietaire est toujours l'utilisateur authentifie, jamais un champ du corps.")
    public ResponseEntity<ApiResponse<BusinessProfileResponse>> upsert(
            @Valid @RequestBody UpsertBusinessProfileRequest request,
            @AuthenticatedUser CurrentUser currentUser) {
        BusinessProfileService.UpsertResult result = businessProfileService.upsert(request, currentUser.getId());
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        String message = result.created() ? "Profil professionnel cree." : "Profil professionnel mis a jour.";
        return ResponseEntity.status(status).body(ApiResponse.of(result.response(), message));
    }
}
