package com.converter.audit.domain;

/**
 * Catalogue ferme des operations tracees.
 *
 * <p>Un enum plutot qu'une chaine libre : le journal d'audit sert de
 * preuve, ses valeurs doivent rester requetables et stables dans le
 * temps. Une faute de frappe rendrait une categorie d'evenements
 * invisible aux recherches.
 *
 * <p>Certaines actions concernent des modules livres ulterieurement.
 * Les declarer des maintenant fige le vocabulaire d'audit.
 */
public enum AuditAction {

    // ---- Comptes ----
    USER_REGISTERED,
    USER_LOGIN_SUCCESS,
    USER_LOGIN_FAILED,
    USER_BLOCKED,
    USER_UNBLOCKED,

    // ---- Taux de change (Phase 1, non declenche) ----
    EXCHANGE_RATE_CREATED,

    // ---- Rate Engine / Quote (Phase 3) ----
    RATE_SOURCE_PUBLISHED,
    QUOTE_CREATED,
    QUOTE_ACCEPTED,
    QUOTE_CANCELLED,
    QUOTE_EXPIRED,

    // ---- Ordres ----
    ORDER_CREATED,
    ORDER_CANCELLED,
    ORDER_REJECTED,
    ORDER_EXPIRED,
    ORDER_PAYMENT_SUBMITTED,
    ORDER_PAYMENT_VERIFIED,
    ORDER_PROCESSING_STARTED,
    ORDER_COMPLETED,

    // ---- Paiements ----
    PAYMENT_SUBMITTED,
    PAYMENT_PROOF_UPLOADED,
    PAYMENT_VERIFIED,
    PAYMENT_CONFIRMED,
    PAYMENT_REJECTED,

    // ---- Reglement (Settlement) ----
    SETTLEMENT_CREATED,
    SETTLEMENT_EXECUTED,

    // ---- Tresorerie ----
    TREASURY_DEPOSIT,
    TREASURY_WITHDRAWAL,
    TREASURY_ADJUSTMENT,
    TREASURY_RESERVED,
    TREASURY_RELEASED,
    TREASURY_CONSUMED,

    // ---- Configuration ----
    SETTING_UPDATED
}
