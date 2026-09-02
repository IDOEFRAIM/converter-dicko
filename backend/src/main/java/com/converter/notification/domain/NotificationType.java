package com.converter.notification.domain;

/** Catalogue ferme des types de notification interne (section 6 de la specification). */
public enum NotificationType {
    QUOTE_CREATED,
    PAYMENT_SUBMITTED,
    PAYMENT_CONFIRMED,
    EXCHANGE_STARTED,
    EXCHANGE_PROGRESS,
    EXCHANGE_COMPLETED,
    PREFERRED_RATE_REACHED,
    PREFERRED_RATE_EXPIRED,
    EXCHANGE_CANCELLED,
    /** L'ordre a expire faute de paiement dans le delai imparti ; sa reservation a ete liberee (passe 2, P2-1). */
    ORDER_EXPIRED
}
