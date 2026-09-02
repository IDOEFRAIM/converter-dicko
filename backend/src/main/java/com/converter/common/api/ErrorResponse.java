package com.converter.common.api;

import java.time.Instant;
import java.util.List;

/**
 * Enveloppe unique de toutes les reponses d'erreur.
 *
 * @param code      identifiant STABLE et machine-lisible de l'erreur.
 *                  C'est la seule chose que le frontend doit tester.
 * @param message   texte destine a un humain ; peut etre traduit ou
 *                  reformule sans constituer une rupture de contrat.
 * @param traceId   correlateur avec les journaux serveur. Permet
 *                  d'investiguer un incident sans exposer de stack trace.
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        String traceId,
        List<FieldViolation> violations
) {
    /** Detail d'une violation de validation, champ par champ. */
    public record FieldViolation(String field, String message) {
    }
}
