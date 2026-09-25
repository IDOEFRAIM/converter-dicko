package com.converter.transfi.domain;

/**
 * Statut interne d'un {@link TransfiOrder} — vocabulaire STABLE de notre cote, traduit depuis le
 * statut brut renvoye par TransFi (voir {@code TransfiWebhookController}, qui ne fait jamais
 * transiter le vocabulaire proprietaire de TransFi au-dela de la couche de traduction).
 */
public enum TransfiOrderStatus {
    /** Cree chez TransFi, aucune confirmation recue. */
    CREATED,
    /** Statut intermediaire explicite (ex. en cours de conversion) — jamais suppose "en cours" par defaut. */
    PENDING,
    SUCCESS,
    FAILED
}
