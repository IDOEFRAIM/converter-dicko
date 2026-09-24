package com.converter.rate.engine;

import com.converter.common.exception.BusinessException;
import com.converter.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

import static com.converter.rate.engine.MoneyRounding.INTERMEDIATE;
import static com.converter.rate.engine.MoneyRounding.normalizeRate;
import static com.converter.rate.engine.MoneyRounding.roundCnyDown;
import static com.converter.rate.engine.MoneyRounding.roundXofUp;

/**
 * Transforme un taux de base en tarification complete pour le client.
 *
 * <pre>
 * baseRate
 *     |
 *     v
 *   Margin
 *     |
 *     v
 * CustomerRate  --- fee ---&gt;  PricingResult (amountXof, amountCny, ...)
 * </pre>
 *
 * <p>Ce composant ne gere ni HTTP, ni JWT, ni persistance : il ne
 * depend que de {@link BigDecimal} et de types de valeur immuables
 * ({@link PricingResult}), ce qui le rend testable unitairement sans
 * contexte Spring ni base de donnees.
 *
 * <p><b>Deliberement agnostique de la provenance de {@code baseRate}.</b>
 * Jusqu'a la Phase 3, {@code baseRate} venait d'une {@code MarketRate}
 * publiee manuellement ({@code RateSource}) ; depuis la Phase 3.1, il
 * vient du {@code breakEvenRate} calcule par {@code CostRateCalculator}
 * a partir de la derniere {@code DailyCostRateConfiguration} (voir
 * docs/ARCHITECTURE.md, Partie I, section G.7). Ce moteur n'a jamais eu
 * besoin de le savoir : il applique la meme formule quel que soit
 * l'appelant, ce qui a permis ce changement de source sans toucher une
 * seule ligne de ce fichier au-dela du renommage {@code marketRate} ->
 * {@code baseRate}.
 *
 * <p>Convention de sens du taux, identique a la Phase 1 : <b>1 CNY =
 * X XOF</b>. Toute l'arithmetique de ce composant utilise
 * exclusivement {@link BigDecimal} — aucun {@code double}/{@code float}
 * n'intervient a aucune etape.
 */
@Component
public class RateEngine {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /**
     * Applique la marge commerciale au taux de base.
     *
     * <p>{@code customerRate = baseRate * (1 + marginPercentage / 100)}.
     * Une marge positive augmente le nombre de XOF necessaires pour
     * obtenir 1 CNY par rapport au taux de base : c'est ainsi que
     * l'entreprise capture sa marge de change, distincte des frais de
     * service (voir {@link #price}) et du cout de revient lui-meme
     * ({@code baseRate}) — trois notions qui ne sont jamais fusionnees.
     */
    public BigDecimal applyMargin(BigDecimal baseRate, BigDecimal marginPercentage) {
        BigDecimal factor = BigDecimal.ONE.add(marginPercentage.divide(HUNDRED, INTERMEDIATE), INTERMEDIATE);
        return normalizeRate(baseRate.multiply(factor, INTERMEDIATE));
    }

    /**
     * Calcule la tarification complete d'un montant, dans un sens ou
     * l'autre.
     *
     * @param basis            devise dans laquelle {@code amount} est exprime
     * @param amount            montant fourni par le client (XOF s'il paie un montant connu,
     *                          CNY s'il vise un montant a recevoir pour le beneficiaire)
     * @param baseRate          taux avant marge a utiliser ("1 CNY = X XOF") — cotation de marche ou
     *                          cout de revient, au choix de l'appelant, ce moteur n'en depend pas
     * @param marginPercentage  marge commerciale, en pourcentage
     * @param feePercentage     part proportionnelle des frais de service
     * @param fixedFeeXof       part fixe des frais de service, en XOF
     */
    public PricingResult price(AmountBasis basis,
                               BigDecimal amount,
                               BigDecimal baseRate,
                               BigDecimal marginPercentage,
                               BigDecimal feePercentage,
                               BigDecimal fixedFeeXof) {
        BigDecimal customerRate = applyMargin(baseRate, marginPercentage);

        return switch (basis) {
            case XOF -> priceFromXof(amount, baseRate, marginPercentage, customerRate, feePercentage, fixedFeeXof);
            case CNY -> priceFromTargetCny(amount, baseRate, marginPercentage, customerRate, feePercentage, fixedFeeXof);
        };
    }

