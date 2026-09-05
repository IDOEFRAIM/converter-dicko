package com.converter.config.props;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parametres du garde-fou anti abus/flooding sur les endpoints d'ecriture
 * les plus exposes : inscription, creation de devis, creation d'ordre,
 * soumission de paiement. Complement de {@link LoginProtectionProperties}
 * (dediee a {@code /api/auth/login}), voir {@code RateLimitFilter}.
 *
 * @param registerMaxAttempts   nombre de requetes tolerees sur {@code POST /api/auth/register} par fenetre
 * @param registerWindowMinutes duree de la fenetre pour l'inscription
 * @param writeMaxAttempts      nombre de requetes tolerees sur les endpoints d'ecriture authentifies par fenetre
 * @param writeWindowMinutes    duree de la fenetre pour ces endpoints
 */
@ConfigurationProperties(prefix = "app.abuse-protection")
public record AbuseProtectionProperties(
        int registerMaxAttempts, int registerWindowMinutes,
        int writeMaxAttempts, int writeWindowMinutes) {

    public AbuseProtectionProperties {
        if (registerMaxAttempts <= 0) {
            registerMaxAttempts = 5;
        }
        if (registerWindowMinutes <= 0) {
            registerWindowMinutes = 15;
        }
        if (writeMaxAttempts <= 0) {
            writeMaxAttempts = 60;
        }
        if (writeWindowMinutes <= 0) {
            writeWindowMinutes = 5;
        }
    }
}
