package com.converter.rate.cost;

import java.math.BigDecimal;

/**
 * Resultat complet et decompose du calcul de cout de revient
 * XOF -&gt; USD -&gt; CNY.
 *
 * <p>Chaque champ correspond a une etape distincte de la chaine
 * (voir {@link CostRateCalculator}), jamais fusionnee : un audit doit
 * pouvoir retrouver, a partir d'un seul {@code breakEvenRate}, tous
 * les montants intermediaires qui y ont mene.
 *
 * @param amountXof          montant XOF de reference utilise pour ce calcul
 * @param netXof             {@code amountXof} apres frais XOF -&gt; USD
 * @param usdReceived        USD obtenus en changeant {@code netXof} au taux {@code rateXofUsd}
 * @param usdNet             {@code usdReceived} apres frais fixe USD -&gt; CNY
 * @param cnyReceived        CNY finalement obtenus
 * @param breakEvenRate      cout de revient reel, en XOF par CNY ({@code amountXof / cnyReceived})
 */
public record BreakEvenResult(
        BigDecimal amountXof,
        BigDecimal netXof,
        BigDecimal usdReceived,
        BigDecimal usdNet,
        BigDecimal cnyReceived,
        BigDecimal breakEvenRate
) {
}
