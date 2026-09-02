package com.converter.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injecte le {@link CurrentUser} authentifie comme parametre de
 * controleur.
 *
 * <p>Prefere a {@code @AuthenticationPrincipal} pour son intention
 * explicite dans les signatures de methode, et pour ouvrir un point
 * d'extension unique si le principal devait un jour porter davantage
 * de contexte (ex. preferences d'affichage).
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuthenticatedUser {
}
