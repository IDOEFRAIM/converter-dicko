package com.converter.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Identifiant de correlation par requete — voir l'audit de fermeture, Phase 18.
 *
 * <p>Reprend {@code X-Request-ID} s'il est fourni par un client/proxy en amont (sanitise
 * strictement : jamais injecte tel quel dans les journaux ou l'en-tete de reponse sans controle),
 * sinon en genere un nouveau. Place en MDC ({@link #MDC_KEY}) pour la duree de la requete : toute
 * ligne de log emise pendant son traitement — y compris par un module qui n'a aucune connaissance
 * du HTTP (service, repository) — le porte automatiquement des lors que {@code
 * logging.pattern.level} l'inclut (voir {@code application.yml}).
 *
 * <p>Volontairement minimal : pas de tracing distribue, pas de propagation vers un service tiers
 * — un simple identifiant qui permet de retrouver, dans les journaux d'une seule instance, toutes
 * les lignes issues d'une meme requete HTTP, y compris a travers plusieurs threads si le
 * traitement en delegue une partie (le MDC de SLF4J est cependant lie au thread : une delegation
 * asynchrone ne herite pas automatiquement de cette valeur, hors perimetre de cette mission).
 */
@Component
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-ID";
    public static final String MDC_KEY = "requestId";

    private static final int MAX_LENGTH = 64;
    private static final Pattern SAFE_VALUE = Pattern.compile("[a-zA-Z0-9-]+");

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = sanitize(request.getHeader(REQUEST_ID_HEADER));
        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
        }
        MDC.put(MDC_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // Le pool de threads du serveur est reutilise entre requetes : une valeur laissee en
            // MDC contaminerait les journaux de la requete suivante traitee par le meme thread.
            MDC.remove(MDC_KEY);
        }
    }

    /** N'accepte qu'une valeur simple et bornee — jamais un caractere de controle ou un saut de ligne dans un log. */
    private static String sanitize(String candidate) {
        if (candidate == null || candidate.isBlank() || candidate.length() > MAX_LENGTH) {
            return null;
        }
        return SAFE_VALUE.matcher(candidate).matches() ? candidate : null;
    }
}
