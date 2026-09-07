package com.converter.user.domain;

/**
 * Profil d'experience marketing du compte — pilote uniquement l'habillage cote
 * mobile (couleurs, badges, ton des notifications), jamais le moteur de
 * change : memes taux, memes frais, meme backend de tarification pour les
 * trois valeurs. Distinct de {@code BusinessProfile} (Personnel vs
 * Professionnel), un axe totalement independant.
 *
 * <p>Auto-selectionnable par le client lui-meme (voir {@code AuthController
 * #updateExperienceProfile}) — contrairement a {@code kycVerified}, ceci
 * n'est jamais un controle administrateur.
 */
public enum ExperienceProfile {
    PRO,
    STUDENT_MALE,
    STUDENT_FEMALE
}
