package com.converter.rate.engine;

import java.math.BigDecimal;

/**
 * Resultat complet et decompose d'un calcul de tarification.
 *
 * <p>Chaque champ repond a une question d'audit distincte (voir
 * docs/ARCHITECTURE.md, Partie I, section Q) : quel taux de marche,
 * quelle marge, quel taux client, quels frais, quel montant final —
 * jamais fusionnes en une seule valeur. C'est ce record, dans son
 * integralite, qui est snapshote dans un {@code Quote}.
 *
 * @param marketRate       taux de marche brut au moment du calcul ("1 CNY = X XOF")
 * @param marginPercentage marge appliquee, en pourcentage
 * @param customerRate     taux reellement propose au client — {@code marketRate * (1 + marginPercentage/100)}
 * @param feePercentage    part proportionnelle des frais de service (distincte de la marge)
 * @param fixedFeeXof      part fixe des frais de service, en XOF
 * @param feeXof           frais de service effectivement factures, en XOF (arrondi au superieur)
 * @param amountXof        montant XOF brut (paye par le client)
 * @param netAmountXof     {@code amountXof - feeXof}, montant reellement converti
 * @param amountCny        montant CNY final (arrondi a l'inferieur)
 */
public record PricingResult(
        BigDecimal marketRate,
        BigDecimal marginPercentage,
        BigDecimal customerRate,
        BigDecimal feePercentage,
        BigDecimal fixedFeeXof,
        BigDecimal feeXof,
        BigDecimal amountXof,
        BigDecimal netAmountXof,
        BigDecimal amountCny
) {
}
