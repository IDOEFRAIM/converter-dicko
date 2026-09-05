package com.converter.settings.domain;

/**
 * Catalogue ferme des parametres metier.
 *
 * <p>Le nom de la constante est exactement la cle en base. Passer par
 * cet enum plutot que par des chaines libres garantit qu'une faute de
 * frappe est detectee a la compilation et non en production, sur un
 * plafond de montant silencieusement absent.
 *
 * <p>Certaines cles servent des modules livres ulterieurement : elles
 * sont amorcees des la migration V4 pour que la configuration metier
 * soit complete et administrable des le premier demarrage.
 */
public enum SettingKey {

    MIN_ORDER_AMOUNT_CFA(SettingType.DECIMAL),
    MAX_ORDER_AMOUNT_CFA(SettingType.DECIMAL),
    RATE_LOCK_DURATION_MINUTES(SettingType.INTEGER),
    ORDER_AUTO_EXPIRE_ENABLED(SettingType.BOOLEAN),
    REQUIRE_PAYMENT_PROOF(SettingType.BOOLEAN),
    TREASURY_RESERVE_ON_ORDER(SettingType.BOOLEAN),
    MAX_PROOF_FILE_SIZE_BYTES(SettingType.INTEGER),
    MAX_PROOFS_PER_PAYMENT(SettingType.INTEGER),
    ENABLED_PAYMENT_METHODS(SettingType.STRING),
    MAX_OPEN_ORDERS_PER_USER(SettingType.INTEGER),
    DEFAULT_MARGIN_PERCENTAGE(SettingType.DECIMAL),
    DEFAULT_FEE_PERCENTAGE(SettingType.DECIMAL),
    DEFAULT_FIXED_FEE_XOF(SettingType.DECIMAL),

    /** Fenetre de paiement d'un ordre, en minutes ; au-dela, l'ordre est expire et sa reservation liberee (passe 2, P2-1). */
    ORDER_PAYMENT_WINDOW_MINUTES(SettingType.INTEGER),
    /** Ecart tolere, en XOF, entre le montant recu declare et le montant attendu d'un paiement ; 0 = exact (passe 2, P2-2). */
    PAYMENT_AMOUNT_TOLERANCE_XOF(SettingType.DECIMAL),
    /** Age maximal, en minutes, d'une cotation encore consideree CURRENT ; 0 = pas de controle de fraicheur (passe 2, P2-7). */
    RATE_MAX_AGE_MINUTES(SettingType.INTEGER),

    /** Montant XOF (inclus) a partir duquel la verification d'identite (KYC) est obligatoire pour creer un ordre. */
    KYC_REQUIRED_THRESHOLD_XOF(SettingType.DECIMAL);

    private final SettingType type;

    SettingKey(SettingType type) {
        this.type = type;
    }

    public SettingType type() {
        return type;
    }
}
