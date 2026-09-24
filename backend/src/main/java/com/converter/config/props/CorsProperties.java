package com.converter.config.props;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Origines autorisees pour les requetes navigateur.
 *
 * <p>Jamais de joker : la configuration CORS de l'application active
 * {@code allowCredentials}, et la specification interdit de combiner
 * {@code allowCredentials} avec {@code Access-Control-Allow-Origin: *}.
 * Les origines sont donc listees explicitement par environnement.
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }
}
