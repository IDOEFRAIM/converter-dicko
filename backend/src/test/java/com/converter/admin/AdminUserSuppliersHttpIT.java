package com.converter.admin;

import com.converter.common.api.ApiResponse;
import com.converter.common.api.PageResponse;
import com.converter.order.domain.BeneficiaryType;
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
 * {@code GET /api/admin/users/{id}/suppliers[/{{supplierId}}/qr-code]} — retour client :
 * "l'admin n'arrive pas a voir les detail des different fournisseur pour chaque
 * utilisateur...c'est ca qui permet de pouvoir faire les transfert". Avant cet endpoint,
 * la seule vue admin d'un fournisseur passait par un ordre deja cree ({@code
 * AdminOrderQrCodeHttpIT}) : impossible de consulter le carnet d'un client AVANT qu'il ne
 * l'utilise pour un transfert.
 */
class AdminUserSuppliersHttpIT extends AbstractOrderPipelineIT {

    @Autowired
    private SupplierService supplierService;

    @Test
    void suppliers_returnsFullDetailUnmasked_unlikeTheOwnersOwnMaskedCarnet() {
        String admin = adminToken();
        User userEntity = createUser(RoleCode.USER);

        CreateSupplierRequest request = new CreateSupplierRequest(BeneficiaryType.CHINESE_BANK_ACCOUNT,
                "Fournisseur Test", null, null, null, "China", "Shenzhen", null, "Bank of China", null,
                "Zhang San", "6222000000001234", null, null, Currency.CNY, Purpose.IMPORT_GOODS, null);
        supplierService.create(request, userEntity.getId());

        ResponseEntity<ApiResponse<PageResponse<SupplierDetailResponse>>> response = restTemplate.exchange(
                "/api/admin/users/" + userEntity.getId() + "/suppliers", HttpMethod.GET,
                new HttpEntity<>(auth(admin)),
                new ParameterizedTypeReference<ApiResponse<PageResponse<SupplierDetailResponse>>>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var content = response.getBody().data().content();
        assertThat(content).hasSize(1);
        // Jamais masque (contrairement a SupplierSummaryResponse renvoye au client lui-meme) :
        // c'est precisement le compte en clair qui permet a l'admin d'effectuer le transfert.
        assertThat(content.get(0).accountNumber()).isEqualTo("6222000000001234");
    }

    @Test
    void suppliers_forUserWithoutSuppliers_returnsEmptyPage() {
        String admin = adminToken();
        User userEntity = createUser(RoleCode.USER);

        ResponseEntity<ApiResponse<PageResponse<SupplierDetailResponse>>> response = restTemplate.exchange(
                "/api/admin/users/" + userEntity.getId() + "/suppliers", HttpMethod.GET,
                new HttpEntity<>(auth(admin)),
                new ParameterizedTypeReference<ApiResponse<PageResponse<SupplierDetailResponse>>>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().content()).isEmpty();
    }

    @Test
    void suppliers_asNonAdmin_isForbidden() {
        String user = tokenFor(createUser(RoleCode.USER));

        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                "/api/admin/users/" + UUID.randomUUID() + "/suppliers", HttpMethod.GET,
                new HttpEntity<>(auth(user)), new ParameterizedTypeReference<ApiResponse<Void>>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void supplierQrCode_forAlipaySupplier_returnsTheRealQrCodeImage() {
        String admin = adminToken();
        User userEntity = createUser(RoleCode.USER);

        CreateSupplierRequest request = new CreateSupplierRequest(BeneficiaryType.ALIPAY, "QR Only Supplier",
                null, null, null, "China", "Shenzhen", null, null, null, null, null, null, null,
                Currency.CNY, Purpose.IMPORT_GOODS, null);
        SupplierDetailResponse supplier = supplierService.create(request, userEntity.getId());
        supplierService.attachQrCode(supplier.id(), "qr.png", "image/png", REAL_QR_CODE_PNG, userEntity.getId());

        ResponseEntity<byte[]> response = restTemplate.exchange(
                "/api/admin/users/" + userEntity.getId() + "/suppliers/" + supplier.id() + "/qr-code",
                HttpMethod.GET, new HttpEntity<>(auth(admin)), byte[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(response.getBody()).isEqualTo(REAL_QR_CODE_PNG);
    }

    @Test
    void supplierQrCode_whenSupplierBelongsToADifferentUser_returns404() {
        String admin = adminToken();
        User owner = createUser(RoleCode.USER);
        User otherUser = createUser(RoleCode.USER);

        CreateSupplierRequest request = new CreateSupplierRequest(BeneficiaryType.ALIPAY, "QR Only Supplier",
                null, null, null, "China", "Shenzhen", null, null, null, null, null, null, null,
                Currency.CNY, Purpose.IMPORT_GOODS, null);
        SupplierDetailResponse supplier = supplierService.create(request, owner.getId());
        supplierService.attachQrCode(supplier.id(), "qr.png", "image/png", REAL_QR_CODE_PNG, owner.getId());

        // URL imbriquee trompeuse : le fournisseur existe bel et bien, mais pas sous CET utilisateur.
        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                "/api/admin/users/" + otherUser.getId() + "/suppliers/" + supplier.id() + "/qr-code",
                HttpMethod.GET, new HttpEntity<>(auth(admin)), new ParameterizedTypeReference<ApiResponse<Void>>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void supplierQrCode_asNonAdmin_isForbidden() {
        String user = tokenFor(createUser(RoleCode.USER));

        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                "/api/admin/users/" + UUID.randomUUID() + "/suppliers/" + UUID.randomUUID() + "/qr-code",
                HttpMethod.GET, new HttpEntity<>(auth(user)), new ParameterizedTypeReference<ApiResponse<Void>>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
