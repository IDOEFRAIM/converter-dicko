package com.converter.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Deux issues possibles pour {@code POST /api/auth/google}, jamais melangees :
 *
 * <ul>
 *   <li>{@code accountExists = true} : un compte est deja associe a ce compte
 *       Google (connexion) — {@link #auth()} porte le jeton d'acces, le reste
 *       des champs est {@code null}.</li>
 *   <li>{@code accountExists = false} : aucun compte associe (premiere
 *       connexion Google) — {@link #auth()} est {@code null} ; {@link #email()}/
 *       {@link #suggestedFirstName()}/{@link #suggestedLastName()} pre-remplissent
 *       l'ecran "votre numero" cote mobile, qui appellera ensuite
 *       {@code POST /api/auth/google/complete} avec le MEME jeton Google et le
 *       numero de telephone (obligatoire, jamais fourni par Google).</li>
 * </ul>
 */
@Schema(description = "Resultat d'une tentative de connexion Google")
public record GoogleSignInResponse(
        boolean accountExists,
        AuthResponse auth,
        String email,
        String suggestedFirstName,
        String suggestedLastName
) {
    public static GoogleSignInResponse existingAccount(AuthResponse auth) {
        return new GoogleSignInResponse(true, auth, null, null, null);
    }

    public static GoogleSignInResponse newAccount(String email, String suggestedFirstName, String suggestedLastName) {
        return new GoogleSignInResponse(false, null, email, suggestedFirstName, suggestedLastName);
    }
}
