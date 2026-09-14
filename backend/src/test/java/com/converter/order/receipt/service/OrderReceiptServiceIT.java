package com.converter.order.receipt.service;

import com.converter.common.api.ApiResponse;
import com.converter.order.domain.BeneficiaryType;
import com.converter.order.dto.OrderDetailResponse;
import com.converter.order.receipt.model.ReceiptDocument;
import com.converter.order.receipt.pdf.PdfBoxReceiptPdfGenerator;
import com.converter.quote.dto.QuoteResponse;
import com.converter.refund.dto.RefundResponse;
import com.converter.settings.domain.SettingKey;
import com.converter.supplier.dto.CreateSupplierRequest;
import com.converter.supplier.dto.SupplierDetailResponse;
import com.converter.supplier.dto.UpdateSupplierRequest;
import com.converter.supplier.domain.Purpose;
import com.converter.support.AbstractOrderPipelineIT;
import com.converter.supplier.service.SupplierService;
import com.converter.treasury.domain.Currency;
import com.converter.user.domain.RoleCode;
import com.converter.user.domain.User;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests critiques du justificatif (Phase 7) : aucun recalcul de pricing (section 33), snapshot du
 * beneficiaire jamais recompose depuis le {@code Supplier} courant (section 34), et gestion fidele
 * d'un {@code Refund} (section 35), y compris {@code REJECTED}.
 */
class OrderReceiptServiceIT extends AbstractOrderPipelineIT {

    @Autowired
    private OrderReceiptService orderReceiptService;

    @Autowired
    private SupplierService supplierService;

