package com.converter.auth.dto;

import com.converter.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.media.Schema;

/** Reponse d'inscription ou de connexion reussie. */
@Schema(description = "Jeton d'acces et profil du compte connecte")
public record AuthResponse(

        @Schema(description = "Jeton JWT a transmettre dans l'en-tete Authorization")
        String accessToken,

        @Schema(description = "Type du jeton", example = "Bearer")
        String tokenType,

        @Schema(description = "Duree de validite du jeton, en secondes", example = "7200")
        long expiresInSeconds,

        UserResponse user
) {
    public static AuthResponse of(String token, long expiresInSeconds, UserResponse user) {
        return new AuthResponse(token, "Bearer", expiresInSeconds, user);
    }
}
