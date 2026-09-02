package com.converter.config.props;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parametres du garde-fou anti force brute sur {@code /api/auth/login}.
 *
 * @param maxAttempts         nombre d'echecs tolere avant verrouillage
 * @param lockDurationMinutes duree du verrouillage
 */
@ConfigurationProperties(prefix = "app.login-protection")
public record LoginProtectionProperties(int maxAttempts, int lockDurationMinutes) {

    public LoginProtectionProperties {
        if (maxAttempts <= 0) {
            maxAttempts = 5;
        }
        if (lockDurationMinutes <= 0) {
            lockDurationMinutes = 15;
        }
    }
}
