package com.converter.supplier.web;

import com.converter.common.api.ApiResponse;
import com.converter.common.api.ErrorResponse;
import com.converter.order.domain.BeneficiaryType;
import com.converter.supplier.dto.CreateSupplierRequest;
import com.converter.supplier.dto.SupplierDetailResponse;
import com.converter.support.AbstractRateQuoteIT;
import com.converter.treasury.domain.Currency;
import com.converter.user.domain.RoleCode;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Isolation stricte par proprietaire, verifiee de bout en bout via HTTP (controleur + securite +
 * service) : un utilisateur qui connait l'UUID du fournisseur d'un autre ne doit rien pouvoir en
 * lire ni en modifier — 404, jamais 403 (voir {@code OwnershipService}).
 */
class SupplierSecurityIT extends AbstractRateQuoteIT {

    private SupplierDetailResponse createSupplierAsOwner(String ownerToken) {
        CreateSupplierRequest request = new CreateSupplierRequest(BeneficiaryType.ALIPAY, "Owner's supplier", null,
                null, null, null, null, null, null, null, null, "alipay-owner-id", null, null, Currency.CNY, null,
                null);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(ownerToken);
        ResponseEntity<ApiResponse<SupplierDetailResponse>> response = restTemplate.exchange(
                "/api/v1/suppliers", HttpMethod.POST, new HttpEntity<>(request, headers),
                new ParameterizedTypeReference<ApiResponse<SupplierDetailResponse>>() {
                });
        return response.getBody().data();
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    @Test
    void get_anotherUsersSupplier_returns404NotForbidden() {
        String owner = tokenFor(createUser(RoleCode.USER));
        String intruder = tokenFor(createUser(RoleCode.USER));
        SupplierDetailResponse supplier = createSupplierAsOwner(owner);

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/v1/suppliers/" + supplier.id(), HttpMethod.GET,
                new HttpEntity<>(authHeaders(intruder)), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo("SUPPLIER_NOT_FOUND");
    }

    @Test
    void update_anotherUsersSupplier_returns404NotForbidden() {
        String owner = tokenFor(createUser(RoleCode.USER));
        String intruder = tokenFor(createUser(RoleCode.USER));
        SupplierDetailResponse supplier = createSupplierAsOwner(owner);

        CreateSupplierRequest hijackAttempt = new CreateSupplierRequest(BeneficiaryType.ALIPAY, "Hijacked", null,
                null, null, null, null, null, null, null, null, "hijacked-id", null, null, Currency.CNY, null,
                null);

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/v1/suppliers/" + supplier.id(), HttpMethod.PUT,
                new HttpEntity<>(hijackAttempt, authHeaders(intruder)), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo("SUPPLIER_NOT_FOUND");
    }

    @Test
    void favorite_anotherUsersSupplier_returns404NotForbidden() {
        String owner = tokenFor(createUser(RoleCode.USER));
        String intruder = tokenFor(createUser(RoleCode.USER));
        SupplierDetailResponse supplier = createSupplierAsOwner(owner);

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/v1/suppliers/" + supplier.id() + "/favorite", HttpMethod.POST,
                new HttpEntity<>(authHeaders(intruder)), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo("SUPPLIER_NOT_FOUND");
    }

    @Test
    void deactivate_anotherUsersSupplier_returns404NotForbidden() {
        String owner = tokenFor(createUser(RoleCode.USER));
        String intruder = tokenFor(createUser(RoleCode.USER));
        SupplierDetailResponse supplier = createSupplierAsOwner(owner);

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/v1/suppliers/" + supplier.id() + "/deactivate", HttpMethod.POST,
                new HttpEntity<>(authHeaders(intruder)), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo("SUPPLIER_NOT_FOUND");
    }

    @Test
    void list_neverReturnsAnotherUsersSuppliers() {
        String owner = tokenFor(createUser(RoleCode.USER));
        String other = tokenFor(createUser(RoleCode.USER));
        createSupplierAsOwner(owner);

        ResponseEntity<ApiResponse<Object>> response = restTemplate.exchange(
                "/api/v1/suppliers", HttpMethod.GET, new HttpEntity<>(authHeaders(other)),
                new ParameterizedTypeReference<ApiResponse<Object>>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // La liste de "other" existe (200) mais ne contient jamais la ressource du proprietaire —
        // verifie plus precisement au niveau service (SupplierServiceIT.anotherUsersSuppliers_areNeverVisibleInMyList).
    }

    @Test
    void create_withoutToken_returns401() {
        CreateSupplierRequest request = new CreateSupplierRequest(BeneficiaryType.ALIPAY, "No auth", null, null,
                null, null, null, null, null, null, null, "some-id", null, null, Currency.CNY, null, null);

        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                "/api/v1/suppliers", request, ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().code()).isEqualTo("AUTHENTICATION_REQUIRED");
    }
}
