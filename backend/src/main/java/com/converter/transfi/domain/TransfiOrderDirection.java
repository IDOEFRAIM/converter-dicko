package com.converter.transfi.domain;

/** Sens d'un ordre cote TransFi, voir {@code docs/TRANSFI_INTEGRATION.md} section 2. */
public enum TransfiOrderDirection {
    /** Le client encaisse en XOF (mobile money, bancaire...). */
    PAYIN,
    /** Nous decaissons vers le beneficiaire (CNY, USDT...). */
    PAYOUT
}
