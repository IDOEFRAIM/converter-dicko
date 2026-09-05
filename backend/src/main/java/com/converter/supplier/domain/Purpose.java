package com.converter.supplier.domain;

/**
 * Catalogue ferme du motif d'un transfert, transversal au domaine (utilise a la fois par
 * {@code Supplier}, comme classification par defaut du fournisseur, et par {@code Order}, comme
 * motif effectif d'une transaction donnee — voir {@code orders.purpose}). Les deux valeurs sont
 * independantes l'une de l'autre : le motif par defaut d'un fournisseur n'est jamais copie
 * aveuglement sur un ordre, il ne fait que pre-remplir une valeur que l'utilisateur peut changer.
 */
public enum Purpose {
    PERSONAL,
    EDUCATION,
    FAMILY_SUPPORT,
    IMPORT_GOODS,
    SERVICES,
    BUSINESS,
    OTHER
}
