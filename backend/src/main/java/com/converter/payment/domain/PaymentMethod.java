package com.converter.payment.domain;

/**
 * Moyens de paiement CFA reserves dans l'abstraction.
 *
 * <p>Seul {@code MOBILE_MONEY} est actif (voir {@code system_settings.ENABLED_PAYMENT_METHODS}).
 * {@code WAVE} et {@code BANK_TRANSFER} sont des noms reserves : aucune
 * integration reelle n'existe, une soumission avec ces methodes est
 * rejetee tant qu'elles ne figurent pas dans la liste des methodes
 * activees.
 */
public enum PaymentMethod {
    MOBILE_MONEY,
    WAVE,
    BANK_TRANSFER
}
