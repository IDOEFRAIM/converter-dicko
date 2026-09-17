package com.converter.auth;

import com.converter.auth.dto.AuthResponse;
import com.converter.auth.dto.CompleteGoogleSignUpRequest;
import com.converter.auth.dto.DeleteAccountRequest;
import com.converter.auth.dto.GoogleSignInRequest;
import com.converter.auth.dto.GoogleSignInResponse;
import com.converter.auth.dto.LoginRequest;
import com.converter.auth.dto.RegisterRequest;
import com.converter.auth.dto.UpdateExperienceProfileRequest;
import com.converter.common.api.ApiResponse;
import com.converter.security.AuthenticatedUser;
import com.converter.security.CurrentUser;
import com.converter.user.dto.UserResponse;
import com.converter.user.service.AccountDeletionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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
    private final AccountDeletionService accountDeletionService;

    public AuthController(AuthService authService, AccountDeletionService accountDeletionService) {
        this.authService = authService;
        this.accountDeletionService = accountDeletionService;
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

    @PostMapping("/google")
    @Operation(summary = "Se connecter avec Google",
            description = "Verifie le jeton d'identite Google transmis par le SDK mobile. "
                    + "Si aucun compte n'y est deja associe (accountExists=false), le mobile doit "
                    + "collecter le numero de telephone puis appeler /auth/google/complete avec le "
                    + "MEME jeton -- Google ne transmet jamais de numero de telephone.")
    public ResponseEntity<ApiResponse<GoogleSignInResponse>> google(@Valid @RequestBody GoogleSignInRequest request) {
        GoogleSignInResponse response = authService.googleSignIn(request);
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    @PostMapping("/google/complete")
    @Operation(summary = "Finaliser un compte cree via Google",
            description = "A appeler uniquement apres un /auth/google ayant renvoye accountExists=false. "
                    + "Cree le compte (sans mot de passe) avec le numero de telephone fourni.")
    public ResponseEntity<ApiResponse<AuthResponse>> completeGoogleSignUp(
            @Valid @RequestBody CompleteGoogleSignUpRequest request) {
        AuthResponse response = authService.completeGoogleSignUp(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(response, "Compte cree avec succes."));
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

    @DeleteMapping("/me")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "Supprimer mon compte",
            description = "Anonymise le compte (nom, telephone, email, mot de passe/Google) -- "
                    + "les transferts deja effectues restent en base pour les obligations legales "
                    + "de conservation. Refuse si un transfert est encore en cours, ou pour un "
                    + "compte administrateur. currentPassword obligatoire sauf pour un compte "
                    + "cree via Google (qui n'en a jamais).")
    public ResponseEntity<ApiResponse<Void>> deleteMyAccount(
            @RequestBody(required = false) DeleteAccountRequest request,
            @AuthenticatedUser CurrentUser currentUser) {
        String currentPassword = request == null ? null : request.currentPassword();
        accountDeletionService.deleteOwnAccount(currentUser.getId(), currentPassword);
        return ResponseEntity.ok(ApiResponse.message("Compte supprime."));
    }
}
