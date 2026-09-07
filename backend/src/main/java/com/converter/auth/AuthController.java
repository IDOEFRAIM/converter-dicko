package com.converter.auth;

import com.converter.auth.dto.AuthResponse;
import com.converter.auth.dto.LoginRequest;
import com.converter.auth.dto.RegisterRequest;
import com.converter.auth.dto.UpdateExperienceProfileRequest;
import com.converter.common.api.ApiResponse;
import com.converter.security.AuthenticatedUser;
import com.converter.security.CurrentUser;
import com.converter.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentification", description = "Inscription, connexion et profil courant")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @Operation(summary = "Creer un compte client",
            description = "Cree un compte avec le role USER. Le mot de passe est hache "
                    + "avec BCrypt avant persistance et n'est jamais renvoye.")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(response, "Compte cree avec succes."));
    }

    @PostMapping("/login")
    @Operation(summary = "Se connecter",
            description = "Verifie le mot de passe, le statut du compte, puis emet un jeton JWT "
                    + "valide 2 heures. Proteges par un limiteur de tentatives par adresse IP.")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.of(response, "Connexion reussie."));
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "Profil du compte connecte")
    public ResponseEntity<ApiResponse<UserResponse>> me(@AuthenticatedUser CurrentUser currentUser) {
        UserResponse response = authService.me(currentUser.getId());
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    @PatchMapping("/me/experience-profile")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "Changer mon habillage mobile (PRO / STUDENT_MALE / STUDENT_FEMALE)",
            description = "Purement cosmetique : ne modifie jamais le taux, les frais ni aucune regle metier. "
                    + "Auto-selectionnable, aucun controle administrateur.")
    public ResponseEntity<ApiResponse<UserResponse>> updateExperienceProfile(
            @Valid @RequestBody UpdateExperienceProfileRequest request,
            @AuthenticatedUser CurrentUser currentUser) {
        UserResponse response = authService.updateExperienceProfile(currentUser.getId(), request.experienceProfile());
        return ResponseEntity.ok(ApiResponse.of(response, "Habillage mis a jour."));
    }
}
