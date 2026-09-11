package com.converter.auth.dto;

import com.converter.common.validation.PhoneNumber;
import com.converter.user.domain.ExperienceProfile;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Finalise un compte apres un premier {@code POST /api/auth/google} sans
 * correspondance ({@code accountExists = false}). Reenvoie le MEME jeton
 * Google (revérifié ici — jamais fait confiance a une identite non
 * reverifiee) plutot qu'un jeton intermediaire maison : plus simple, pas de
 * nouveau format de jeton a emettre/valider pour une fenetre de quelques
 * minutes.
 */
@Schema(description = "Finalisation d'un compte cree via Google")
public record CompleteGoogleSignUpRequest(

        @NotBlank(message = "Le jeton Google est obligatoire")
        String idToken,

        @NotBlank(message = "Le numero de telephone est obligatoire")
        @PhoneNumber
        @Schema(description = "Numero au format international", example = "+2250700000000")
        String phone,

        @NotBlank(message = "Le prenom est obligatoire")
        @Size(max = 80)
        String firstName,

        @NotBlank(message = "Le nom est obligatoire")
        @Size(max = 80)
        String lastName,

        @Schema(description = "Habillage mobile choisi a l'inscription — PRO par defaut si omis")
        ExperienceProfile experienceProfile
) {
}
