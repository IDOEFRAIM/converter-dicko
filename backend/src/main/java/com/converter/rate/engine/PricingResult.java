package com.converter.rate.engine;

import java.math.BigDecimal;

/**
 * Resultat complet et decompose d'un calcul de tarification.
 *
 * <p>Chaque champ repond a une question d'audit distincte (voir
 * docs/ARCHITECTURE.md, Partie I, section Q) : quel taux de base,
 * quelle marge, quel taux client, quels frais, quel montant final —
 * jamais fusionnes en une seule valeur. C'est ce record, dans son
 * integralite, qui est snapshote dans un {@code Quote}.
 *
 * <p>{@code baseRate} est le taux <b>avant marge</b> — {@link RateEngine}
 * ne connait ni ne se soucie de sa provenance (cotation de marche
 * publiee manuellement, ou cout de revient calcule par
 * {@code CostRateCalculator} : depuis la Phase 3.1, c'est ce dernier
 * qui alimente {@code baseRate} pour tout nouveau {@code Quote} — voir
 * docs/ARCHITECTURE.md, Partie I, section G.7). Ce decouplage est
 * volontaire : le moteur de tarification n'a jamais eu besoin de
 * connaitre {@code MarketRate} ni {@code RateSource} pour faire son
 * travail, seulement un nombre.
 *
 * @param baseRate         taux avant marge au moment du calcul ("1 CNY = X XOF")
 * @param marginPercentage marge appliquee, en pourcentage
 * @param customerRate     taux reellement propose au client — {@code baseRate * (1 + marginPercentage/100)}
 * @param feePercentage    part proportionnelle des frais de service (distincte de la marge)
 * @param fixedFeeXof      part fixe des frais de service, en XOF
 * @param feeXof           frais de service effectivement factures, en XOF (arrondi au superieur)
 * @param amountXof        montant XOF brut (paye par le client)
 * @param netAmountXof     {@code amountXof - feeXof}, montant reellement converti
 * @param amountCny        montant CNY final (arrondi a l'inferieur)
 */
public record PricingResult(
        BigDecimal baseRate,
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
