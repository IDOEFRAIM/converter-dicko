package com.converter.auth.google;

/**
 * Identite extraite d'un jeton Google deja verifie — jamais construite
 * autrement qu'via {@link GoogleTokenVerifierService#verify}, donc jamais
 * a partir d'une donnee non authentifiee.
 */
public record GoogleIdentity(
        String subject,
        String email,
        boolean emailVerified,
        String givenName,
        String familyName
) {
}
