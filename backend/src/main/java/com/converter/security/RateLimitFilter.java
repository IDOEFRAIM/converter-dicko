package com.converter.security;

import com.converter.common.exception.ErrorCode;
import com.converter.config.props.LoginProtectionProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Garde-fou anti force brute sur {@code POST /api/auth/login}.
 *
 * <p>Le compteur est cle sur l'adresse IP, pas sur le numero de
 * telephone soumis : compter par identifiant cible permettrait a un
 * attaquant de saturer volontairement le compteur d'un tiers pour
 * l'empecher de se connecter (attaque en deni de service cible).
 *
 * <p>Implementation en memoire (Caffeine), suffisante pour une seule
 * instance. Un deploiement multi-instance necessiterait un compteur
 * partage (Redis) — hors perimetre du MVP, note en Phase 10.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final String LOGIN_PATH = "/api/auth/login";

    private final LoginProtectionProperties properties;
    private final SecurityResponseWriter responseWriter;
    private final Cache<String, AtomicInteger> attemptsByIp;

    public RateLimitFilter(LoginProtectionProperties properties, SecurityResponseWriter responseWriter) {
        this.properties = properties;
        this.responseWriter = responseWriter;
        this.attemptsByIp = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(properties.lockDurationMinutes()))
                .maximumSize(10_000)
                .build();
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        if (!isLoginAttempt(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip = clientIp(request);
        AtomicInteger attempts = attemptsByIp.get(ip, key -> new AtomicInteger(0));

        if (attempts.get() >= properties.maxAttempts()) {
            log.warn("Verrouillage anti force-brute actif pour l'IP {}", ip);
            responseWriter.write(request, response, ErrorCode.TOO_MANY_ATTEMPTS,
                    "Trop de tentatives de connexion. Reessayez dans "
                            + properties.lockDurationMinutes() + " minutes.");
            return;
        }

        // Incremente avant de traiter la requete : meme une exception
        // inattendue en aval compte comme une tentative, comportement
        // prudent pour un garde-fou de securite.
        attempts.incrementAndGet();
        filterChain.doFilter(request, response);

        if (response.getStatus() < 400) {
            // Connexion reussie : le compteur de cette IP est remis a zero.
            attemptsByIp.invalidate(ip);
        }
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !isLoginAttempt(request);
    }

    private boolean isLoginAttempt(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod())
                && LOGIN_PATH.equals(request.getRequestURI());
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
