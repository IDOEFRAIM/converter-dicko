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
    KYC_REQUIRED_THRESHOLD_XOF(SettingType.DECIMAL),

    /**
     * Montant XOF (inclus) a partir duquel un ordre est traite comme un <b>paiement fournisseur</b>
     * (remarque produit #3) : facture proforma disponible, meme sans fournisseur enregistre. En
     * dessous, c'est un simple echange personnel. Purement une bascule d'affichage/service — ne
     * change jamais le pricing.
     */
    SUPPLIER_PAYMENT_THRESHOLD_XOF(SettingType.DECIMAL),

    /**
     * Part de <b>base</b> de la reduction de marge d'une Ruee reussie (points de marge retires,
     * jamais sous zero, sur LE PROCHAIN devis de chaque participant). La reduction finale y ajoute
     * un bonus par participant ({@link #POOL_REWARD_PER_PARTICIPANT_PCT}) et par volume echange
     * ({@link #POOL_REWARD_PER_MILLION_XOF_PCT}), bornee par {@link #POOL_REWARD_MAX_PCT}
     * (remarque produit #4). Jamais applique retroactivement a l'ordre qui a rempli l'objectif
     * (immutabilite du pricing fige, voir {@code QuoteService}).
     */
    POOL_REWARD_MARGIN_REDUCTION_PERCENTAGE(SettingType.DECIMAL),

    /** Points de marge ajoutes a la recompense de Ruee par participant au-dela du premier (#4). */
    POOL_REWARD_PER_PARTICIPANT_PCT(SettingType.DECIMAL),

    /** Points de marge ajoutes par tranche pleine de 1 000 000 XOF echanges par le groupe (#4). */
    POOL_REWARD_PER_MILLION_XOF_PCT(SettingType.DECIMAL),

    /** Plafond de la reduction de marge accordee par une Ruee, tous bonus cumules (#4). */
    POOL_REWARD_MAX_PCT(SettingType.DECIMAL);

    private final SettingType type;

    SettingKey(SettingType type) {
        this.type = type;
    }

    public SettingType type() {
        return type;
    }
}
