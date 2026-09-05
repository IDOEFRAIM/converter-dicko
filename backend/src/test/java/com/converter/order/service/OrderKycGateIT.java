package com.converter.order.service;

import com.converter.common.api.ErrorResponse;
import com.converter.order.dto.CreateOrderRequest;
import com.converter.order.dto.OrderDetailResponse;
import com.converter.quote.dto.QuoteResponse;
import com.converter.settings.domain.SettingKey;
import com.converter.support.AbstractOrderPipelineIT;
import com.converter.user.domain.RoleCode;
import com.converter.user.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KYC obligatoire (inclus) a partir de {@code SettingKey.KYC_REQUIRED_THRESHOLD_XOF} pour creer
 * un ordre — verification minimale (drapeau administrateur), jamais un moteur de conformite
 * complet. Voir {@code OrderService#assertKycVerifiedIfRequired}.
 */
class OrderKycGateIT extends AbstractOrderPipelineIT {

    private ResponseEntity<ErrorResponse> createOrderRaw(String userToken, java.util.UUID quoteId) {
        return restTemplate.exchange("/api/v1/orders", HttpMethod.POST,
                new HttpEntity<>(new CreateOrderRequest(quoteId, alipayBeneficiary(), null, null, null, null),
                        auth(userToken)),
                ErrorResponse.class);
    }

    @Test
    void createOrder_belowKycThreshold_neverRequiresVerification() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));

        // Seuil par defaut (V27) = 300 000 XOF ; ce montant reste strictement en dessous.
        QuoteResponse quote = createAcceptedQuote(user, "100000");
        OrderDetailResponse order = createOrder(user, quote.id(), alipayBeneficiary());

        assertThat(order.amountXof()).isEqualByComparingTo("100000");
    }

    @Test
    void createOrder_atOrAboveThreshold_unverifiedUser_returnsKycRequired() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "5000000");
        String user = tokenFor(createUnverifiedUser(RoleCode.USER));

        QuoteResponse quote = createAcceptedQuote(user, "300000");

        ResponseEntity<ErrorResponse> response = createOrderRaw(user, quote.id());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).isEqualTo("KYC_VERIFICATION_REQUIRED");
    }

    @Test
    void createOrder_exactlyAtThreshold_isTreatedAsRequiringKyc() {
        // "A partir de 300 000" = seuil inclus, jamais strictement superieur.
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "5000000");
        String user = tokenFor(createUnverifiedUser(RoleCode.USER));
        QuoteResponse quote = createAcceptedQuote(user, "300000");
        assertThat(quote.amountXof()).isEqualByComparingTo("300000");

        ResponseEntity<ErrorResponse> response = createOrderRaw(user, quote.id());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).isEqualTo("KYC_VERIFICATION_REQUIRED");
    }

    @Test
    void createOrder_justBelowThreshold_neverRequiresKyc() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "5000000");
        String user = tokenFor(createUser(RoleCode.USER));

        QuoteResponse quote = createAcceptedQuote(user, "299999");

        OrderDetailResponse order = createOrder(user, quote.id(), alipayBeneficiary());

        assertThat(order.amountXof()).isEqualByComparingTo("299999");
    }

    @Test
    void createOrder_atOrAboveThreshold_verifiedUser_succeeds() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "5000000");
        // createUser(...) verifie deja le KYC par defaut (voir AbstractRateQuoteIT) : suffisant ici.
        String user = tokenFor(createUser(RoleCode.USER));

        QuoteResponse quote = createAcceptedQuote(user, "400000");
        OrderDetailResponse order = createOrder(user, quote.id(), alipayBeneficiary());

        assertThat(order.amountXof()).isEqualByComparingTo("400000");
    }

    @Test
    void createOrder_thresholdIsConfigurable_viaSettings() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        settingsService.update(SettingKey.KYC_REQUIRED_THRESHOLD_XOF, "50000", createUser(RoleCode.ADMIN).getId());
        String user = tokenFor(createUnverifiedUser(RoleCode.USER));

        // 60 000 XOF est en-dessous du seuil par defaut (300 000) mais au-dessus du nouveau seuil (50 000).
        QuoteResponse quote = createAcceptedQuote(user, "60000");

        ResponseEntity<ErrorResponse> response = createOrderRaw(user, quote.id());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).isEqualTo("KYC_VERIFICATION_REQUIRED");
    }

    @Test
    void createOrder_revokedKyc_isTreatedAsUnverifiedAgain() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "5000000");
        User userEntity = verifyKyc(createUser(RoleCode.USER));
        userEntity.revokeKyc();
        userEntity = userRepository.saveAndFlush(userEntity);
        String user = tokenFor(userEntity);

        QuoteResponse quote = createAcceptedQuote(user, "350000");

        ResponseEntity<ErrorResponse> response = createOrderRaw(user, quote.id());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).isEqualTo("KYC_VERIFICATION_REQUIRED");
    }
}
