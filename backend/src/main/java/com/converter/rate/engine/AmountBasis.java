package com.converter.rate.engine;

/**
 * Devise dans laquelle le montant fourni au moteur de tarification est
 * exprime.
 *
 * <p>Concept du moteur de tarification, volontairement independant du
 * vocabulaire du module {@code quote} : {@link RateEngine} ne doit
 * connaitre aucune notion de "Quote" (regle de dependance de
 * l'architecture — un module de plus bas niveau ne remonte jamais vers
 * un module qui en depend). {@code QuoteService} traduit
 * {@code QuoteDirection} vers ce type avant d'appeler le moteur.
 */
public enum AmountBasis {

    /** Le montant fourni est un montant XOF (le client sait ce qu'il paie). */
    XOF,

    /** Le montant fourni est un montant CNY cible (le client sait ce qu'il veut que le beneficiaire recoive). */
    CNY
}