    private String extractText(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    /**
     * Section 33 — TEST CRITIQUE NO REPRICING : un changement de marge et une nouvelle
     * publication de taux APRES la completion de l'ordre ne doivent jamais apparaitre dans un
     * justificatif genere ensuite -- uniquement les valeurs figees au moment de l'ordre.
     */
    @Test
    void receipt_neverReflectsPricingChangesMadeAfterCompletion() throws IOException {
        resetMarginToZero();
        String admin = adminToken();
        publishRate(admin, "84.200000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));

        QuoteResponse quote = createAcceptedQuote(user, "1000000");
        OrderDetailResponse order = createOrder(user, quote.id(), bankBeneficiary());
        UUID orderId = completeOrder(admin, user, order.id(), order.amountXof().toPlainString(),
                "MM-NOREPRICE-001", "CNY-NOREPRICE-001");

        BigDecimal originalRate = order.customerRate();
        BigDecimal originalFee = order.feeXof();
        BigDecimal originalAmountCny = order.amountCny();
        assertThat(originalRate).isEqualByComparingTo("84.200000");

        // Pricing change APRES completion : marge et taux courants totalement differents.
        settingsService.update(SettingKey.DEFAULT_MARGIN_PERCENTAGE, "10", createUser(RoleCode.ADMIN).getId());
        publishRate(admin, "150.000000");

        ReceiptDocument document = orderReceiptService.generate(orderId, extractUserId(user));
        String text = extractText(document.content());

        assertThat(text).contains(PdfBoxReceiptPdfGenerator.formatAmount(originalRate));
        assertThat(text).contains(PdfBoxReceiptPdfGenerator.formatAmount(originalFee));
        assertThat(text).contains(PdfBoxReceiptPdfGenerator.formatAmount(originalAmountCny));
        assertThat(text).doesNotContain("150.00");
    }

    /**
     * Section 34 — TEST CRITIQUE SUPPLIER MUTATION : le beneficiaire affiche reste celui du
     * snapshot {@code Beneficiary} pris a la creation de l'ordre, jamais le {@code Supplier}
     * modifie apres coup.
     */
    @Test
    void receipt_usesBeneficiarySnapshot_neverCurrentSupplierAfterMutation() throws IOException {
        resetMarginToZero();
        String admin = adminToken();
        publishRate(admin, "84.200000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));

        SupplierDetailResponse supplier = createSupplier(user, "Bank A", "1111000011110000");
        QuoteResponse quote = createAcceptedQuote(user, "1000000");
        OrderDetailResponse order = createOrderRawWithSupplier(user, quote.id(), supplier.id()).getBody().data();
        UUID orderId = completeOrder(admin, user, order.id(), order.amountXof().toPlainString(),
                "MM-SUPPLIERMUT-001", "CNY-SUPPLIERMUT-001");

        // Le fournisseur enregistre est modifie APRES que l'ordre a ete cree (et complete).
        updateSupplier(user, supplier.id(), "Bank B", "2222000022220000");

        ReceiptDocument document = orderReceiptService.generate(orderId, extractUserId(user));
        String text = extractText(document.content());

        assertThat(text).contains("Bank A");
        assertThat(text).contains("******0000"); // masque, 4 derniers de 1111000011110000
        assertThat(text).doesNotContain("Bank B");
        assertThat(text).doesNotContain("2222000022220000");
        assertThat(text).doesNotContain("1111000011110000"); // jamais en clair, meme la valeur d'origine
    }

    /**
     * Section 35 — remboursement traite : la section REMBOURSEMENT apparait, le transfert initial
     * reste "COMPLETED", jamais requalifie en annule.
     */
    @Test
    void receipt_withProcessedRefund_showsRefundSection_orderStatusStaysCompleted() throws IOException {
        resetMarginToZero();
        String admin = adminToken();
        publishRate(admin, "84.200000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));

        QuoteResponse quote = createAcceptedQuote(user, "1000000");
        OrderDetailResponse order = createOrder(user, quote.id(), bankBeneficiary());
        UUID orderId = completeOrder(admin, user, order.id(), order.amountXof().toPlainString(),
                "MM-REFUND-RECEIPT-001", "CNY-REFUND-RECEIPT-001");

        var paymentResponse = restTemplate.exchange("/api/v1/orders/" + orderId, HttpMethod.GET,
                new HttpEntity<>(auth(user)),
                new ParameterizedTypeReference<ApiResponse<OrderDetailResponse>>() {
                }).getBody().data();
        assertThat(paymentResponse.status().name()).isEqualTo("COMPLETED");

        UUID paymentId = findPaymentId(admin, orderId);
        RefundResponse refund = createRefund(admin, paymentId, "Remboursement test justificatif");
        RefundResponse processed = processRefund(admin, refund.id(), "XOF-REFUND-RECEIPT-001");

        ReceiptDocument document = orderReceiptService.generate(orderId, extractUserId(user));
        String text = extractText(document.content());

        assertThat(text).contains("REMBOURSEMENT");
        assertThat(text).contains("Traite");
        assertThat(text).contains("XOF-REFUND-RECEIPT-001");

        OrderDetailResponse reloaded = restTemplate.exchange("/api/v1/orders/" + orderId, HttpMethod.GET,
                new HttpEntity<>(auth(user)),
                new ParameterizedTypeReference<ApiResponse<OrderDetailResponse>>() {
                }).getBody().data();
        assertThat(reloaded.status().name()).isEqualTo("COMPLETED");
        assertThat(processed.status().name()).isEqualTo("PROCESSED");
    }

    /**
     * Section 35 — remboursement rejete : le justificatif doit representer fidelement l'etat
     * REJECTED, jamais le cacher ni pretendre qu'aucun remboursement n'a ete tente.
     */
    @Test
    void receipt_withRejectedRefund_representsRejectionFaithfully() throws IOException {
        resetMarginToZero();
        String admin = adminToken();
        publishRate(admin, "84.200000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));

        QuoteResponse quote = createAcceptedQuote(user, "1000000");
        OrderDetailResponse order = createOrder(user, quote.id(), bankBeneficiary());
        UUID orderId = completeOrder(admin, user, order.id(), order.amountXof().toPlainString(),
                "MM-REFUND-REJ-001", "CNY-REFUND-REJ-001");

        UUID paymentId = findPaymentId(admin, orderId);
        RefundResponse refund = createRefund(admin, paymentId, "Demande refusee test");
        rejectRefundRaw(admin, refund.id(), "Preuve insuffisante");

        ReceiptDocument document = orderReceiptService.generate(orderId, extractUserId(user));
        String text = extractText(document.content());

        assertThat(text).contains("REMBOURSEMENT");
        assertThat(text).contains("Rejete");
    }

    /**
     * Regression reelle (retour client, ordres deployes en production) : un beneficiaire
     * ALIPAY/WECHAT_PAY identifie par un code QR (V36/V38) n'a pas d'identifiant texte --
     * {@code Beneficiary#identifier} est {@code null}, exactement le cas des 3 ordres reels
     * bloques par le bug de reglement corrige plus haut dans cette meme session, qui viennent
     * eux-memes d'etre completes. Le justificatif doit se generer normalement (voir
     * OrderReceiptService#mask, deja null-safe) pour ce chemin, le seul reel en production pour
     * Alipay/WeChat -- jamais lever d'exception ni bloquer le telechargement.
     */
    @Test
    void receipt_forSupplierBeneficiaryIdentifiedByQrCodeWithoutTextIdentifier_neverThrows() throws IOException {
        resetMarginToZero();
        String admin = adminToken();
        publishRate(admin, "84.200000");
        depositCny(admin, "1000000");
        User userEntity = createUser(RoleCode.USER);
        String user = tokenFor(userEntity);

        CreateSupplierRequest request = new CreateSupplierRequest(BeneficiaryType.ALIPAY, "QR Only Supplier",
                null, null, null, "China", "Shenzhen", null, null, null, null, null, null, null,
                Currency.CNY, Purpose.IMPORT_GOODS, null);
        SupplierDetailResponse supplier = supplierService.create(request, userEntity.getId());
        supplierService.attachQrCode(supplier.id(), "qr.png", "image/png", REAL_QR_CODE_PNG, userEntity.getId());

        QuoteResponse quote = createAcceptedQuote(user, "500000");
        OrderDetailResponse order = createOrderWithSupplierRaw(user, quote.id(), supplier.id()).getBody().data();
        assertThat(order.beneficiary().identifier()).isNull();
        UUID orderId = completeOrder(admin, user, order.id(), order.amountXof().toPlainString(),
                "MM-QR-RECEIPT-001", "CNY-QR-RECEIPT-001");

        ReceiptDocument document = orderReceiptService.generate(orderId, extractUserId(user));
        String text = extractText(document.content());

        assertThat(text).contains("QR Only Supplier");
    }

    // ---- Utilitaires locaux ----

    private SupplierDetailResponse createSupplier(String userToken, String bankName, String accountNumber) {
        var response = restTemplate.exchange("/api/v1/suppliers", HttpMethod.POST,
                new HttpEntity<>(new CreateSupplierRequest(BeneficiaryType.CHINESE_BANK_ACCOUNT, "Fournisseur test",
                        null, null, null, "China", "Shanghai", null, bankName, "Branch 1", "Zhang San",
                        accountNumber, null, null, Currency.CNY, Purpose.IMPORT_GOODS, null), auth(userToken)),
                new ParameterizedTypeReference<ApiResponse<SupplierDetailResponse>>() {
                });
        return response.getBody().data();
    }

    private void updateSupplier(String userToken, UUID supplierId, String bankName, String accountNumber) {
        restTemplate.exchange("/api/v1/suppliers/" + supplierId, HttpMethod.PUT,
                new HttpEntity<>(new UpdateSupplierRequest(BeneficiaryType.CHINESE_BANK_ACCOUNT, "Fournisseur test",
                        null, null, null, "China", "Shanghai", null, bankName, "Branch 2", "Zhang San",
                        accountNumber, null, null, Currency.CNY, Purpose.IMPORT_GOODS, null), auth(userToken)),
                new ParameterizedTypeReference<ApiResponse<SupplierDetailResponse>>() {
                });
    }

    private com.converter.order.dto.CreateOrderRequest supplierOrderRequest(UUID quoteId, UUID supplierId) {
        return new com.converter.order.dto.CreateOrderRequest(quoteId, null, "test", supplierId, null, null, null);
    }

    private org.springframework.http.ResponseEntity<ApiResponse<OrderDetailResponse>> createOrderRawWithSupplier(
            String userToken, UUID quoteId, UUID supplierId) {
        return restTemplate.exchange("/api/v1/orders", HttpMethod.POST,
                new HttpEntity<>(supplierOrderRequest(quoteId, supplierId), auth(userToken)),
                new ParameterizedTypeReference<ApiResponse<OrderDetailResponse>>() {
                });
    }

    @Autowired
    private com.converter.payment.repository.PaymentRepository paymentRepository;

    private UUID findPaymentId(String adminToken, UUID orderId) {
        return paymentRepository.findByOrderId(orderId).orElseThrow().getId();
    }

    private UUID extractUserId(String userToken) {
        return jwtService.parse(userToken).userId();
    }
}
