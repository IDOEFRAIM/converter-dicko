package com.converter.rate.engine;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Regles d'arrondi centralisees et deterministes du moteur de
 * tarification.
 *
 * <p>Deux regles, et deux seulement, appliquees exclusivement aux
 * points de sortie du calcul (jamais aux etapes intermediaires) :
 * <ul>
 *   <li>tout montant XOF financierement engageant (frais, montant brut
 *       derive) est arrondi <b>au superieur</b>, echelle 0 — le XOF n'a
 *       pas de sous-unite ;</li>
 *   <li>tout montant CNY final est arrondi <b>a l'inferieur</b>,
 *       echelle 2.</li>
 * </ul>
 * Cette dissymetrie garantit que la plateforme ne peut jamais devoir
 * plus qu'elle n'a encaisse : le residu d'arrondi (fraction de XOF ou
 * moins de 0,01 CNY) reste toujours du cote de la tresorerie. C'est la
 * convention prudente standard en change, deja validee en Phase 1.
 */
public final class MoneyRounding {

    /**
     * Precision de calcul intermediaire, avant l'arrondi final explicite.
     * Suffisamment large pour qu'aucune division (taux, marge) ne puisse
     * lever {@code ArithmeticException: Non-terminating decimal expansion}.
     */
    public static final MathContext INTERMEDIATE = new MathContext(20, RoundingMode.HALF_UP);

    private MoneyRounding() {
    }

    /** Arrondit un montant XOF au superieur, echelle 0. */
    public static BigDecimal roundXofUp(BigDecimal value) {
        return value.setScale(0, RoundingMode.UP);
    }

    /** Arrondit un montant CNY a l'inferieur, echelle 2. */
    public static BigDecimal roundCnyDown(BigDecimal value) {
        return value.setScale(2, RoundingMode.DOWN);
    }

    /**
     * Normalise un taux de change a l'echelle de stockage (6 decimales),
     * arrondi au plus proche : ce n'est pas un arrondi "financier" au
     * sens des deux regles ci-dessus (aucun montant n'est engage a ce
     * stade), seulement une precision d'affichage/persistance stable,
     * appliquee une seule fois pour que tous les calculs derives d'un
     * meme taux utilisent exactement la meme valeur.
     */
    public static BigDecimal normalizeRate(BigDecimal value) {
        return value.setScale(6, RoundingMode.HALF_UP);
    }
}
