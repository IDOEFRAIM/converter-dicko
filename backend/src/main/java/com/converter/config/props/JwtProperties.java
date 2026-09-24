package com.converter.config.props;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Parametres de signature et de duree de vie des jetons d'acces.
 *
 * <p>{@code secret} est annote {@link NotBlank} : en profil {@code prod},
 * aucune valeur par defaut n'est fournie, donc l'application echoue au
 * demarrage si {@code JWT_SECRET} est absent. C'est volontaire — mieux
 * vaut un refus de demarrage qu'un service signant avec une cle devinee.
 */
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(

        @NotBlank(message = "app.jwt.secret est obligatoire (variable d'environnement JWT_SECRET)")
        String secret,

        @Min(value = 1, message = "app.jwt.expiration-minutes doit valoir au moins 1")
        long expirationMinutes,

        @NotBlank
        String issuer
) {
    /** Longueur minimale imposee par HMAC-SHA256 (RFC 7518, section 3.2). */
    public static final int MIN_SECRET_LENGTH = 32;
}
