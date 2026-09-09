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
    ORDER_EXPIRED,
    /** Le taux client public a atteint l'objectif d'une {@code RateAlert} (Phase 6). */
    RATE_ALERT_TRIGGERED,
    /** L'objectif collectif d'une Ruee (mission "differenciation marketing", Lot 3) a ete atteint. */
    POOL_SUCCEEDED,
    /** Une Ruee a expire (J+timer) sans atteindre son objectif. */
    POOL_EXPIRED,
    /** Un profil STUDENT_MALE/STUDENT_FEMALE vient de franchir un nouveau palier de badge
     * (mission "differenciation marketing" : celebrer le moment, jamais seulement l'afficher au
     * prochain chargement de "Mes gains") — jamais declenche pour PRO (aucun badge). */
    BADGE_UNLOCKED
}
