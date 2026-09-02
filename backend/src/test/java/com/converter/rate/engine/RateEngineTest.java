package com.converter.rate.engine;

import com.converter.common.exception.BusinessException;
import com.converter.rate.domain.MarketRate;
import com.converter.rate.domain.RateProviderType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests unitaires purs : {@link RateEngine} n'a aucune dependance
 * Spring/HTTP/persistence, il est donc instancie directement.
 */
class RateEngineTest {

    private final RateEngine engine = new RateEngine();

    @Test
    void applyMargin_increasesRateByPercentage() {
        // 1 CNY = 85 XOF sur le marche, marge 1.5 % -> le client doit
        // donner davantage de XOF pour 1 CNY : c'est ainsi que la marge
        // de change est capturee, independamment des frais de service.
        BigDecimal customerRate = engine.applyMargin(new BigDecimal("85"), new BigDecimal("1.5"));

        assertThat(customerRate).isEqualByComparingTo("86.275000");
    }

    @Test
    void applyMargin_withZeroMargin_equalsMarketRate() {
        BigDecimal customerRate = engine.applyMargin(new BigDecimal("85"), BigDecimal.ZERO);

        assertThat(customerRate).isEqualByComparingTo("85.000000");
    }

    @Test
    void priceSendXof_withNoMarginOrFee_matchesReferenceExample() {
        // Exemple canonique de la specification : 100 000 XOF a 85 XOF/CNY
        // sans marge ni frais -> 1 176,47 CNY (arrondi CNY a l'inferieur).
        MarketRate marketRate = marketRateOf("85");

        PricingResult result = engine.price(AmountBasis.XOF, new BigDecimal("100000"), marketRate,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        assertThat(result.customerRate()).isEqualByComparingTo("85.000000");
        assertThat(result.feeXof()).isEqualByComparingTo("0");
        assertThat(result.netAmountXof()).isEqualByComparingTo("100000");
        assertThat(result.amountCny()).isEqualByComparingTo("1176.47");
    }

    @Test
    void priceSendXof_feeIsRoundedUp_evenOnATinyFraction() {
        // Taux 100 pour simplifier (customerRate = 100 exactement).
        // Frais 2.5 % sur 100 001 XOF = 2500,025 -> arrondi AU SUPERIEUR
        // (jamais a l'inferieur) = 2501, jamais 2500.
        MarketRate marketRate = marketRateOf("100");

        PricingResult result = engine.price(AmountBasis.XOF, new BigDecimal("100001"), marketRate,
                BigDecimal.ZERO, new BigDecimal("2.5"), BigDecimal.ZERO);

        assertThat(result.feeXof()).isEqualByComparingTo("2501");
        assertThat(result.netAmountXof()).isEqualByComparingTo("97500");
        assertThat(result.amountCny()).isEqualByComparingTo("975.00");
    }

    @Test
    void priceSendXof_cnyIsRoundedDown_neverUp() {
        MarketRate marketRate = marketRateOf("85");

        // 100 100 / 85 = 1177,647058... -> arrondi CNY a l'inferieur = 1177.64,
        // jamais 1177.65.
        PricingResult result = engine.price(AmountBasis.XOF, new BigDecimal("100100"), marketRate,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        assertThat(result.amountCny()).isEqualByComparingTo("1177.64");
    }

    @Test
    void priceReceiveCny_derivesConsistentGrossAmount() {
        // Le client veut que le beneficiaire recoive exactement 1000 CNY,
        // taux client = 100 (marge nulle), frais 2.5 %. Verifie que la
        // formule inverse reconcilie exactement avec la formule directe :
        // reappliquer SEND_XOF au montant XOF brut obtenu doit retomber
        // sur les memes frais et le meme montant CNY.
        MarketRate marketRate = marketRateOf("100");

        PricingResult result = engine.price(AmountBasis.CNY, new BigDecimal("1000"), marketRate,
                BigDecimal.ZERO, new BigDecimal("2.5"), BigDecimal.ZERO);

        assertThat(result.amountXof()).isEqualByComparingTo("102565");
        assertThat(result.feeXof()).isEqualByComparingTo("2565");
        assertThat(result.netAmountXof()).isEqualByComparingTo("100000");
        assertThat(result.amountCny()).isEqualByComparingTo("1000.00");

        // Coherence interne : rejouer SEND_XOF sur le montant brut obtenu
        // doit produire exactement le meme resultat (memes frais, meme
        // montant CNY) — un Quote RECEIVE_CNY n'est jamais qu'un SEND_XOF
        // "presente a l'envers".
        PricingResult replay = engine.price(AmountBasis.XOF, result.amountXof(), marketRate,
                BigDecimal.ZERO, new BigDecimal("2.5"), BigDecimal.ZERO);
        assertThat(replay.feeXof()).isEqualByComparingTo(result.feeXof());
        assertThat(replay.amountCny()).isEqualByComparingTo(result.amountCny());
    }

    @Test
    void priceReceiveCny_neverDeliversLessThanRequested() {
        // Propriete de securite : quel que soit l'arrondi, le montant CNY
        // final ne doit jamais etre INFERIEUR a ce que le client a
        // demande — l'arrondi protege toujours la tresorerie, jamais le
        // client n'est floue.
        MarketRate marketRate = marketRateOf("85");
        BigDecimal[] targets = {
                new BigDecimal("1000.01"), new BigDecimal("50.33"), new BigDecimal("9999.99"), new BigDecimal("1")
        };
        BigDecimal[] fees = {BigDecimal.ZERO, new BigDecimal("1.75"), new BigDecimal("5")};

        for (BigDecimal target : targets) {
            for (BigDecimal fee : fees) {
                PricingResult result = engine.price(AmountBasis.CNY, target, marketRate,
                        new BigDecimal("1.5"), fee, new BigDecimal("100"));

                assertThat(result.amountCny())
                        .as("cible=%s frais=%s", target, fee)
                        .isGreaterThanOrEqualTo(target);
                // Identite comptable toujours verifiee, quels que soient les arrondis.
                assertThat(result.netAmountXof()).isEqualByComparingTo(
                        result.amountXof().subtract(result.feeXof()));
            }
        }
    }

    @Test
    void price_withAmountTooSmallToCoverFees_throwsBusinessException() {
        MarketRate marketRate = marketRateOf("85");

        assertThatThrownBy(() -> engine.price(AmountBasis.XOF, new BigDecimal("50"), marketRate,
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("1000")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void price_withNonTerminatingDivision_doesNotThrowAndStaysConsistent() {
        // 333333 / 85.8... n'a pas de developpement decimal fini : verifie
        // que le calcul aboutit (pas d'ArithmeticException) et que
        // l'identite comptable est preservee, sans dependre d'une valeur
        // magique difficile a verifier a la main.
        MarketRate marketRate = marketRateOf("85");

        PricingResult result = engine.price(AmountBasis.XOF, new BigDecimal("333333"), marketRate,
                new BigDecimal("1.5"), new BigDecimal("0.73"), new BigDecimal("57"));

        assertThat(result.netAmountXof()).isEqualByComparingTo(
                result.amountXof().subtract(result.feeXof()));
        assertThat(result.amountCny()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    void priceSendXof_withCombinedFixedAndPercentFee_appliesBothExactlyOnce() {
        // Taux 100 (customerRate = 100), frais = 2 % + 500 XOF fixes.
        MarketRate marketRate = marketRateOf("100");

        PricingResult result = engine.price(AmountBasis.XOF, new BigDecimal("100000"), marketRate,
                BigDecimal.ZERO, new BigDecimal("2"), new BigDecimal("500"));

        // feeXof = roundUp(100000 * 2/100 + 500) = roundUp(2500) = 2500 — les deux composantes
        // sont additionnees une seule fois, jamais appliquees deux fois ni sur le mauvais montant.
        assertThat(result.feeXof()).isEqualByComparingTo("2500");
        assertThat(result.netAmountXof()).isEqualByComparingTo("97500");
        assertThat(result.netAmountXof()).isEqualByComparingTo(result.amountXof().subtract(result.feeXof()));
        assertThat(result.amountCny()).isEqualByComparingTo("975.00");
    }

    @Test
    void priceReceiveCny_withPercentFee_feeIsAppliedOnGrossOnce_andNetCoversTheTarget() {
        // Sens inverse : on vise 1 000 CNY, taux 100, frais 10 %.
        MarketRate marketRate = marketRateOf("100");

        PricingResult result = engine.price(AmountBasis.CNY, new BigDecimal("1000"), marketRate,
                BigDecimal.ZERO, new BigDecimal("10"), BigDecimal.ZERO);

        // Les frais sont calcules sur le montant XOF BRUT (grossAmountXof), une seule fois :
        // net = brut - frais, et net/customerRate doit couvrir la cible (jamais moins).
        assertThat(result.feeXof()).isEqualByComparingTo(
                MoneyRounding.roundXofUp(result.amountXof().multiply(new BigDecimal("0.10"))));
        assertThat(result.netAmountXof()).isEqualByComparingTo(result.amountXof().subtract(result.feeXof()));
        assertThat(result.amountCny()).isGreaterThanOrEqualTo(new BigDecimal("1000.00"));
        // Pas de sur-facturation grossiere : le brut reste dans l'ordre de grandeur de cible/(1-fee).
        assertThat(result.amountXof()).isLessThan(new BigDecimal("112000"));
    }

    private static MarketRate marketRateOf(String cfaPerCny) {
        return new MarketRate("XOF/CNY", new BigDecimal(cfaPerCny), RateProviderType.MANUAL,
                Instant.parse("2026-08-20T09:00:00Z"), UUID.randomUUID());
    }
}
