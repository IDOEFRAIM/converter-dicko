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
    BLOCKED
}
