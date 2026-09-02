package com.converter.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Motif de blocage d'un compte.
 *
 * <p>Le motif est obligatoire : bloquer un client est une decision
 * opposable, elle doit rester justifiee dans le journal d'audit.
 */
@Schema(description = "Demande de blocage d'un compte")
public record BlockUserRequest(

        @NotBlank(message = "Le motif du blocage est obligatoire")
        @Size(max = 500, message = "Le motif ne peut depasser 500 caracteres")
        @Schema(description = "Motif du blocage", example = "Documents de verification non conformes")
        String reason
) {
}
