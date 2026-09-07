package com.converter.user.dto;

import com.converter.user.domain.ExperienceProfile;
import com.converter.user.domain.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Representation publique d'un compte.
 *
 * <p>Ce type ne comporte deliberement aucun champ lie au mot de passe.
 * L'absence est structurelle, pas conditionnelle : il n'existe aucun
 * chemin de code capable de serialiser un condensat.
 */
@Schema(description = "Compte utilisateur")
public record UserResponse(

        @Schema(description = "Identifiant public du compte")
        UUID id,

        @Schema(description = "Numero au format E.164", example = "+2250700000000")
        String phone,

        String firstName,

        String lastName,

        String email,

        @Schema(description = "ACTIVE ou BLOCKED")
        UserStatus status,

        @Schema(description = "Roles applicatifs", example = "[\"USER\"]")
        Set<String> roles,

        Instant createdAt,

        @Schema(description = "Derniere connexion reussie, null si jamais connecte")
        Instant lastLoginAt,

        @Schema(description = "Habillage mobile choisi par le client — pilote uniquement l'interface, jamais le pricing")
        ExperienceProfile experienceProfile
) {
}
