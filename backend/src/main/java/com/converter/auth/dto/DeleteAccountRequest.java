package com.converter.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Suppression du compte connecte")
public record DeleteAccountRequest(

        @Schema(description = "Obligatoire pour un compte phone+mot de passe (re-confirmation "
                + "de possession) ; omis/ignore pour un compte cree via Google, qui n'en a jamais.")
        String currentPassword
) {
}
