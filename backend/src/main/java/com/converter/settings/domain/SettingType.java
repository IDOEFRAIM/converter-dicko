package com.converter.settings.domain;

/**
 * Type d'un parametre metier.
 *
 * <p>Les valeurs sont stockees en texte et converties a la lecture.
 * Le type permet de refuser une saisie incoherente au moment de la
 * modification, plutot que de decouvrir l'erreur au premier calcul
 * financier qui l'utilise.
 */
public enum SettingType {
    STRING,
    INTEGER,
    DECIMAL,
    BOOLEAN
}
