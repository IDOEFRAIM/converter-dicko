package com.converter.config.props;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Amorcage du compte administrateur initial.
 *
 * <p>Aucun mot de passe n'est ecrit dans le code ni dans les fichiers
 * versionnes. Deux comportements selon le profil :
 * <ul>
 *   <li>{@code dev} — si {@code password} est vide, un mot de passe
 *       aleatoire est genere et affiche une seule fois au demarrage ;</li>
 *   <li>{@code prod} — un mot de passe genere est refuse : le seed
 *       exige {@code ADMIN_PASSWORD}, sinon le demarrage echoue.</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "app.admin-seed")
public record AdminSeedProperties(
        boolean enabled,
        String phone,
        String password,
        String firstName,
        String lastName
) {
    public boolean hasExplicitPassword() {
        return password != null && !password.isBlank();
    }
}
