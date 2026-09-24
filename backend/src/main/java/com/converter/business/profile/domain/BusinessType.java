package com.converter.business.profile.domain;

/**
 * Categorie minimale du profil professionnel — distingue "quoi" (le type d'activite), jamais
 * "pourquoi" un transfert donne est effectue (voir {@code com.converter.supplier.domain.Purpose},
 * un axe different, jamais reutilise ici). Catalogue volontairement restreint : ce n'est pas une
 * taxonomie ERP.
 */
public enum BusinessType {
    IMPORTER,
    MERCHANT,
    SERVICES,
    OTHER
}
