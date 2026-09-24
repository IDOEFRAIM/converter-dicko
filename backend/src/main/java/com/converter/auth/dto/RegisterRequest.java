package com.converter.auth.dto;

import com.converter.common.validation.PhoneNumber;
import com.converter.user.domain.ExperienceProfile;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Demande d'inscription.
 *
 * <p>Le mot de passe n'a volontairement pas de contrainte de "complexite"
 * (majuscule/chiffre/symbole obligatoires) : la recherche en securite
 * (NIST SP 800-63B) montre que ces regles poussent vers des mots de
 * passe predictibles ("Password1!") plutot que robustes. Seule une
 * longueur minimale est imposee ; c'est BCrypt, pas la politique de
 * saisie, qui porte la resistance a l'attaque hors ligne.
 */
@Schema(description = "Demande de creation de compte")
public record RegisterRequest(

        @NotBlank(message = "Le numero de telephone est obligatoire")
        @PhoneNumber
        @Schema(description = "Numero au format international", example = "+2250700000000")
        String phone,

        @NotBlank(message = "Le mot de passe est obligatoire")
        @Size(min = 8, max = 72, message = "Le mot de passe doit contenir entre 8 et 72 caracteres")
        @Schema(description = "Mot de passe, au moins 8 caracteres")
        String password,

        @NotBlank(message = "Le prenom est obligatoire")
        @Size(max = 80)
        String firstName,

        @NotBlank(message = "Le nom est obligatoire")
        @Size(max = 80)
        String lastName,

        @Schema(description = "Habillage mobile choisi a l'inscription — PRO par defaut si omis",
                example = "STUDENT_MALE")
        ExperienceProfile experienceProfile
) {
}
