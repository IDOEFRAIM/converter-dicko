package com.converter.order.domain;

/**
 * Statuts deja definis par l'architecture (docs/ARCHITECTURE.md,
 * Partie II, section E ; Partie I, section N.2) — repris a l'identique,
 * aucune valeur ajoutee ni retiree.
 *
 * <pre>
 *              creation
 *                 |
 *                 v
 *         AWAITING_PAYMENT ----+----&gt; CANCELLED
 *                 |            |
 *                 |            +----&gt; EXPIRED (non declenche automatiquement dans cette phase)
 *                 v
 *         PAYMENT_SUBMITTED ---+----&gt; REJECTED
 *                 |
 *                 v
 *         PAYMENT_VERIFIED
 *                 |
 *                 v
 *            PROCESSING            (creation du Settlement)
 *                 |
 *                 v
 *            COMPLETED             (execution du Settlement)
 * </pre>
 */
public enum OrderStatus {
    AWAITING_PAYMENT,
    PAYMENT_SUBMITTED,
    PAYMENT_VERIFIED,
    PROCESSING,
    COMPLETED,
    CANCELLED,
    REJECTED,
    EXPIRED
}
