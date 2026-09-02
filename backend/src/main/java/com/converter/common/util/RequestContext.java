package com.converter.common.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Acces a la requete HTTP courante depuis la couche service, pour les
 * seuls besoins de l'audit (adresse IP, agent utilisateur).
 *
 * <p>Renvoie null hors contexte web (job planifie, test unitaire) sans
 * lever d'exception : l'audit doit fonctionner dans les deux mondes.
 */
public final class RequestContext {

    private static final int MAX_IP_LENGTH = 45;
    private static final int MAX_USER_AGENT_LENGTH = 255;

    private RequestContext() {
    }

    public static HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            return servletAttributes.getRequest();
        }
        return null;
    }

    /**
     * Adresse IP du client.
     *
     * <p>X-Forwarded-For n'est lu que parce que l'application tourne
     * derriere un reverse proxy de confiance
     * (server.forward-headers-strategy=framework). Seule la premiere
     * entree est retenue : les suivantes proviennent du client et ne
     * sont pas dignes de confiance.
     */
    public static String clientIp() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return truncate(forwarded.split(",")[0].trim(), MAX_IP_LENGTH);
        }
        return truncate(request.getRemoteAddr(), MAX_IP_LENGTH);
    }

    public static String userAgent() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        return truncate(request.getHeader("User-Agent"), MAX_USER_AGENT_LENGTH);
    }

    public static String path() {
        HttpServletRequest request = currentRequest();
        return request == null ? null : request.getRequestURI();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
