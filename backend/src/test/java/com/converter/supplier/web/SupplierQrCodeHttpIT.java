package com.converter.supplier.web;

import com.converter.common.api.ApiResponse;
import com.converter.order.domain.BeneficiaryType;
import com.converter.support.AbstractOrderPipelineIT;
import com.converter.supplier.domain.Purpose;
import com.converter.supplier.dto.CreateSupplierRequest;
import com.converter.supplier.dto.SupplierDetailResponse;
import com.converter.supplier.service.SupplierService;
import com.converter.treasury.domain.Currency;
import com.converter.user.domain.RoleCode;
import com.converter.user.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code POST/GET /api/v1/suppliers/{id}/qr-code} — un QR Alipay/WeChat est une IMAGE, jamais un
 * champ texte (voir {@code Supplier#attachQrCode}). Meme discipline HTTP que les preuves de
 * paiement : ownership (404, jamais 403), type de fichier verifie par signature binaire.
 */
class SupplierQrCodeHttpIT extends AbstractOrderPipelineIT {

    @Autowired
    private SupplierService supplierService;

    private SupplierDetailResponse createAlipaySupplier(UUID ownerUserId) {
        // type, displayName, legalName, phone, email, country, city, province, bankName,
        // bankBranch, accountName, accountNumber, bankAddress, swiftCode, currency, purpose, notes
        CreateSupplierRequest request = new CreateSupplierRequest(BeneficiaryType.ALIPAY, "QR Test Supplier",
                null, null, null, "China", "Shenzhen", null, null, null, null, null, null, null,
                Currency.CNY, Purpose.IMPORT_GOODS, null);
        return supplierService.create(request, ownerUserId);
    }

    private SupplierDetailResponse createBankSupplier(UUID ownerUserId) {
        CreateSupplierRequest request = new CreateSupplierRequest(BeneficiaryType.CHINESE_BANK_ACCOUNT,
                "Bank Test Supplier", null, null, null, "China", "Guangzhou", null, "Bank of China", null, null,
                "6222021234567890", null, null, Currency.CNY, Purpose.BUSINESS, null);
        return supplierService.create(request, ownerUserId);
    }

    private ResponseEntity<ApiResponse<SupplierDetailResponse>> uploadQrCodeRaw(String userToken, UUID supplierId) {
        return uploadFileRaw(userToken, supplierId, REAL_QR_CODE_PNG, "qr.png");
    }

    private ResponseEntity<ApiResponse<SupplierDetailResponse>> uploadFileRaw(
            String userToken, UUID supplierId, byte[] content, String fileName) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return fileName;
            }
        });
        HttpHeaders headers = auth(userToken);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return restTemplate.exchange("/api/v1/suppliers/" + supplierId + "/qr-code", HttpMethod.POST,
                new HttpEntity<>(body, headers),
                new ParameterizedTypeReference<ApiResponse<SupplierDetailResponse>>() {
                });
    }

    @Test
    void uploadQrCode_forAlipaySupplier_succeedsAndMarksSupplierReady() {
        User userEntity = createUser(RoleCode.USER);
        String user = tokenFor(userEntity);
        SupplierDetailResponse supplier = createAlipaySupplier(userEntity.getId());
        assertThat(supplier.readyForPayment()).isFalse();

        ResponseEntity<ApiResponse<SupplierDetailResponse>> response = uploadQrCodeRaw(user, supplier.id());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        SupplierDetailResponse updated = response.getBody().data();
        assertThat(updated.qrCodeUploaded()).isTrue();
        assertThat(updated.qrCodeFileName()).isEqualTo("qr.png");
        assertThat(updated.readyForPayment()).isTrue();
    }

    /**
     * Regression cible : {@code FileValidator} verifie seulement le TYPE de fichier (signature
     * binaire) -- avant {@code QrCodeValidator}, n'importe quelle photo passait pour un "code QR"
     * (retour client : "au niveau du qrcode qu'on upload, on doit verifier qu'il est valide").
     */
    @Test
    void uploadQrCode_withAValidImageThatIsNotAQrCode_isRejected() {
        User userEntity = createUser(RoleCode.USER);
        String user = tokenFor(userEntity);
        SupplierDetailResponse supplier = createAlipaySupplier(userEntity.getId());

        ResponseEntity<ApiResponse<SupplierDetailResponse>> response =
                uploadFileRaw(user, supplier.id(), REAL_NON_QR_IMAGE_PNG, "not-a-qr.png");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /**
     * Regression cible (retour client, capture d'ecran a l'appui) : de nombreux telephones
     * Android enregistrent les captures d'ecran/images de galerie en WebP par defaut -- un vrai
     * code QR dans ce format etait rejete a tort ("Ce fichier ne contient pas de code QR
     * lisible.") car {@code ImageIO} n'a aucun lecteur WebP integre au JDK (voir QrCodeValidator,
     * pom.xml). Bout en bout HTTP, pas seulement {@code QrCodeValidatorTest} (unitaire) : verifie
     * aussi que {@code FileValidator} (deja au courant du type WebP) laisse bien passer le fichier
     * jusqu'a QrCodeValidator.
     */
    @Test
    void uploadQrCode_withARealQrCodeInWebpFormat_succeeds() {
        User userEntity = createUser(RoleCode.USER);
        String user = tokenFor(userEntity);
        SupplierDetailResponse supplier = createAlipaySupplier(userEntity.getId());
        byte[] webpQr = Base64.getDecoder().decode(
                "UklGRlIBAABXRUJQVlA4TEYBAAAvx8AxAA8w//M///MfeJDcSJIcSU7UI58UITVpKkagCFCxKk1ShHzOQTBm9r5yr1dE"
                + "/ydA/0kbMOSXpji0AFmKpKnQeitOTanX4uQ0ndqeJE1jr0fRueVXZaazIElTTi5Eyyn1WoBhvuc033MBSvmk6dT2"
                + "FKe+/pdn7IB6bk/RGfKrFHFIisZt0XMqetZCLjg55ZcWnFIsWm5P7PDEzm06VYmkdcgPTYvGULQsJRqPJz7yax2K"
                + "Xoo5DHGt90cPopXzgEPbE/B4ilOlRAN8TwkYipaVyA9tT9G4TZ0h9lLMyQW1nAqtw9RKETu3qQPsuRGnKjEnFzi0"
                + "EA3Q52v4pPmhKUiZk5UYMIxrvU0tp0WrRdK0OLURPWXRa3FymnpK0QEVpGjcFp3bnHrMySn13Ihei6Rp6gxBTulU"
                + "KcAwnZqKlhtqWcl/3A==");

        ResponseEntity<ApiResponse<SupplierDetailResponse>> response =
                uploadFileRaw(user, supplier.id(), webpQr, "qr.webp");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().readyForPayment()).isTrue();
    }

    @Test
    void uploadQrCode_forBankAccountSupplier_isRejected() {
        User userEntity = createUser(RoleCode.USER);
        String user = tokenFor(userEntity);
        SupplierDetailResponse supplier = createBankSupplier(userEntity.getId());

        ResponseEntity<ApiResponse<SupplierDetailResponse>> response = uploadQrCodeRaw(user, supplier.id());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void uploadQrCode_forAnotherUsersSupplier_returns404() {
        UUID ownerId = createUser(RoleCode.USER).getId();
        SupplierDetailResponse supplier = createAlipaySupplier(ownerId);
        String intruder = tokenFor(createUser(RoleCode.USER));

        ResponseEntity<ApiResponse<SupplierDetailResponse>> response = uploadQrCodeRaw(intruder, supplier.id());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void downloadQrCode_afterUpload_returnsTheImageWithCorrectContentType() {
        User userEntity = createUser(RoleCode.USER);
        String user = tokenFor(userEntity);
        SupplierDetailResponse supplier = createAlipaySupplier(userEntity.getId());
        uploadQrCodeRaw(user, supplier.id());

        ResponseEntity<byte[]> response = restTemplate.exchange(
                "/api/v1/suppliers/" + supplier.id() + "/qr-code", HttpMethod.GET,
                new HttpEntity<>(auth(user)), byte[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(response.getBody()).isEqualTo(REAL_QR_CODE_PNG);
    }

    @Test
    void downloadQrCode_beforeAnyUpload_returns404() {
        User userEntity = createUser(RoleCode.USER);
        String user = tokenFor(userEntity);
        SupplierDetailResponse supplier = createAlipaySupplier(userEntity.getId());

        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                "/api/v1/suppliers/" + supplier.id() + "/qr-code", HttpMethod.GET,
                new HttpEntity<>(auth(user)), new ParameterizedTypeReference<ApiResponse<Void>>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void downloadQrCode_forAnotherUsersSupplier_returns404() {
        User ownerEntity = createUser(RoleCode.USER);
        SupplierDetailResponse supplier = createAlipaySupplier(ownerEntity.getId());
        uploadQrCodeRaw(tokenFor(ownerEntity), supplier.id());
        String intruder = tokenFor(createUser(RoleCode.USER));

        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                "/api/v1/suppliers/" + supplier.id() + "/qr-code", HttpMethod.GET,
                new HttpEntity<>(auth(intruder)), new ParameterizedTypeReference<ApiResponse<Void>>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
