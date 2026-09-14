package com.converter.supplier.web;

import com.converter.common.api.ApiResponse;
import com.converter.common.api.ErrorResponse;
import com.converter.order.domain.BeneficiaryType;
import com.converter.order.dto.OrderDetailResponse;
import com.converter.support.AbstractOrderPipelineIT;
import com.converter.supplier.domain.Purpose;
import com.converter.supplier.dto.CreateSupplierRequest;
import com.converter.supplier.dto.PayAgainRequest;
import com.converter.supplier.dto.SupplierDetailResponse;
import com.converter.supplier.service.SupplierService;
import com.converter.treasury.domain.Currency;
import com.converter.user.domain.RoleCode;
import com.converter.user.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code POST /api/v1/suppliers/{id}/pay-again} — securite HTTP complete (auth, ownership) et
 * idempotence, meme convention que {@code POST /api/v1/orders} (voir {@code OrderController}).
 */
class SupplierPayAgainHttpIT extends AbstractOrderPipelineIT {

    @Autowired
    private SupplierService supplierService;

    private String setUpAdminWithLiquidity() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        return admin;
    }

    private SupplierDetailResponse createActiveSupplier(UUID ownerUserId) {
        CreateSupplierRequest request = new CreateSupplierRequest(BeneficiaryType.ALIPAY,
                "Guangzhou Electronics Ltd.", null, null, null, "China", "Guangzhou", null, null, null, null,
                "alipay-http-" + UUID.randomUUID(), null, null, Currency.CNY, Purpose.IMPORT_GOODS, null);
        SupplierDetailResponse created = supplierService.create(request, ownerUserId);
        // ALIPAY ne peut plus creer d'ordre sans code QR televerse (Supplier#isReadyForPayment).
        return supplierService.attachQrCode(created.id(), "qr.png", "image/png", REAL_QR_CODE_PNG, ownerUserId);
    }

    private ResponseEntity<ApiResponse<OrderDetailResponse>> payAgainRaw(
            String userToken, UUID supplierId, PayAgainRequest request, String idempotencyKey) {
        HttpHeaders headers = auth(userToken);
        if (idempotencyKey != null) {
            headers.set("Idempotency-Key", idempotencyKey);
        }
        return restTemplate.exchange("/api/v1/suppliers/" + supplierId + "/pay-again", HttpMethod.POST,
                new HttpEntity<>(request, headers),
                new ParameterizedTypeReference<ApiResponse<OrderDetailResponse>>() {
                });
    }

    // ---- HTTP : 201 owner, 401 anonymous, 404 other user, 404 unknown ----

    @Test
    void payAgain_authenticatedOwner_returns201() {
        setUpAdminWithLiquidity();
        User userEntity = createUser(RoleCode.USER);
        String user = tokenFor(userEntity);
        SupplierDetailResponse supplier = createActiveSupplier(userEntity.getId());

        ResponseEntity<ApiResponse<OrderDetailResponse>> response = payAgainRaw(user, supplier.id(),
                new PayAgainRequest(new BigDecimal("100000"), null, null), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().data().supplierId()).isEqualTo(supplier.id());
    }

    @Test
    void payAgain_anonymous_returns401() {
        setUpAdminWithLiquidity();
        SupplierDetailResponse supplier = createActiveSupplier(createUser(RoleCode.USER).getId());
        PayAgainRequest request = new PayAgainRequest(new BigDecimal("100000"), null, null);

        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                "/api/v1/suppliers/" + supplier.id() + "/pay-again", request, ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void payAgain_anotherUsersSupplier_returns404NotForbidden() {
        setUpAdminWithLiquidity();
        User owner = createUser(RoleCode.USER);
        SupplierDetailResponse supplier = createActiveSupplier(owner.getId());
        String intruder = tokenFor(createUser(RoleCode.USER));

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/v1/suppliers/" + supplier.id() + "/pay-again", HttpMethod.POST,
                new HttpEntity<>(new PayAgainRequest(new BigDecimal("100000"), null, null), auth(intruder)),
                ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo("SUPPLIER_NOT_FOUND");
    }

    @Test
    void payAgain_unknownSupplier_returns404() {
        String user = tokenFor(createUser(RoleCode.USER));

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/v1/suppliers/" + UUID.randomUUID() + "/pay-again", HttpMethod.POST,
                new HttpEntity<>(new PayAgainRequest(new BigDecimal("100000"), null, null), auth(user)),
                ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo("SUPPLIER_NOT_FOUND");
    }

    @Test
    void payAgain_deactivatedSupplier_returnsBusinessConflict() {
        setUpAdminWithLiquidity();
        User userEntity = createUser(RoleCode.USER);
        String user = tokenFor(userEntity);
        SupplierDetailResponse supplier = createActiveSupplier(userEntity.getId());
        supplierService.deactivate(supplier.id(), userEntity.getId());

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/v1/suppliers/" + supplier.id() + "/pay-again", HttpMethod.POST,
                new HttpEntity<>(new PayAgainRequest(new BigDecimal("100000"), null, null), auth(user)),
                ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).isEqualTo("SUPPLIER_INACTIVE");
    }

    // ---- Idempotence : meme cle + meme corps -> rejeu (meme Order) ----

    @Test
    void payAgain_replayedWithSameIdempotencyKeyAndSameBody_neverCreatesASecondOrder() {
        setUpAdminWithLiquidity();
        User userEntity = createUser(RoleCode.USER);
        String user = tokenFor(userEntity);
        SupplierDetailResponse supplier = createActiveSupplier(userEntity.getId());
        PayAgainRequest request = new PayAgainRequest(new BigDecimal("100000"), null, null);
        String idemKey = "pay-again-" + UUID.randomUUID();

        ResponseEntity<ApiResponse<OrderDetailResponse>> first = payAgainRaw(user, supplier.id(), request, idemKey);
        ResponseEntity<ApiResponse<OrderDetailResponse>> second = payAgainRaw(user, supplier.id(), request, idemKey);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getBody().data().id()).isEqualTo(first.getBody().data().id());
        assertThat(second.getBody().data().quoteId()).isEqualTo(first.getBody().data().quoteId());
    }

    // ---- Idempotence : meme cle + corps different -> 409 ----

    @Test
    void payAgain_sameKeyWithDifferentBody_isRejectedWithConflict() {
        setUpAdminWithLiquidity();
        User userEntity = createUser(RoleCode.USER);
        String user = tokenFor(userEntity);
        SupplierDetailResponse supplier = createActiveSupplier(userEntity.getId());
        String idemKey = "pay-again-conflict-" + UUID.randomUUID();

        ResponseEntity<ApiResponse<OrderDetailResponse>> first = payAgainRaw(user, supplier.id(),
                new PayAgainRequest(new BigDecimal("100000"), null, null), idemKey);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        HttpHeaders headers = auth(user);
        headers.set("Idempotency-Key", idemKey);
        ResponseEntity<ErrorResponse> conflict = restTemplate.exchange(
                "/api/v1/suppliers/" + supplier.id() + "/pay-again", HttpMethod.POST,
                new HttpEntity<>(new PayAgainRequest(new BigDecimal("50000"), null, null), headers),
                ErrorResponse.class);

        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(conflict.getBody().code()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
    }

    // ---- Idempotence : cles differentes -> deux operations distinctes ----

    @Test
    void payAgain_withDifferentIdempotencyKeys_createsTwoIndependentOrders() {
        setUpAdminWithLiquidity();
        User userEntity = createUser(RoleCode.USER);
        String user = tokenFor(userEntity);
        SupplierDetailResponse supplier = createActiveSupplier(userEntity.getId());
        PayAgainRequest request = new PayAgainRequest(new BigDecimal("100000"), null, null);

        ResponseEntity<ApiResponse<OrderDetailResponse>> first = payAgainRaw(user, supplier.id(), request,
                "pay-again-key-a-" + UUID.randomUUID());
        ResponseEntity<ApiResponse<OrderDetailResponse>> second = payAgainRaw(user, supplier.id(), request,
                "pay-again-key-b-" + UUID.randomUUID());

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getBody().data().id()).isNotEqualTo(first.getBody().data().id());
    }

    @Test
    void payAgain_withoutIdempotencyKey_behavesNormally() {
        setUpAdminWithLiquidity();
        User userEntity = createUser(RoleCode.USER);
        String user = tokenFor(userEntity);
        SupplierDetailResponse supplier = createActiveSupplier(userEntity.getId());

        ResponseEntity<ApiResponse<OrderDetailResponse>> response = payAgainRaw(user, supplier.id(),
                new PayAgainRequest(new BigDecimal("100000"), null, null), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }
}
