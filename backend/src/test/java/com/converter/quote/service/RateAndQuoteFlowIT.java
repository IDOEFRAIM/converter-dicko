package com.converter.quote.service;

import com.converter.common.api.ApiResponse;
import com.converter.common.api.ErrorResponse;
import com.converter.quote.domain.QuoteDirection;
import com.converter.quote.domain.QuoteStatus;
import com.converter.quote.dto.CreateQuoteRequest;
import com.converter.quote.dto.QuoteResponse;
import com.converter.rate.cost.dto.CostRateConfigurationResponse;
import com.converter.settings.domain.SettingKey;
import com.converter.support.AbstractRateQuoteIT;
import com.converter.user.domain.RoleCode;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Parcours de bout en bout : publication d'un taux, creation d'un
 * devis, consultation, acceptation — et la garantie centrale de la
 * Phase 3 : un devis deja cree ne change jamais quand le taux change
 * ensuite.
 */
class RateAndQuoteFlowIT extends AbstractRateQuoteIT {

    @Test
    void publishRate_thenCreateQuote_thenAccept_succeeds() {
        resetMarginToZero();
        String admin = adminToken();
        publishRate(admin, "85.000000");

        String user = tokenFor(createUser(com.converter.user.domain.RoleCode.USER));
        QuoteResponse quote = createQuote(user, new CreateQuoteRequest(QuoteDirection.SEND_XOF,
                new BigDecimal("100000"), null));

        assertThat(quote.status()).isEqualTo(QuoteStatus.ACTIVE);
        // Marge remise a zero pour ce test : reproduit exactement
        // l'exemple canonique de la specification (100 000 XOF a 85
        // XOF/CNY -> 1 176,47 CNY).
        assertThat(quote.amountCny()).isEqualByComparingTo("1176.47");
        assertThat(quote.customerRate()).isEqualByComparingTo("85.000000");

        ResponseEntity<ApiResponse<QuoteResponse>> accepted = restTemplate.exchange(
                "/api/v1/quotes/" + quote.id() + "/accept", HttpMethod.POST,
                new HttpEntity<>(authHeaders(user)),
                new ParameterizedTypeReference<ApiResponse<QuoteResponse>>() {
                });

        assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(accepted.getBody().data().status()).isEqualTo(QuoteStatus.ACCEPTED);
    }

    @Test
    void acceptingTwice_returnsConflict() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        String user = tokenFor(createUser(com.converter.user.domain.RoleCode.USER));
        QuoteResponse quote = createQuote(user, new CreateQuoteRequest(QuoteDirection.SEND_XOF,
                new BigDecimal("50000"), null));

        restTemplate.exchange("/api/v1/quotes/" + quote.id() + "/accept", HttpMethod.POST,
                new HttpEntity<>(authHeaders(user)), String.class);

