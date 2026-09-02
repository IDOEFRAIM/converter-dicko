package com.converter.treasury.domain;

/**
 * Sens d'un mouvement de tresorerie.
 *
 * <p>Vocabulaire {@code Available / Reserved / Consumed / Released}
 * (docs/ARCHITECTURE.md, Partie I, section I) mappe sur ces types sans
 * necessiter de colonne ou de type supplementaire :
 * <pre>
 * Available = balance - reserved_balance
 * Reserved  = mouvement RESERVATION (immobilise, ne change pas balance)
 * Consumed  = mouvement WITHDRAWAL avec order_id renseigne (decaissement reel)
 * Released  = mouvement RELEASE (libere une reservation sans decaisser)
 * </pre>
 */
public enum TreasuryTransactionType {

    /** Alimentation manuelle, ou encaissement XOF confirme par un Payment. */
    DEPOSIT,

    /** Decaissement reel (Consumed) — toujours lie a un {@code order_id}. */
    WITHDRAWAL,

    /** Immobilisation de liquidite a la creation d'un Order (Reserved). */
    RESERVATION,

    /** Liberation d'une reservation — annulation, expiration, rejet (Released). */
    RELEASE,

    /** Correction comptable administrative, motif obligatoire. */
    ADJUSTMENT
}
