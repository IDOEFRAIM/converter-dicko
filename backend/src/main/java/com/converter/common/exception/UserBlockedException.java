package com.converter.common.exception;

/**
 * Le compte existe et le mot de passe est peut-etre correct, mais le
 * compte a ete desactive par un administrateur.
 *
 * <p>Levee aussi bien a la connexion qu'a chaque requete authentifiee :
 * le statut est relu en base a chaque appel, de sorte qu'un blocage
 * prend effet immediatement sans attendre l'expiration du jeton.
 */
public class UserBlockedException extends BusinessException {

    public UserBlockedException() {
        super(ErrorCode.USER_BLOCKED,
                "Ce compte a ete desactive. Contactez le support.");
    }
}
