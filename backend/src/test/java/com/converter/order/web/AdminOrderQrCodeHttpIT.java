package com.converter.order.web;

import com.converter.common.api.ApiResponse;
import com.converter.order.domain.BeneficiaryType;
import com.converter.order.dto.OrderDetailResponse;
import com.converter.quote.dto.QuoteResponse;
import com.converter.supplier.domain.Purpose;
import com.converter.supplier.dto.CreateSupplierRequest;
import com.converter.supplier.dto.SupplierDetailResponse;
import com.converter.supplier.service.SupplierService;
import com.converter.support.AbstractOrderPipelineIT;
import com.converter.treasury.domain.Currency;
import com.converter.user.domain.RoleCode;
import com.converter.user.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code GET /api/admin/orders/{id}/beneficiary/qr-code} — retour client : "cote admin, on doit
 * pouvoir voir les fournisseurs de chaque user, c'est ca qui permet de pouvoir faire les
 * transferts". Sans cet endpoint, l'admin n'a litteralement aucun moyen de savoir ou envoyer les
 * fonds pour un beneficiaire Alipay/WeChat (son identifiant texte, seul champ visible jusque-la
 * sur le reglement, est toujours vide pour ce type -- voir V38).
 */
class AdminOrderQrCodeHttpIT extends AbstractOrderPipelineIT {

    @Autowired
    private SupplierService supplierService;

    @Test
    void beneficiaryQrCode_forAlipaySupplierBeneficiary_returnsTheRealQrCodeImage() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        User userEntity = createUser(RoleCode.USER);
        String user = tokenFor(userEntity);

        CreateSupplierRequest request = new CreateSupplierRequest(BeneficiaryType.ALIPAY, "QR Only Supplier",
                null, null, null, "China", "Shenzhen", null, null, null, null, null, null, null,
                Currency.CNY, Purpose.IMPORT_GOODS, null);
        SupplierDetailResponse supplier = supplierService.create(request, userEntity.getId());
        supplierService.attachQrCode(supplier.id(), "qr.png", "image/png", REAL_QR_CODE_PNG, userEntity.getId());

        QuoteResponse quote = createAcceptedQuote(user, "100000");
        OrderDetailResponse order = createOrderWithSupplierRaw(user, quote.id(), supplier.id()).getBody().data();

        ResponseEntity<byte[]> response = restTemplate.exchange(
                "/api/admin/orders/" + order.id() + "/beneficiary/qr-code", HttpMethod.GET,
                new HttpEntity<>(auth(admin)), byte[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(response.getBody()).isEqualTo(REAL_QR_CODE_PNG);
    }

    @Test
    void beneficiaryQrCode_forBankAccountBeneficiary_returns404() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));
        QuoteResponse quote = createAcceptedQuote(user, "100000");
        OrderDetailResponse order = createOrder(user, quote.id(), bankBeneficiary());

        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                "/api/admin/orders/" + order.id() + "/beneficiary/qr-code", HttpMethod.GET,
                new HttpEntity<>(auth(admin)), new ParameterizedTypeReference<ApiResponse<Void>>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void beneficiaryQrCode_forUnknownOrder_returns404() {
        String admin = adminToken();

        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                "/api/admin/orders/" + UUID.randomUUID() + "/beneficiary/qr-code", HttpMethod.GET,
                new HttpEntity<>(auth(admin)), new ParameterizedTypeReference<ApiResponse<Void>>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void beneficiaryQrCode_asNonAdmin_isForbidden() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));
        QuoteResponse quote = createAcceptedQuote(user, "100000");
        OrderDetailResponse order = createOrder(user, quote.id(), bankBeneficiary());

        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                "/api/admin/orders/" + order.id() + "/beneficiary/qr-code", HttpMethod.GET,
                new HttpEntity<>(auth(user)), new ParameterizedTypeReference<ApiResponse<Void>>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
