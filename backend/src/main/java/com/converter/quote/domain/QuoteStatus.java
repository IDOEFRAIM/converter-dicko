package com.converter.quote.domain;

/**
 * Cycle de vie d'un devis.
 *
 * <pre>
 * ACTIVE ──► ACCEPTED   (terminal — consomme par un futur Order, Phase 4)
 *   │
 *   ├──► EXPIRED         (terminal — 30 minutes ecoulees sans decision)
 *   └──► CANCELLED       (terminal — annulation explicite du client)
 * </pre>
 *
 * <p>{@code ACCEPTED} est le seul etat "financierement immuable" au
 * sens fort du terme metier (voir section 4 du cahier des charges),
 * mais en pratique <b>aucun</b> etat ne modifie jamais les colonnes
 * financieres d'un {@code Quote} : celles-ci sont figees des la
 * creation, quel que soit l'etat ulterieur (voir
 * docs/ARCHITECTURE.md, Partie I, section G.6 et section 5 du cahier
 * des charges de cette phase).
 */
public enum QuoteStatus {
    ACTIVE,
    ACCEPTED,
    EXPIRED,
    CANCELLED
}
