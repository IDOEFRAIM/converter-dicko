package com.converter.push;

import com.converter.common.api.ApiResponse;
import com.converter.common.api.ErrorResponse;
import com.converter.push.dto.PushConfigResponse;
import com.converter.push.dto.SubscribePushRequest;
import com.converter.push.dto.UnsubscribePushRequest;
import com.converter.support.AbstractOrderPipelineIT;
import com.converter.user.domain.RoleCode;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/** {@code /api/v1/push/**} — abonnement Web Push (PWA, mission "blocages Apple/Meta" oct. 2026). */
class PushFlowIT extends AbstractOrderPipelineIT {

    @Test
    void config_isPublic_andReflectsUnconfiguredEnvironment() {
        // Aucune cle VAPID en profil de test (voir application.yml, VAPID_PUBLIC_KEY absent) :
        // available doit rester false, jamais une exception ni un 401 -- endpoint public
        // (SecurityConfig), consultable meme avant la connexion.
        ResponseEntity<ApiResponse<PushConfigResponse>> response = restTemplate.exchange(
                "/api/v1/push/config", HttpMethod.GET, HttpEntity.EMPTY,
                new ParameterizedTypeReference<ApiResponse<PushConfigResponse>>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().available()).isFalse();
    }

    @Test
    void subscribe_thenUnsubscribe_succeeds() {
        String user = tokenFor(createUser(RoleCode.USER));
        SubscribePushRequest request = new SubscribePushRequest(
                "https://push.example.com/endpoint/" + java.util.UUID.randomUUID(),
                new SubscribePushRequest.Keys("p256dh-test-key", "auth-test-key"));

        ResponseEntity<ApiResponse<Void>> subscribeResponse = restTemplate.exchange(
                "/api/v1/push/subscriptions", HttpMethod.POST, new HttpEntity<>(request, auth(user)),
                new ParameterizedTypeReference<ApiResponse<Void>>() {
                });
        assertThat(subscribeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<ApiResponse<Void>> unsubscribeResponse = restTemplate.exchange(
                "/api/v1/push/subscriptions", HttpMethod.DELETE,
                new HttpEntity<>(new UnsubscribePushRequest(request.endpoint()), auth(user)),
                new ParameterizedTypeReference<ApiResponse<Void>>() {
                });
        assertThat(unsubscribeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void subscribe_withTheSameEndpointTwice_isIdempotent() {
        String user = tokenFor(createUser(RoleCode.USER));
        SubscribePushRequest request = new SubscribePushRequest(
                "https://push.example.com/endpoint/" + java.util.UUID.randomUUID(),
                new SubscribePushRequest.Keys("p256dh-test-key", "auth-test-key"));

        ResponseEntity<ApiResponse<Void>> first = restTemplate.exchange(
                "/api/v1/push/subscriptions", HttpMethod.POST, new HttpEntity<>(request, auth(user)),
                new ParameterizedTypeReference<ApiResponse<Void>>() {
                });
        ResponseEntity<ApiResponse<Void>> second = restTemplate.exchange(
                "/api/v1/push/subscriptions", HttpMethod.POST, new HttpEntity<>(request, auth(user)),
                new ParameterizedTypeReference<ApiResponse<Void>>() {
                });

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void subscribe_anonymous_returns401() {
        SubscribePushRequest request = new SubscribePushRequest(
                "https://push.example.com/endpoint/anon",
                new SubscribePushRequest.Keys("p256dh-test-key", "auth-test-key"));

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/push/subscriptions", new HttpEntity<>(request), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void subscribe_withoutEndpoint_returns400() {
        String user = tokenFor(createUser(RoleCode.USER));
        String body = "{\"keys\":{\"p256dh\":\"k\",\"auth\":\"a\"}}";
        var headers = auth(user);
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/v1/push/subscriptions", HttpMethod.POST, new HttpEntity<>(body, headers), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
