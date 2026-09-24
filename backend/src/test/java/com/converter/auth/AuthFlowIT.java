package com.converter.auth;

import com.converter.auth.dto.AuthResponse;
import com.converter.auth.dto.LoginRequest;
import com.converter.auth.dto.RegisterRequest;
import com.converter.common.api.ApiResponse;
import com.converter.common.api.ErrorResponse;
import com.converter.auth.dto.UpdateExperienceProfileRequest;
import com.converter.support.AbstractIntegrationTest;
import com.converter.user.domain.ExperienceProfile;
import com.converter.user.dto.UserResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifie le parcours complet inscription -> connexion -> profil, ainsi
 * que les rejets attendus (numero deja utilise, mot de passe errone,
 * absence de jeton).
 */
class AuthFlowIT extends AbstractIntegrationTest {

    @Test
    void registerThenLoginThenMe_succeeds() {
        String phone = uniquePhone();
        RegisterRequest register = new RegisterRequest(phone, "correct-horse-battery", "Jean", "Kouassi", null);

        ResponseEntity<ApiResponse<AuthResponse>> registerResponse = restTemplate.exchange(
                "/api/auth/register", HttpMethod.POST,
                new HttpEntity<>(register),
                new org.springframework.core.ParameterizedTypeReference<ApiResponse<AuthResponse>>() {
                });

        assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        AuthResponse registerBody = registerResponse.getBody().data();
        assertThat(registerBody.accessToken()).isNotBlank();
        assertThat(registerBody.user().phone()).isEqualTo(phone);
        assertThat(registerBody.user().roles()).containsExactly("USER");

        LoginRequest login = new LoginRequest(phone, "correct-horse-battery");
        ResponseEntity<ApiResponse<AuthResponse>> loginResponse = restTemplate.exchange(
                "/api/auth/login", HttpMethod.POST,
                new HttpEntity<>(login),
                new org.springframework.core.ParameterizedTypeReference<ApiResponse<AuthResponse>>() {
                });

        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        String token = loginResponse.getBody().data().accessToken();
        assertThat(token).isNotBlank();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<ApiResponse<UserResponse>> meResponse = restTemplate.exchange(
                "/api/auth/me", HttpMethod.GET,
                new HttpEntity<>(headers),
                new org.springframework.core.ParameterizedTypeReference<ApiResponse<UserResponse>>() {
                });

        assertThat(meResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(meResponse.getBody().data().phone()).isEqualTo(phone);
        // PRO par defaut : jamais de bascule silencieuse vers un habillage jamais choisi.
        assertThat(meResponse.getBody().data().experienceProfile()).isEqualTo(ExperienceProfile.PRO);
    }

    @Test
    void register_withExplicitExperienceProfile_isPersisted() {
        String phone = uniquePhone();
        RegisterRequest register = new RegisterRequest(
                phone, "correct-horse-battery", "Ali", "Traore", ExperienceProfile.STUDENT_MALE);

        ResponseEntity<ApiResponse<AuthResponse>> registerResponse = restTemplate.exchange(
                "/api/auth/register", HttpMethod.POST,
                new HttpEntity<>(register),
                new org.springframework.core.ParameterizedTypeReference<ApiResponse<AuthResponse>>() {
                });

        assertThat(registerResponse.getBody().data().user().experienceProfile())
                .isEqualTo(ExperienceProfile.STUDENT_MALE);
    }

    @Test
    void updateExperienceProfile_changesItForSubsequentMeCalls() {
        String phone = uniquePhone();
        restTemplate.postForEntity("/api/auth/register",
                new RegisterRequest(phone, "correct-horse-battery", "Fatou", "Ba", null), ApiResponse.class);
        ResponseEntity<ApiResponse<AuthResponse>> loginResponse = restTemplate.exchange(
                "/api/auth/login", HttpMethod.POST,
                new HttpEntity<>(new LoginRequest(phone, "correct-horse-battery")),
                new org.springframework.core.ParameterizedTypeReference<ApiResponse<AuthResponse>>() {
                });
        String token = loginResponse.getBody().data().accessToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<ApiResponse<UserResponse>> updateResponse = restTemplate.exchange(
                "/api/auth/me/experience-profile", HttpMethod.PATCH,
                new HttpEntity<>(new UpdateExperienceProfileRequest(ExperienceProfile.STUDENT_FEMALE), headers),
                new org.springframework.core.ParameterizedTypeReference<ApiResponse<UserResponse>>() {
                });
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateResponse.getBody().data().experienceProfile()).isEqualTo(ExperienceProfile.STUDENT_FEMALE);

        ResponseEntity<ApiResponse<UserResponse>> meResponse = restTemplate.exchange(
                "/api/auth/me", HttpMethod.GET,
                new HttpEntity<>(headers),
                new org.springframework.core.ParameterizedTypeReference<ApiResponse<UserResponse>>() {
                });
        assertThat(meResponse.getBody().data().experienceProfile()).isEqualTo(ExperienceProfile.STUDENT_FEMALE);
    }

    @Test
    void register_withAlreadyUsedPhone_returns409() {
        String phone = uniquePhone();
        RegisterRequest register = new RegisterRequest(phone, "first-password-123", "Awa", "Diallo", null);
        restTemplate.postForEntity("/api/auth/register", register, ApiResponse.class);

        ResponseEntity<ErrorResponse> second = restTemplate.postForEntity(
                "/api/auth/register", register, ErrorResponse.class);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(second.getBody().code()).isEqualTo("PHONE_ALREADY_REGISTERED");
    }

    @Test
    void login_withWrongPassword_returns401WithGenericMessage() {
        String phone = uniquePhone();
        restTemplate.postForEntity("/api/auth/register",
                new RegisterRequest(phone, "the-real-password", "Fatou", "Sy", null), ApiResponse.class);

        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                "/api/auth/login", new LoginRequest(phone, "wrong-password"), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().code()).isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void login_withUnknownPhone_returns401WithSameGenericMessage() {
        // Meme code d'erreur qu'un mot de passe errone : voir la note de
        // AuthService sur la prevention de l'enumeration de comptes.
        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                "/api/auth/login", new LoginRequest(uniquePhone(), "whatever-123"), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().code()).isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void me_withoutToken_returns401() {
        ResponseEntity<ErrorResponse> response = restTemplate.getForEntity("/api/auth/me", ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().code()).isEqualTo("AUTHENTICATION_REQUIRED");
    }

    @Test
    void register_withInvalidPhoneFormat_returns400ValidationError() {
        RegisterRequest invalid = new RegisterRequest("0700000000", "some-password-123", "Jean", "Kouassi", null);

        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                "/api/auth/register", invalid, ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
    }
}
