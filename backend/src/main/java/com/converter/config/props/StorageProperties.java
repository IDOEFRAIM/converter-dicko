package com.converter.config.props;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Racine du stockage local des fichiers (preuves de paiement et de
 * reglement).
 *
 * @param rootDir repertoire racine, cree au demarrage s'il n'existe pas.
 *                Hors racine web par construction (jamais servi
 *                statiquement) : tout acces passe par un endpoint
 *                authentifie qui verifie la propriete de la ressource.
 */
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(String rootDir) {

    public StorageProperties {
        if (rootDir == null || rootDir.isBlank()) {
            rootDir = "./storage";
        }
    }
}
