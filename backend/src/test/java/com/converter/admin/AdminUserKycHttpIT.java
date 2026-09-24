package com.converter.admin;

import com.converter.common.api.ApiResponse;
import com.converter.common.api.ErrorResponse;
import com.converter.support.AbstractOrderPipelineIT;
import com.converter.user.domain.RoleCode;
import com.converter.user.domain.User;
import com.converter.user.dto.AdminUserDetail;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code /api/admin/users/{id}/kyc/verify} et {@code /revoke} — reserve aux administrateurs
 * (verifie deux fois : {@code SecurityFilterChain} + {@code @PreAuthorize}), transitions
 * idempotentes.
 */
class AdminUserKycHttpIT extends AbstractOrderPipelineIT {

    private ResponseEntity<ApiResponse<AdminUserDetail>> verifyKycRaw(String adminToken, UUID userId) {
        return restTemplate.exchange("/api/admin/users/" + userId + "/kyc/verify", HttpMethod.POST,
                new HttpEntity<>(auth(adminToken)),
                new ParameterizedTypeReference<ApiResponse<AdminUserDetail>>() {
                });
    }

    private ResponseEntity<ApiResponse<AdminUserDetail>> revokeKycRaw(String adminToken, UUID userId) {
        return restTemplate.exchange("/api/admin/users/" + userId + "/kyc/revoke", HttpMethod.POST,
                new HttpEntity<>(auth(adminToken)),
                new ParameterizedTypeReference<ApiResponse<AdminUserDetail>>() {
                });
    }

    @Test
    void verify_asAdmin_marksUserVerified() {
        String admin = adminToken();
        User target = createUnverifiedUser(RoleCode.USER);

        ResponseEntity<ApiResponse<AdminUserDetail>> response = verifyKycRaw(admin, target.getId());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().kycVerified()).isTrue();
        assertThat(response.getBody().data().kycVerifiedAt()).isNotNull();
    }

    @Test
    void verify_twice_isIdempotent() {
        String admin = adminToken();
        User target = createUnverifiedUser(RoleCode.USER);
        verifyKycRaw(admin, target.getId());

        ResponseEntity<ApiResponse<AdminUserDetail>> second = verifyKycRaw(admin, target.getId());

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody().data().kycVerified()).isTrue();
    }

    @Test
    void revoke_afterVerify_marksUserUnverified() {
        String admin = adminToken();
        User target = createUnverifiedUser(RoleCode.USER);
        verifyKycRaw(admin, target.getId());

        ResponseEntity<ApiResponse<AdminUserDetail>> response = revokeKycRaw(admin, target.getId());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().kycVerified()).isFalse();
        assertThat(response.getBody().data().kycVerifiedAt()).isNull();
    }

    @Test
    void verify_asRegularUser_returns403() {
        String admin = adminToken();
        User target = createUser(RoleCode.USER);
        String regularUser = tokenFor(createUser(RoleCode.USER));

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/admin/users/" + target.getId() + "/kyc/verify", HttpMethod.POST,
                new HttpEntity<>(auth(regularUser)), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void verify_anonymous_returns401() {
        User target = createUser(RoleCode.USER);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/admin/users/" + target.getId() + "/kyc/verify", null, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void verify_unknownUser_returns404() {
        String admin = adminToken();

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/admin/users/" + UUID.randomUUID() + "/kyc/verify", HttpMethod.POST,
                new HttpEntity<>(auth(admin)), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo("USER_NOT_FOUND");
    }
}
