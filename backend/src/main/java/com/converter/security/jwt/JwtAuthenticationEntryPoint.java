package com.converter.security.jwt;

import com.converter.common.exception.ErrorCode;
import com.converter.security.SecurityResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Point d'entree invoque quand une ressource protegee est appelee sans
 * authentification valide.
 *
 * <p>Enregistre sur le {@code SecurityFilterChain} : c'est la reponse
 * que recoit tout appel non authentifie sur un endpoint exigeant un
 * jeton — la premiere ligne des "contraintes d'implementation" de la
 * Phase 2 (les endpoints proteges refusent reellement les acces non
 * authentifies).
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final SecurityResponseWriter responseWriter;

    public JwtAuthenticationEntryPoint(SecurityResponseWriter responseWriter) {
        this.responseWriter = responseWriter;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        responseWriter.write(request, response, ErrorCode.AUTHENTICATION_REQUIRED,
                "Authentification requise pour acceder a cette ressource.");
    }
}
