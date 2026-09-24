package com.converter.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Jeton d'identite Google (obtenu par le SDK Google Sign-In cote mobile)")
public record GoogleSignInRequest(

        @NotBlank(message = "Le jeton Google est obligatoire")
        String idToken
) {
}