        ResponseEntity<ErrorResponse> second = restTemplate.exchange(
                "/api/v1/quotes/" + quote.id() + "/accept", HttpMethod.POST,
                new HttpEntity<>(authHeaders(user)), ErrorResponse.class);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(second.getBody().code()).isEqualTo("INVALID_QUOTE_STATE");
    }

    @Test
    void cancel_transitionsToCancelled() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        String user = tokenFor(createUser(com.converter.user.domain.RoleCode.USER));
        QuoteResponse quote = createQuote(user, new CreateQuoteRequest(QuoteDirection.RECEIVE_CNY,
                null, new BigDecimal("500")));

        ResponseEntity<ApiResponse<QuoteResponse>> cancelled = restTemplate.exchange(
                "/api/v1/quotes/" + quote.id() + "/cancel", HttpMethod.POST,
                new HttpEntity<>(authHeaders(user)),
                new ParameterizedTypeReference<ApiResponse<QuoteResponse>>() {
                });

        assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cancelled.getBody().data().status()).isEqualTo(QuoteStatus.CANCELLED);
    }

    @Test
    void changingRate_neverAffectsAlreadyCreatedQuote() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        String user = tokenFor(createUser(com.converter.user.domain.RoleCode.USER));

        QuoteResponse quoteA = createQuote(user, new CreateQuoteRequest(QuoteDirection.SEND_XOF,
                new BigDecimal("100000"), null));

        // L'administrateur republie un taux different.
        publishRate(admin, "90.000000");

        QuoteResponse quoteB = createQuote(user, new CreateQuoteRequest(QuoteDirection.SEND_XOF,
                new BigDecimal("100000"), null));
        assertThat(quoteB.amountCny()).isNotEqualByComparingTo(quoteA.amountCny());

        // Le devis A, relu apres coup, doit renvoyer EXACTEMENT les memes
        // valeurs qu'a sa creation — le changement de taux ne l'a jamais
        // touche.
        ResponseEntity<ApiResponse<QuoteResponse>> reloaded = restTemplate.exchange(
                "/api/v1/quotes/" + quoteA.id(), HttpMethod.GET,
                new HttpEntity<>(authHeaders(user)),
                new ParameterizedTypeReference<ApiResponse<QuoteResponse>>() {
                });
        QuoteResponse reloadedA = reloaded.getBody().data();
        assertThat(reloadedA.amountCny()).isEqualByComparingTo(quoteA.amountCny());
        assertThat(reloadedA.customerRate()).isEqualByComparingTo(quoteA.customerRate());
        assertThat(reloadedA.feeXof()).isEqualByComparingTo(quoteA.feeXof());
    }

    @Test
    void create_withBothAmountsProvided_returnsValidationError() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        String user = tokenFor(createUser(com.converter.user.domain.RoleCode.USER));

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/v1/quotes", HttpMethod.POST,
                new HttpEntity<>(new CreateQuoteRequest(QuoteDirection.SEND_XOF,
                        new BigDecimal("100000"), new BigDecimal("500")), authHeaders(user)),
                ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void createQuote_customerRateEqualsBreakEvenRateWithMarginApplied() {
        // Chaine complete (Phase 3.1) : parametres XOF -> USD -> CNY reels -> breakEvenRate ->
        // + marge commerciale -> customerRate effectivement utilise par le Quote.
        //
        // breakEvenRate="87.971572" est une valeur calculee independamment (Decimal, precision
        // arbitraire) lors de la mission precedente pour ces memes parametres (1 000 000 XOF, 583,
        // 6.70, 1 %, 1.50 USD) : jamais une constante inventee pour ce test. On l'utilise ici
        // directement (plutot que de la relire via /cost-rates/current) pour que cette assertion ne
        // dependre d'aucun autre appel HTTP entre la publication et la creation du devis.
        String admin = adminToken();
        publishCostRate(admin, "583", "6.70", "0.01", "1.50", "1000000");
        settingsService.update(SettingKey.DEFAULT_MARGIN_PERCENTAGE, "2", createUser(RoleCode.ADMIN).getId());

        String user = tokenFor(createUser(com.converter.user.domain.RoleCode.USER));
        QuoteResponse quote = createQuote(user, new CreateQuoteRequest(QuoteDirection.SEND_XOF,
                new BigDecimal("100000"), null));

        // customerRate = breakEvenRate * (1 + margin/100), avec les memes regles d'arrondi que
        // RateEngine (verifiees independamment par RateEngineTest) : ce test verifie le CABLAGE
        // bout en bout (config -> breakEvenRate -> RateEngine -> Quote), pas l'arithmetique elle-meme.
        BigDecimal expectedCustomerRate = new BigDecimal("87.971572")
                .multiply(new BigDecimal("1.02"), new MathContext(20, RoundingMode.HALF_UP))
                .setScale(6, RoundingMode.HALF_UP);
        assertThat(quote.customerRate()).isEqualByComparingTo(expectedCustomerRate);
    }

    @Test
    void changingMargin_changesCustomerRate_butNeverTheUnderlyingBreakEvenRate() {
        String admin = adminToken();
        publishCostRate(admin, "583", "6.70", "0.01", "1.50", "1000000");
        String user = tokenFor(createUser(RoleCode.USER));

        settingsService.update(SettingKey.DEFAULT_MARGIN_PERCENTAGE, "1", createUser(RoleCode.ADMIN).getId());
        QuoteResponse lowMarginQuote = createQuote(user, new CreateQuoteRequest(QuoteDirection.SEND_XOF,
                new BigDecimal("100000"), null));
        CostRateConfigurationResponse afterFirstQuote = currentCostRateConfiguration(admin);

        settingsService.update(SettingKey.DEFAULT_MARGIN_PERCENTAGE, "5", createUser(RoleCode.ADMIN).getId());
        QuoteResponse highMarginQuote = createQuote(user, new CreateQuoteRequest(QuoteDirection.SEND_XOF,
                new BigDecimal("100000"), null));
        CostRateConfigurationResponse afterSecondQuote = currentCostRateConfiguration(admin);

        // La marge change bien le customerRate expose au client...
        assertThat(highMarginQuote.customerRate()).isGreaterThan(lowMarginQuote.customerRate());
        // ...mais ne touche jamais la configuration de cout (aucune republication entre les deux
        // devis) : c'est bien la meme ligne, le meme breakEvenRate, avant et apres le changement de
        // marge — la marge est une decision purement commerciale, distincte du cout de revient.
        assertThat(afterSecondQuote.id()).isEqualTo(afterFirstQuote.id());
        assertThat(afterSecondQuote.breakEvenRate()).isEqualByComparingTo(afterFirstQuote.breakEvenRate());
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }
}
