package com.converter.common.util;

/**
 * Construction minimale de fragments JSON pour les colonnes {@code jsonb} d'audit
 * ({@code audit_logs.metadata}), sans dependance a Jackson pour un simple objet plat.
 *
 * <p>Doit rester conforme RFC 8259 : Postgres rejette (avec un {@code DataIntegrityViolationException},
 * 500) tout caractere de controle (0x00-0x1F, dont saut de ligne et tabulation) non echappe a
 * l'interieur d'une chaine JSON. Une valeur libre saisie par un utilisateur ou un administrateur
 * (motif de rejet/blocage, note...) peut legitimement contenir un saut de ligne — l'echappement
 * doit donc couvrir tous les caracteres de controle, pas seulement le guillemet et l'antislash.
 */
public final class JsonUtil {

    private JsonUtil() {
    }

    /** Echappe {@code raw} et l'entoure de guillemets, prete a etre inseree dans un fragment JSON. */
    public static String jsonString(String raw) {
        StringBuilder escaped = new StringBuilder(raw.length() + 2).append('"');
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                default -> {
                    if (c < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) c));
                    } else {
                        escaped.append(c);
                    }
                }
            }
        }
        return escaped.append('"').toString();
    }
}
