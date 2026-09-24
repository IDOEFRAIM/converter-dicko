package com.converter.auth.dto;

import com.converter.common.validation.PhoneNumber;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Demande de connexion")
public record LoginRequest(

        @NotBlank(message = "Le numero de telephone est obligatoire")
        @PhoneNumber
        String phone,

        @NotBlank(message = "Le mot de passe est obligatoire")
        String password
) {
}
