package com.converter.payment.domain;

/**
 * Cycle de vie d'un paiement — MVP entierement manuel.
 *
 * <pre>
 * SUBMITTED --+--&gt; CONFIRMED  (terminal — declenche Order -> PAYMENT_VERIFIED)
 *             `--&gt; REJECTED   (terminal — declenche Order -> REJECTED, terminal)
 * </pre>
 *
 * Un ordre porte au plus un paiement (voir migration V10) : un rejet
 * termine l'ordre, il n'y a pas de resoumission.
 */
public enum PaymentStatus {
    SUBMITTED,
    CONFIRMED,
    REJECTED
}
