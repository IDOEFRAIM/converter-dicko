package com.converter.rate.cost;

import com.converter.common.exception.BusinessException;
import com.converter.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

import static com.converter.rate.engine.MoneyRounding.INTERMEDIATE;
import static com.converter.rate.engine.MoneyRounding.normalizeRate;

/**
 * Calcule le cout de revient reel (en XOF par CNY) de la chaine de
 * conversion effectivement utilisee par la tresorerie :
 *
 * <pre>
 * XOF -&gt; USD -&gt; CNY
 * </pre>
 *
 * <p><b>Ce composant ne fait que ca.</b> Il ne cree ni {@code Quote} ni
 * {@code Order}, n'applique aucune marge commerciale, et ne depend
 * d'aucune infrastructure Spring autre que l'injection elle-meme (pas
 * de persistance, pas d'HTTP) — exactement comme {@link com.converter.rate.engine.RateEngine},
 * dont il reprend les conventions d'arrondi ({@link com.converter.rate.engine.MoneyRounding}) et
 * l'usage exclusif de {@link BigDecimal}.
 *
 * <p>Le resultat, {@code breakEvenRate}, est une donnee interne
 * confidentielle (cout de revient) : elle ne doit jamais atteindre un
 * DTO expose au client. Depuis la Phase 3.1, ce resultat alimente
 * directement le pricing du {@code Quote} (via {@code CostRateProvider}
 * -&gt; {@code RateEngine}, voir docs/ARCHITECTURE.md, Partie I, section
 * G.7) — {@code RateSource}/{@code RateAdminService} restent une chaine
 * separee, toujours utilisee par {@code preferredrate}, mais plus par
 * la creation d'un {@code Quote}.
 *
 * <p>{@code feeUsdCnyFixedUsd} etant un cout fixe, le cout de revient
 * depend du montant utilise : ce composant expose donc
 * {@link #calculateBreakEven(BigDecimal, BigDecimal, BigDecimal, BigDecimal, BigDecimal)},
 * jamais une simple lecture de taux journalier sans montant.
 */
@Component
public class CostRateCalculator {

    /**
     * Calcule le cout de revient XOF/CNY pour un montant XOF donne.
     *
     * <pre>
     * netXof       = amountXof * (1 - feeXofUsdPercent)
     * usdReceived  = netXof / rateXofUsd
     * usdNet       = usdReceived - feeUsdCnyFixedUsd
     * cnyReceived  = usdNet * rateUsdCny
     * breakEvenRate = amountXof / cnyReceived
     * </pre>
     *
     * @param amountXof         montant XOF de reference, doit etre strictement positif
     * @param rateXofUsd        taux XOF pour 1 USD, doit etre strictement positif
     * @param rateUsdCny        taux CNY pour 1 USD, doit etre strictement positif
     * @param feeXofUsdPercent  frais proportionnels de la jambe XOF -&gt; USD, exprimes en
     *                          <b>fraction decimale</b> (0.01 = 1&nbsp;%), dans [0, 1)
     * @param feeUsdCnyFixedUsd frais fixe de la jambe USD -&gt; CNY, en USD, doit etre &gt;= 0
     * @throws BusinessException {@code VALIDATION_ERROR} si un parametre est hors bornes, ou si
     *         les frais consomment la totalite du montant converti (USD net ou CNY receus &lt;= 0)
     */
    public BreakEvenResult calculateBreakEven(BigDecimal amountXof,
                                              BigDecimal rateXofUsd,
                                              BigDecimal rateUsdCny,
                                              BigDecimal feeXofUsdPercent,
                                              BigDecimal feeUsdCnyFixedUsd) {
        requirePositive(amountXof, "amountXof");
        requirePositive(rateXofUsd, "rateXofUsd");
        requirePositive(rateUsdCny, "rateUsdCny");
        requireInFraction(feeXofUsdPercent, "feeXofUsdPercent");
        requireNonNegative(feeUsdCnyFixedUsd, "feeUsdCnyFixedUsd");

        BigDecimal netXof = amountXof.multiply(BigDecimal.ONE.subtract(feeXofUsdPercent), INTERMEDIATE);
        BigDecimal usdReceived = netXof.divide(rateXofUsd, INTERMEDIATE);
        BigDecimal usdNet = usdReceived.subtract(feeUsdCnyFixedUsd);
        requirePositiveResult(usdNet, "Les frais depassent le montant converti en USD : "
                + "aucun dollar net ne resterait pour la conversion vers le CNY.");

        BigDecimal cnyReceived = usdNet.multiply(rateUsdCny, INTERMEDIATE);
        requirePositiveResult(cnyReceived, "Le montant CNY obtenu serait nul ou negatif.");

        BigDecimal breakEvenRate = normalizeRate(amountXof.divide(cnyReceived, INTERMEDIATE));

        return new BreakEvenResult(amountXof, netXof, usdReceived, usdNet, cnyReceived, breakEvenRate);
    }

    private void requirePositive(BigDecimal value, String field) {
        if (value == null || value.signum() <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Le parametre " + field + " doit etre strictement positif.");
        }
    }

    private void requireNonNegative(BigDecimal value, String field) {
        if (value == null || value.signum() < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Le parametre " + field + " ne peut pas etre negatif.");
        }
    }

    private void requireInFraction(BigDecimal value, String field) {
        if (value == null || value.signum() < 0 || value.compareTo(BigDecimal.ONE) >= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Le parametre " + field + " doit etre une fraction dans [0, 1) (0.01 = 1 %).");
        }
    }

    private void requirePositiveResult(BigDecimal value, String message) {
        if (value.signum() <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, message);
        }
    }
}
