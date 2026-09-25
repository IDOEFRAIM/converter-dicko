package com.converter.config.props;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Integration TransFi BizPay (voir {@code docs/TRANSFI_INTEGRATION.md}) — payin/payout
 * automatises, en remplacement progressif du flux manuel (declaration + verification par un
 * administrateur), qui reste le chemin par defaut tant que cette integration n'est pas active.
 *
 * <p>Optionnelle, comme {@link PushProperties}/{@link GoogleAuthProperties} : sans identifiants,
 * {@link #configured()} vaut {@code false} et toute tentative d'appeler TransFi echoue proprement
 * (503 {@code TRANSFI_UNAVAILABLE}) plutot que d'empecher le demarrage du service. {@code enabled}
 * est une bascule SEPAREE de {@code configured()} : meme des identifiants sandbox valides
 * renseignes, l'integration reste inactive tant que {@code enabled=false} — l'activation est donc
 * TOUJOURS un choix explicite, jamais un effet de bord de l'ajout des cles en environnement.
 *
 * <p>{@code webhookSecret} signe les notifications de statut recues de TransFi (voir
 * {@code TransfiWebhookController}) — distinct de {@code clientSecret}, qui authentifie NOS
 * appels sortants. Les deux sont des secrets serveur uniquement, jamais exposes au frontend ni
 * loggues (voir la Javadoc de {@code TransFiHttpClient}).
 *
 * <p><b>Chemins d'API provisoires</b> ({@code /v3/orders}, voir {@code TransFiHttpClient}) : a
 * confirmer contre la documentation officielle TransFi avant toute activation en production —
 * ce fichier ne fige rien de definitif sur le contrat reseau, seulement la configuration.
 */
@ConfigurationProperties(prefix = "app.transfi")
public record TransFiProperties(boolean enabled, String baseUrl, String clientId, String clientSecret,
                                 String webhookSecret) {

    public boolean configured() {
        return baseUrl != null && !baseUrl.isBlank()
                && clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank();
    }

    /** Vrai seulement si la bascule est active ET que les identifiants sont presents. */
    public boolean active() {
        return enabled && configured();
    }

    public boolean webhookConfigured() {
        return webhookSecret != null && !webhookSecret.isBlank();
    }
}
