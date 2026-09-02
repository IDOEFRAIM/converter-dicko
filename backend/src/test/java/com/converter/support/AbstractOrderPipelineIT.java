package com.converter.support;

import com.converter.common.api.ApiResponse;
import com.converter.order.domain.BeneficiaryType;
import com.converter.order.dto.BeneficiaryRequest;
import com.converter.order.dto.CreateOrderRequest;
import com.converter.order.dto.OrderDetailResponse;
import com.converter.payment.domain.PaymentMethod;
import com.converter.payment.dto.PaymentProofResponse;
import com.converter.payment.dto.PaymentResponse;
import com.converter.payment.dto.SubmitPaymentRequest;
import com.converter.quote.domain.QuoteDirection;
import com.converter.quote.dto.CreateQuoteRequest;
import com.converter.quote.dto.QuoteResponse;
import com.converter.settlement.dto.SettlementResponse;
import com.converter.treasury.domain.Currency;
import com.converter.treasury.dto.TreasuryAccountResponse;
import com.converter.treasury.dto.TreasuryAdjustmentRequest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Socle partage des tests d'integration Order/Payment/Settlement/
 * Treasury : enchaîne devis accepte -> ordre -> paiement -> reglement,
 * en reutilisant les helpers de {@link AbstractRateQuoteIT}.
 */
public abstract class AbstractOrderPipelineIT extends AbstractRateQuoteIT {

    private static final byte[] FAKE_JPEG = {
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 'J', 'F', 'I', 'F',
            0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09
    };

    protected QuoteResponse createAcceptedQuote(String userToken, String amountXof) {
        QuoteResponse quote = createQuote(userToken,
                new CreateQuoteRequest(QuoteDirection.SEND_XOF, new BigDecimal(amountXof), null));
        HttpHeaders headers = auth(userToken);
        ResponseEntity<ApiResponse<QuoteResponse>> accepted = restTemplate.exchange(
                "/api/v1/quotes/" + quote.id() + "/accept", HttpMethod.POST,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<ApiResponse<QuoteResponse>>() {
                });
        // Les valeurs financieres sont identiques (snapshot immuable) ;
        // seul le statut differe — renvoyer la reponse post-acceptation
        // evite qu'un appelant observe encore ACTIVE.
        return accepted.getBody().data();
    }

    protected BeneficiaryRequest alipayBeneficiary() {
        return new BeneficiaryRequest(BeneficiaryType.ALIPAY, "Zhang San", "zhang.san@example.com", null, null);
    }

    protected BeneficiaryRequest bankBeneficiary() {
        return new BeneficiaryRequest(BeneficiaryType.CHINESE_BANK_ACCOUNT, "Li Wei", "6222000000000000",
                "Bank of China", "Shanghai Branch");
    }

    protected ResponseEntity<ApiResponse<OrderDetailResponse>> createOrderRaw(
            String userToken, UUID quoteId, BeneficiaryRequest beneficiary) {
        return restTemplate.exchange("/api/v1/orders", HttpMethod.POST,
                new HttpEntity<>(new CreateOrderRequest(quoteId, beneficiary, "test"), auth(userToken)),
                new ParameterizedTypeReference<ApiResponse<OrderDetailResponse>>() {
                });
    }

    protected OrderDetailResponse createOrder(String userToken, UUID quoteId, BeneficiaryRequest beneficiary) {
        return createOrderRaw(userToken, quoteId, beneficiary).getBody().data();
    }

    protected ResponseEntity<ApiResponse<PaymentResponse>> submitPaymentRaw(
            String userToken, UUID orderId, String receivedAmountXof, String reference) {
        return restTemplate.exchange("/api/v1/orders/" + orderId + "/payments", HttpMethod.POST,
                new HttpEntity<>(new SubmitPaymentRequest(PaymentMethod.MOBILE_MONEY,
                        new BigDecimal(receivedAmountXof), reference, "+2250700000000"), auth(userToken)),
                new ParameterizedTypeReference<ApiResponse<PaymentResponse>>() {
                });
    }

