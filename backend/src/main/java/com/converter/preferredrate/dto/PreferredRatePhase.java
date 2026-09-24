package com.converter.preferredrate.dto;

/**
 * Vue d'affichage combinant l'etat de la demande ET celui de l'echange,
 * calculee cote backend pour que le frontend n'ait jamais a la deduire
 * lui-meme (deux ecrans distincts : {@code WAITING} vs
 * {@code EXCHANGE_IN_PROGRESS}/{@code EXCHANGE_COMPLETED}, jamais les
 * deux compteurs -- J+3 et 2h -- affiches en meme temps).
 */
public enum PreferredRatePhase {
    /** Demande ACTIVE, taux cible pas encore atteint : compte a rebours J+3 pertinent. */
    WAITING,
    /** Taux atteint, echange demarre et pas encore termine : compte a rebours 2h pertinent, plus de J+3. */
    EXCHANGE_IN_PROGRESS,
    /** Echange termine. */
    EXCHANGE_COMPLETED,
    EXPIRED,
    CANCELLED
}
