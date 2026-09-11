package com.converter.config.props;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Client ID OAuth 2.0 Google attendu comme audience des jetons d'identite
 * envoyes par l'app mobile (connexion "Continuer avec Google").
 *
 * <p>Contrairement a {@link JwtProperties#secret()}, {@code clientId} n'est
 * PAS {@code @NotBlank} : la connexion Google est une fonctionnalite
 * optionnelle, pas un pilier de securite exploite a chaque requete comme le
 * secret JWT. Tant qu'aucun {@code GOOGLE_OAUTH_CLIENT_ID} n'est fourni,
 * {@link #configured()} vaut {@code false} et {@code GoogleTokenVerifierService}
 * refuse proprement (503) plutot que de faire echouer tout le demarrage de
 * l'application pour une fonctionnalite que ce deploiement n'utilise peut-etre
 * pas encore.
 */
@ConfigurationProperties(prefix = "app.google")
public record GoogleAuthProperties(String clientId) {

    public boolean configured() {
        return clientId != null && !clientId.isBlank();
    }
}
