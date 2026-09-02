package com.converter.quote.domain;

/**
 * Intention du client au moment de la simulation.
 *
 * <p>Le client fournit toujours l'un des deux montants, jamais les
 * deux, et ne fournit jamais lui-meme le taux, la marge ou les frais —
 * ces valeurs sont exclusivement calculees et controlees par le
 * backend ({@link com.converter.rate.engine.RateEngine}).
 */
public enum QuoteDirection {

    /** Montant XOF connu (ce que le client paie) → CNY calcule. */
    SEND_XOF,

    /** Montant CNY souhaite (ce que le beneficiaire recoit) → XOF calcule. */
    RECEIVE_CNY
}
