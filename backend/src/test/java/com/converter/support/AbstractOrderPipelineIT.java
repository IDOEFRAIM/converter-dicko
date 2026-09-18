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
import com.converter.refund.dto.CreateRefundRequest;
import com.converter.refund.dto.ProcessRefundRequest;
import com.converter.refund.dto.RefundResponse;
import com.converter.refund.dto.RejectRefundRequest;
import com.converter.settlement.dto.SettlementResponse;
import com.converter.treasury.domain.Currency;
import com.converter.treasury.dto.TreasuryAccountResponse;
import com.converter.treasury.dto.TreasuryAdjustmentRequest;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Socle partage des tests d'integration Order/Payment/Settlement/
 * Treasury : enchaîne devis accepte -> ordre -> paiement -> reglement,
 * en reutilisant les helpers de {@link AbstractRateQuoteIT}.
 */
public abstract class AbstractOrderPipelineIT extends AbstractRateQuoteIT {

    protected static final byte[] FAKE_JPEG = {
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 'J', 'F', 'I', 'F',
            0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09
    };

    /**
     * Un vrai code QR PNG, genere par ZXing lui-meme (jamais une simple image bidon) : contrairement
     * a {@link #FAKE_JPEG} (magic bytes suffisants pour {@code FileValidator}), {@code
     * QrCodeValidator} decode reellement le contenu -- il faut un fichier qui contienne un
     * veritable code QR pour passer cette verification dans les tests.
     */
    protected static final byte[] REAL_QR_CODE_PNG = generateQrCodePng("alipay://example/pay?id=test-supplier");

    /** Une vraie image PNG (decodable), mais SANS aucun code QR -- pour verifier le rejet. */
    protected static final byte[] REAL_NON_QR_IMAGE_PNG = generatePlainPng();

