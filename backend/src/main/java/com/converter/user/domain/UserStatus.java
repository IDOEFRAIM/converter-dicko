package com.converter.user.domain;

/** Etat d'un compte client. */
public enum UserStatus {

    /** Le compte peut se connecter et operer normalement. */
    ACTIVE,

    /**
     * Le compte a ete desactive par un administrateur.
     *
     * <p>Le blocage est verifie a chaque requete authentifiee, et pas
     * seulement a la connexion : un jeton deja emis cesse donc d'etre
     * utilisable immediatement.
     */
    BLOCKED,

    /**
     * Suppression demandee par l'utilisateur lui-meme (voir
     * {@code AccountDeletionService}) : donnees personnelles anonymisees,
     * jamais la ligne elle-meme (les tables financieres qui referencent
     * cet utilisateur doivent conserver un {@code user_id} valide). Meme
     * verification a chaque requete que {@link #BLOCKED} : un jeton deja
     * emis avant la suppression cesse immediatement d'etre utilisable.
     */
    DELETED
}
