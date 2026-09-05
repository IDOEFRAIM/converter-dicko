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

    // ---- Cout de revient XOF -> USD -> CNY ----
    COST_RATE_CONFIGURATION_PUBLISHED,

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

    // ---- Remboursement (Refund) ----
    REFUND_CREATED,
    REFUND_PROCESSED,
    REFUND_REJECTED,

    // ---- Tresorerie ----
    TREASURY_DEPOSIT,
    TREASURY_WITHDRAWAL,
    TREASURY_ADJUSTMENT,
    TREASURY_RESERVED,
    TREASURY_RELEASED,
    TREASURY_CONSUMED,
    TREASURY_REFUNDED,

    // ---- Configuration ----
    SETTING_UPDATED,

    // ---- Fournisseurs (carnet reutilisable, Burkina <-> Chine) ----
    SUPPLIER_CREATED,
    SUPPLIER_UPDATED,
    SUPPLIER_FAVORITED,
    SUPPLIER_UNFAVORITED,
    SUPPLIER_DEACTIVATED,

    // ---- Alertes de taux (Phase 6) ----
    RATE_ALERT_CREATED,
    RATE_ALERT_CANCELLED,
    RATE_ALERT_TRIGGERED,

    // ---- Profil professionnel (Phase 8) ----
    BUSINESS_PROFILE_CREATED,
    BUSINESS_PROFILE_UPDATED,

    // ---- Verification d'identite (KYC) ----
    USER_KYC_VERIFIED,
    USER_KYC_REVOKED
}