    private static byte[] generateQrCodePng(String content) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 200, 200);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (WriterException | IOException e) {
            throw new IllegalStateException("Echec de generation du code QR de test", e);
        }
    }

    private static byte[] generatePlainPng() {
        try {
            BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
            for (int x = 0; x < image.getWidth(); x++) {
                for (int y = 0; y < image.getHeight(); y++) {
                    image.setRGB(x, y, 0x336699);
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Echec de generation de l'image de test", e);
        }
    }

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
                new HttpEntity<>(new CreateOrderRequest(quoteId, beneficiary, "test", null, null, null, null), auth(userToken)),
                new ParameterizedTypeReference<ApiResponse<OrderDetailResponse>>() {
                });
    }

    protected OrderDetailResponse createOrder(String userToken, UUID quoteId, BeneficiaryRequest beneficiary) {
        return createOrderRaw(userToken, quoteId, beneficiary).getBody().data();
    }

    protected ResponseEntity<ApiResponse<OrderDetailResponse>> createOrderWithSupplierRaw(
            String userToken, UUID quoteId, UUID supplierId) {
        return restTemplate.exchange("/api/v1/orders", HttpMethod.POST,
                new HttpEntity<>(new CreateOrderRequest(quoteId, null, "test", supplierId, null, null, null),
                        auth(userToken)),
                new ParameterizedTypeReference<ApiResponse<OrderDetailResponse>>() {
                });
    }

    protected ResponseEntity<ApiResponse<com.converter.order.dto.OrderTrackingResponse>> trackingRaw(
            String userToken, UUID orderId) {
        return restTemplate.exchange("/api/v1/orders/" + orderId + "/tracking", HttpMethod.GET,
                new HttpEntity<>(auth(userToken)),
                new ParameterizedTypeReference<ApiResponse<com.converter.order.dto.OrderTrackingResponse>>() {
                });
    }

    protected ResponseEntity<ApiResponse<PaymentResponse>> submitPaymentRaw(
            String userToken, UUID orderId, String receivedAmountXof) {
        return restTemplate.exchange("/api/v1/orders/" + orderId + "/payments", HttpMethod.POST,
                new HttpEntity<>(new SubmitPaymentRequest(PaymentMethod.MOBILE_MONEY,
                        new BigDecimal(receivedAmountXof), "+2250700000000", "Payeur Test"),
                        auth(userToken)),
                new ParameterizedTypeReference<ApiResponse<PaymentResponse>>() {
                });
    }

    protected PaymentResponse submitPayment(String userToken, UUID orderId, String receivedAmountXof) {
        return submitPaymentRaw(userToken, orderId, receivedAmountXof).getBody().data();
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

    protected ResponseEntity<ApiResponse<PaymentResponse>> rejectPaymentRaw(
            String adminToken, UUID paymentId, String reason) {
        return restTemplate.exchange("/api/admin/payments/" + paymentId + "/reject", HttpMethod.POST,
                new HttpEntity<>(new com.converter.payment.dto.RejectPaymentRequest(reason), auth(adminToken)),
                new ParameterizedTypeReference<ApiResponse<PaymentResponse>>() {
                });
    }

    protected PaymentResponse rejectPayment(String adminToken, UUID paymentId, String reason) {
        return rejectPaymentRaw(adminToken, paymentId, reason).getBody().data();
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

    protected void uploadSettlementProof(String adminToken, UUID settlementId) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new org.springframework.core.io.ByteArrayResource(FAKE_JPEG) {
            @Override
            public String getFilename() {
                return "settlement-proof.jpg";
            }
        });
        HttpHeaders headers = auth(adminToken);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        restTemplate.exchange("/api/admin/settlements/" + settlementId + "/proofs", HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
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

    protected ResponseEntity<ApiResponse<RefundResponse>> createRefundRaw(
            String adminToken, UUID paymentId, String reason, String idempotencyKey) {
        HttpHeaders headers = auth(adminToken);
        if (idempotencyKey != null) {
            headers.set("Idempotency-Key", idempotencyKey);
        }
        return restTemplate.exchange("/api/admin/payments/" + paymentId + "/refunds", HttpMethod.POST,
                new HttpEntity<>(new CreateRefundRequest(reason), headers),
                new ParameterizedTypeReference<ApiResponse<RefundResponse>>() {
                });
    }

    protected RefundResponse createRefund(String adminToken, UUID paymentId, String reason) {
        return createRefundRaw(adminToken, paymentId, reason, null).getBody().data();
    }

    protected ResponseEntity<ApiResponse<RefundResponse>> processRefundRaw(
            String adminToken, UUID refundId, String transactionReference, String idempotencyKey) {
        HttpHeaders headers = auth(adminToken);
        if (idempotencyKey != null) {
            headers.set("Idempotency-Key", idempotencyKey);
        }
        return restTemplate.exchange("/api/admin/refunds/" + refundId + "/process", HttpMethod.POST,
                new HttpEntity<>(new ProcessRefundRequest(transactionReference), headers),
                new ParameterizedTypeReference<ApiResponse<RefundResponse>>() {
                });
    }

    protected RefundResponse processRefund(String adminToken, UUID refundId, String transactionReference) {
        return processRefundRaw(adminToken, refundId, transactionReference, null).getBody().data();
    }

    protected ResponseEntity<ApiResponse<RefundResponse>> rejectRefundRaw(
            String adminToken, UUID refundId, String reason) {
        return restTemplate.exchange("/api/admin/refunds/" + refundId + "/reject", HttpMethod.POST,
                new HttpEntity<>(new RejectRefundRequest(reason), auth(adminToken)),
                new ParameterizedTypeReference<ApiResponse<RefundResponse>>() {
                });
    }

    protected RefundResponse getRefund(String adminToken, UUID refundId) {
        ResponseEntity<ApiResponse<RefundResponse>> response = restTemplate.exchange(
                "/api/admin/refunds/" + refundId, HttpMethod.GET, new HttpEntity<>(auth(adminToken)),
                new ParameterizedTypeReference<ApiResponse<RefundResponse>>() {
                });
        return response.getBody().data();
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

    protected TreasuryAccountResponse adjustTreasury(String adminToken, Currency currency, String delta, String reason) {
        ResponseEntity<ApiResponse<TreasuryAccountResponse>> response = restTemplate.exchange(
                "/api/admin/treasury/adjust", HttpMethod.POST,
                new HttpEntity<>(new TreasuryAdjustmentRequest(currency, new BigDecimal(delta), reason),
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

    /**
     * Fait progresser un ordre deja cree jusqu'a {@code COMPLETED} : paiement declare, preuve
     * televersee, paiement confirme, reglement cree, preuve de reglement televersee, reglement
     * execute. Compose avec {@link #createOrder}/{@code createOrderWithSupplierRaw} (l'appelant
     * fournit l'{@code orderId} deja obtenu) — reutilise par les tests du justificatif (Phase 7).
     */
    protected UUID completeOrder(String adminToken, String userToken, UUID orderId, String amountXof,
                                 String settlementReference) {
        PaymentResponse payment = submitPayment(userToken, orderId, amountXof);
        uploadProof(userToken, payment.id());
        confirmPayment(adminToken, payment.id());
        SettlementResponse settlement = createSettlement(adminToken, orderId);
        uploadSettlementProof(adminToken, settlement.id());
        executeSettlement(adminToken, settlement.id(), settlementReference);
        return orderId;
    }

    protected HttpHeaders auth(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }
}
