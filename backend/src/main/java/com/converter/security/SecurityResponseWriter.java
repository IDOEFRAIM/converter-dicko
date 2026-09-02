package com.converter.security;

import com.converter.common.api.ErrorResponse;
import com.converter.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;

/**
 * Ecrit une reponse d'erreur au format {@link ErrorResponse} depuis la
 * chaine de filtres Servlet.
 *
 * <p>{@link com.converter.common.exception.GlobalExceptionHandler} ne
 * peut pas intercepter les exceptions levees avant le
 * {@code DispatcherServlet} : l'authentification par jeton echoue dans
 * un filtre, en amont. Ce composant garantit que le contrat d'erreur
 * reste identique, que le rejet vienne d'un filtre ou d'un controleur —
 * le frontend ne doit jamais avoir a distinguer les deux cas.
 */
@Component
public class SecurityResponseWriter {

    private final ObjectMapper objectMapper;

    public SecurityResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletRequest request, HttpServletResponse response,
                      ErrorCode code, String message) throws IOException {
        response.setStatus(code.status().value());
        response.setContentType("application/json;charset=UTF-8");

        ErrorResponse body = new ErrorResponse(
                Instant.now(),
                code.status().value(),
                code.category(),
                code.name(),
                message,
                request.getRequestURI(),
                null,
                null);

        objectMapper.writeValue(response.getWriter(), body);
    }
}