    /**
     * SEND_XOF : le montant XOF brut est connu, tout le reste en decoule.
     *
     * <pre>
     * feeXof       = roundUp(amountXof * feePercentage/100 + fixedFeeXof)
     * netAmountXof = amountXof - feeXof
     * amountCny    = roundDown(netAmountXof / customerRate)
     * </pre>
     */
    private PricingResult priceFromXof(BigDecimal amountXof,
                                       BigDecimal baseRate,
                                       BigDecimal marginPercentage,
                                       BigDecimal customerRate,
                                       BigDecimal feePercentage,
                                       BigDecimal fixedFeeXof) {
        BigDecimal feeXof = computeFee(amountXof, feePercentage, fixedFeeXof);
        BigDecimal netAmountXof = amountXof.subtract(feeXof);
        requirePositiveNet(netAmountXof);
        BigDecimal amountCny = roundCnyDown(netAmountXof.divide(customerRate, INTERMEDIATE));

        return new PricingResult(baseRate, marginPercentage, customerRate, feePercentage, fixedFeeXof,
                feeXof, amountXof, netAmountXof, amountCny);
    }

    /**
     * RECEIVE_CNY : le montant CNY cible est connu ; le montant XOF brut
     * en est deduit par inversion de la formule ci-dessus, puis
     * <b>tout est recalcule a partir du montant XOF brut final</b>
     * (apres arrondi) pour garantir que le devis reste, dans les deux
     * sens, une application exacte de la meme formule SEND_XOF —
     * jamais deux jeux de valeurs qui ne se recoupent pas.
     *
     * <pre>
     * requiredNetXof  = roundUp(targetAmountCny * customerRate)
     * grossAmountXof  = roundUp((requiredNetXof + fixedFeeXof) / (1 - feePercentage/100))
     * feeXof          = roundUp(grossAmountXof * feePercentage/100 + fixedFeeXof)   [recalcule]
     * netAmountXof    = grossAmountXof - feeXof
     * amountCny       = roundDown(netAmountXof / customerRate)                      [recalcule]
     * </pre>
     *
     * <p>Consequence assumee : {@code amountCny} obtenu peut differer du
     * {@code targetAmountCny} initialement vise, d'au plus l'epsilon
     * introduit par l'arrondi au superieur du montant XOF brut — jamais
     * en defaveur du client (l'arrondi protege systematiquement la
     * tresorerie, jamais le contraire), et jamais d'un montant
     * significatif.
     */
    private PricingResult priceFromTargetCny(BigDecimal targetAmountCny,
                                             BigDecimal baseRate,
                                             BigDecimal marginPercentage,
                                             BigDecimal customerRate,
                                             BigDecimal feePercentage,
                                             BigDecimal fixedFeeXof) {
        BigDecimal requiredNetXof = roundXofUp(targetAmountCny.multiply(customerRate, INTERMEDIATE));

        BigDecimal feeFactor = BigDecimal.ONE.subtract(feePercentage.divide(HUNDRED, INTERMEDIATE));
        // Garanti par la contrainte metier feePercentage < 100 (identique a la
        // contrainte CHECK deja en place sur les frais en Phase 1) : feeFactor > 0.
        BigDecimal grossAmountXof = roundXofUp(
                requiredNetXof.add(fixedFeeXof).divide(feeFactor, INTERMEDIATE));

        BigDecimal feeXof = computeFee(grossAmountXof, feePercentage, fixedFeeXof);
        BigDecimal netAmountXof = grossAmountXof.subtract(feeXof);
        requirePositiveNet(netAmountXof);
        BigDecimal amountCny = roundCnyDown(netAmountXof.divide(customerRate, INTERMEDIATE));

        return new PricingResult(baseRate, marginPercentage, customerRate, feePercentage, fixedFeeXof,
                feeXof, grossAmountXof, netAmountXof, amountCny);
    }

    private BigDecimal computeFee(BigDecimal amountXof, BigDecimal feePercentage, BigDecimal fixedFeeXof) {
        BigDecimal proportional = amountXof.multiply(feePercentage, INTERMEDIATE).divide(HUNDRED, INTERMEDIATE);
        return roundXofUp(proportional.add(fixedFeeXof));
    }

    private void requirePositiveNet(BigDecimal netAmountXof) {
        if (netAmountXof.signum() <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Le montant est insuffisant pour couvrir les frais de service.");
        }
    }
}
