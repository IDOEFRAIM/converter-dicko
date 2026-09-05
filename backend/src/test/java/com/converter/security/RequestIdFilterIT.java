package com.converter.security;

import com.converter.common.api.ErrorResponse;
import com.converter.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Identifiant de correlation par requete — audit de fermeture, Phase 18.
 *
 * <p>Verifie a la fois le contrat HTTP ({@code X-Request-ID} toujours present en reponse, repris
 * tel quel si fourni par le client) et son integration avec {@code traceId} dans les deux chemins
 * de reponse d'erreur — {@code GlobalExceptionHandler} (controleurs) et
 * {@code SecurityResponseWriter} (rejets au niveau filtre, avant {@code DispatcherServlet}) —
 * ({@code traceId} d'une reponse d'erreur == {@code X-Request-ID} de cette meme reponse).
 */
class RequestIdFilterIT extends AbstractIntegrationTest {

    @Test
    void anyResponse_alwaysCarriesAGeneratedRequestId() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/settings/public", String.class);

        String requestId = response.getHeaders().getFirst(RequestIdFilter.REQUEST_ID_HEADER);
        assertThat(requestId).isNotBlank();
    }

    @Test
    void clientSuppliedRequestId_isEchoedBackUnchanged() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(RequestIdFilter.REQUEST_ID_HEADER, "client-supplied-abc123");

        ResponseEntity<String> response = restTemplate.exchange("/api/settings/public", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);

        assertThat(response.getHeaders().getFirst(RequestIdFilter.REQUEST_ID_HEADER))
                .isEqualTo("client-supplied-abc123");
    }

    @Test
    void unsafeClientSuppliedRequestId_isRejectedAndReplacedWithAGeneratedOne() {
        HttpHeaders headers = new HttpHeaders();
        // Un caractere de controle (ex. \n, pour une injection d'en-tete) ne peut meme pas etre
        // transmis via ce client HTTP : le JDK le rejette cote client avant l'envoi (protection
        // independante de RequestIdFilter). On verifie ici le meme filtre allowlist avec une
        // valeur transmissible mais hors de [a-zA-Z0-9-]+.
        headers.set(RequestIdFilter.REQUEST_ID_HEADER, "unsafe value with spaces!");

        ResponseEntity<String> response = restTemplate.exchange("/api/settings/public", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);

        String requestId = response.getHeaders().getFirst(RequestIdFilter.REQUEST_ID_HEADER);
        assertThat(requestId).isNotBlank();
        assertThat(requestId).isNotEqualTo("unsafe value with spaces!");
    }

    @Test
    void errorResponse_traceIdMatchesTheRequestIdHeaderOfTheSameResponse() {
        // 401 (pas de jeton) sur un endpoint protege : rejete au niveau du filtre de securite,
        // avant DispatcherServlet — reponse construite par SecurityResponseWriter, pas
        // GlobalExceptionHandler. Verifie que les deux chemins restent coherents (meme traceId).
        ResponseEntity<ErrorResponse> response = restTemplate.getForEntity("/api/admin/users", ErrorResponse.class);

        String headerRequestId = response.getHeaders().getFirst(RequestIdFilter.REQUEST_ID_HEADER);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().traceId()).isNotBlank().isEqualTo(headerRequestId);
    }
}
