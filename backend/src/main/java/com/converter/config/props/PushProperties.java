package com.converter.config.props;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cle de signature VAPID (RFC 8292) pour les notifications Web Push (PWA, mission "blocages
 * Apple/Meta" oct. 2026). Comme {@link GoogleAuthProperties#clientId()}, cette fonctionnalite est
 * OPTIONNELLE, pas un pilier de securite : tant qu'aucune paire de cles n'est fournie,
 * {@link #configured()} vaut {@code false} et les endpoints push refusent proprement (503)
 * plutot que de faire echouer le demarrage de toute l'application.
 *
 * <p>{@code publicKey} est exposee telle quelle au frontend (voir {@code PushController}) : par
 * construction, une cle publique VAPID n'est pas un secret. Seule {@code privateKey} l'est
 * (signe les envois au nom de ce serveur) -- jamais de valeur par defaut en profil {@code prod}.
 */
@ConfigurationProperties(prefix = "app.push")
public record PushProperties(String publicKey, String privateKey, String subject) {

    public boolean configured() {
        return publicKey != null && !publicKey.isBlank()
                && privateKey != null && !privateKey.isBlank();
    }
}
