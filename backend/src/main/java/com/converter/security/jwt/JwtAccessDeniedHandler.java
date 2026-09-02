package com.converter.security.jwt;

import com.converter.common.exception.ErrorCode;
import com.converter.security.SecurityResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Invoque quand un utilisateur authentifie appelle une ressource hors
 * de ses droits — typiquement un compte {@code USER} sur
 * {@code /api/admin/**}.
 *
 * <p>C'est la seconde des deux barrieres qui protegent l'espace
 * d'administration : la premiere est la regle
 * {@code .requestMatchers("/api/admin/**").hasRole("ADMIN")} du
 * {@code SecurityFilterChain}, la seconde {@code @PreAuthorize} sur
 * chaque methode sensible. Ce gestionnaire uniformise leur reponse.
 */
@Component
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final SecurityResponseWriter responseWriter;

    public JwtAccessDeniedHandler(SecurityResponseWriter responseWriter) {
        this.responseWriter = responseWriter;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        responseWriter.write(request, response, ErrorCode.ACCESS_DENIED,
                "Vous n'avez pas les droits requis pour cette operation.");
    }
}
