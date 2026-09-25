package com.converter.ledger.domain;

/**
 * Nature d'un mouvement du grand livre interne (voir {@code docs/TRANSFI_INTEGRATION.md}).
 *
 * <p><b>{@code SERVICE_FEE}</b> est notre revenu explicite, deja factures au client sous la
 * forme de {@code Order.feeXof} (voir {@code RateEngine}) — distinct de la marge de change
 * ({@code customerRate} vs {@code baseRate}), qui n'est PAS (encore) tracee ligne a ligne ici :
 * elle ne se materialise qu'au moment ou le reglement est effectivement execute a un taux reel
 * (manuel aujourd'hui, TransFi demain), jamais a la creation de l'ordre.
 *
 * <p>{@code PROVIDER_FEE}/{@code RAIL_FEE}/{@code GAS_FEE} sont des couts negatifs, connus
 * seulement une fois un prestataire de paiement automatique (TransFi) branche — extraits du
 * payload de son webhook quand ce champ existe, jamais inventes.
 */
public enum LedgerEntryType {
    /** Frais de service factures au client (Order.feeXof) — notre revenu explicite. */
    SERVICE_FEE,
    /** Frais preleves par le prestataire de paiement (ex. TransFi) sur un payin/payout. */
    PROVIDER_FEE,
    /** Frais du rail utilise (mobile money, bancaire...), quand nous les absorbons. */
    RAIL_FEE,
    /** Frais de gas blockchain (payout USDT). */
    GAS_FEE,
    /** Correction manuelle par un administrateur — toujours justifiee par {@code description}. */
    ADJUSTMENT
}
