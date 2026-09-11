package com.converter.security;

import com.converter.common.exception.ErrorCode;
import com.converter.config.props.AbuseProtectionProperties;
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
import java.util.regex.Pattern;

/**
 * Garde-fou anti force-brute / anti-flooding en memoire (Caffeine),
 * cle sur l'adresse IP cliente (jamais sur un identifiant metier soumis
 * par le client : compter par identifiant cible permettrait a un
 * attaquant de saturer volontairement le compteur d'un tiers).
 *
 * <p>Deux familles de regles :
 * <ul>
 *   <li>{@code /api/auth/login} — limite de <b>tentatives</b> : le
 *       compteur d'une IP est remis a zero des qu'une connexion reussit
 *       (protege contre le bourrage d'identifiants, pas contre le
 *       volume d'un utilisateur legitime).</li>
 *   <li>Inscription (classique et Google), et endpoints d'ecriture authentifies
 *       (devis, ordre, soumission de paiement) — limite de <b>volume</b> par fenetre
 *       glissante, jamais remise a zero par un succes : une IP qui
 *       reussirait 1000 creations de devis legitimes en un instant reste
 *       aussi suspecte qu'une IP qui en ferait echouer 1000.</li>
 * </ul>
 *
 * <p>Implementation en memoire, suffisante pour une seule instance. Un
 * deploiement multi-instance necessiterait un compteur partage (Redis) —
 * hors perimetre du MVP/pilote actuel, deliberement pas introduit ici
 * (voir l'audit de fermeture).
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final String LOGIN_PATH = "/api/auth/login";
    private static final String REGISTER_PATH = "/api/auth/register";
    private static final String GOOGLE_SIGNIN_PATH = "/api/auth/google";
    private static final String GOOGLE_COMPLETE_PATH = "/api/auth/google/complete";
    private static final String QUOTE_CREATE_PATH = "/api/v1/quotes";
    private static final String ORDER_CREATE_PATH = "/api/v1/orders";
    private static final Pattern PAYMENT_SUBMIT_PATH = Pattern.compile("^/api/v1/orders/[^/]+/payments$");

    private final LoginProtectionProperties loginProperties;
    private final AbuseProtectionProperties abuseProperties;
    private final SecurityResponseWriter responseWriter;
    private final Cache<String, AtomicInteger> loginAttemptsByIp;
    private final Cache<String, AtomicInteger> registerAttemptsByIp;
    private final Cache<String, AtomicInteger> writeAttemptsByIp;

    public RateLimitFilter(LoginProtectionProperties loginProperties,
                           AbuseProtectionProperties abuseProperties,
                           SecurityResponseWriter responseWriter) {
        this.loginProperties = loginProperties;
        this.abuseProperties = abuseProperties;
        this.responseWriter = responseWriter;
        this.loginAttemptsByIp = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(loginProperties.lockDurationMinutes()))
                .maximumSize(10_000)
                .build();
        this.registerAttemptsByIp = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(abuseProperties.registerWindowMinutes()))
                .maximumSize(10_000)
                .build();
        this.writeAttemptsByIp = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(abuseProperties.writeWindowMinutes()))
                .maximumSize(10_000)
                .build();
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        if (isLoginAttempt(request)) {
            filterLoginAttempt(request, response, filterChain);
        } else if (isRegisterAttempt(request)) {
            filterVolume(request, response, filterChain, registerAttemptsByIp, abuseProperties.registerMaxAttempts());
        } else if (isWriteAttempt(request)) {
            filterVolume(request, response, filterChain, writeAttemptsByIp, abuseProperties.writeMaxAttempts());
        } else {
            filterChain.doFilter(request, response);
        }
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !(isLoginAttempt(request) || isRegisterAttempt(request) || isWriteAttempt(request));
    }

    private void filterLoginAttempt(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws IOException, ServletException {
        String ip = clientIp(request);
        AtomicInteger attempts = loginAttemptsByIp.get(ip, key -> new AtomicInteger(0));

        if (attempts.get() >= loginProperties.maxAttempts()) {
            log.warn("Verrouillage anti force-brute actif pour l'IP {} sur {}", ip, LOGIN_PATH);
            responseWriter.write(request, response, ErrorCode.TOO_MANY_ATTEMPTS,
                    "Trop de tentatives de connexion. Reessayez dans "
                            + loginProperties.lockDurationMinutes() + " minutes.");
            return;
        }

        // Incremente avant de traiter la requete : meme une exception
        // inattendue en aval compte comme une tentative, comportement
        // prudent pour un garde-fou de securite.
        attempts.incrementAndGet();
        filterChain.doFilter(request, response);

        if (response.getStatus() < 400) {
            // Connexion reussie : le compteur de cette IP est remis a zero.
            loginAttemptsByIp.invalidate(ip);
        }
    }

    private void filterVolume(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain,
                              Cache<String, AtomicInteger> cache, int maxAttempts)
            throws IOException, ServletException {
        String ip = clientIp(request);
        AtomicInteger attempts = cache.get(ip, key -> new AtomicInteger(0));

        if (attempts.incrementAndGet() > maxAttempts) {
            log.warn("Limite de volume atteinte pour l'IP {} sur {} {}", ip, request.getMethod(),
                    request.getRequestURI());
            responseWriter.write(request, response, ErrorCode.TOO_MANY_ATTEMPTS,
                    "Trop de requetes. Reessayez dans quelques minutes.");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isLoginAttempt(HttpServletRequest request) {
        return isPost(request) && LOGIN_PATH.equals(request.getRequestURI());
    }

    private boolean isRegisterAttempt(HttpServletRequest request) {
        return isPost(request) && REGISTER_PATH.equals(request.getRequestURI());
    }

    private boolean isWriteAttempt(HttpServletRequest request) {
        if (!isPost(request)) {
            return false;
        }
        String uri = request.getRequestURI();
        return QUOTE_CREATE_PATH.equals(uri)
                || ORDER_CREATE_PATH.equals(uri)
                || PAYMENT_SUBMIT_PATH.matcher(uri).matches()
                || GOOGLE_SIGNIN_PATH.equals(uri)
                || GOOGLE_COMPLETE_PATH.equals(uri);
    }

    private boolean isPost(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod());
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