    protected PaymentResponse submitPayment(String userToken, UUID orderId, String receivedAmountXof, String reference) {
        return submitPaymentRaw(userToken, orderId, receivedAmountXof, reference).getBody().data();
    }

    protected ResponseEntity<ApiResponse<PaymentProofResponse>> uploadProofRaw(String userToken, UUID paymentId) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new org.springframework.core.io.ByteArrayResource(FAKE_JPEG) {
            @Override
            public String getFilename() {
                return "proof.jpg";
            }
        });
        HttpHeaders headers = auth(userToken);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return restTemplate.exchange("/api/v1/payments/" + paymentId + "/proofs", HttpMethod.POST,
                new HttpEntity<>(body, headers),
                new ParameterizedTypeReference<ApiResponse<PaymentProofResponse>>() {
                });
    }

    protected PaymentProofResponse uploadProof(String userToken, UUID paymentId) {
        return uploadProofRaw(userToken, paymentId).getBody().data();
    }

    protected ResponseEntity<ApiResponse<PaymentResponse>> confirmPaymentRaw(String adminToken, UUID paymentId) {
        return restTemplate.exchange("/api/admin/payments/" + paymentId + "/confirm", HttpMethod.POST,
                new HttpEntity<>(auth(adminToken)),
                new ParameterizedTypeReference<ApiResponse<PaymentResponse>>() {
                });
    }

    protected PaymentResponse confirmPayment(String adminToken, UUID paymentId) {
        return confirmPaymentRaw(adminToken, paymentId).getBody().data();
    }

    protected ResponseEntity<ApiResponse<SettlementResponse>> createSettlementRaw(String adminToken, UUID orderId) {
        return restTemplate.exchange("/api/admin/orders/" + orderId + "/settlement", HttpMethod.POST,
                new HttpEntity<>(auth(adminToken)),
                new ParameterizedTypeReference<ApiResponse<SettlementResponse>>() {
                });
    }

    protected SettlementResponse createSettlement(String adminToken, UUID orderId) {
        return createSettlementRaw(adminToken, orderId).getBody().data();
    }

    protected ResponseEntity<ApiResponse<SettlementResponse>> executeSettlementRaw(
            String adminToken, UUID settlementId, String reference) {
        String body = "{\"settlementReference\":\"" + reference + "\",\"notes\":\"test\"}";
        HttpHeaders headers = auth(adminToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange("/api/admin/settlements/" + settlementId + "/execute", HttpMethod.POST,
                new HttpEntity<>(body, headers),
                new ParameterizedTypeReference<ApiResponse<SettlementResponse>>() {
                });
    }

    protected SettlementResponse executeSettlement(String adminToken, UUID settlementId, String reference) {
        return executeSettlementRaw(adminToken, settlementId, reference).getBody().data();
    }

    protected TreasuryAccountResponse depositCny(String adminToken, String amount) {
        return depositTreasury(adminToken, Currency.CNY, amount);
    }

    protected TreasuryAccountResponse depositXof(String adminToken, String amount) {
        return depositTreasury(adminToken, Currency.XOF, amount);
    }

    protected TreasuryAccountResponse depositTreasury(String adminToken, Currency currency, String amount) {
        ResponseEntity<ApiResponse<TreasuryAccountResponse>> response = restTemplate.exchange(
                "/api/admin/treasury/deposit", HttpMethod.POST,
                new HttpEntity<>(new TreasuryAdjustmentRequest(currency, new BigDecimal(amount), "test seed"),
                        auth(adminToken)),
                new ParameterizedTypeReference<ApiResponse<TreasuryAccountResponse>>() {
                });
        return response.getBody().data();
    }

    protected TreasuryAccountResponse treasurySnapshot(String adminToken, Currency currency) {
        ResponseEntity<ApiResponse<TreasuryAccountResponse>> response = restTemplate.exchange(
                "/api/admin/treasury/accounts/" + currency, HttpMethod.GET,
                new HttpEntity<>(auth(adminToken)),
                new ParameterizedTypeReference<ApiResponse<TreasuryAccountResponse>>() {
                });
        return response.getBody().data();
    }

    protected HttpHeaders auth(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }
}
