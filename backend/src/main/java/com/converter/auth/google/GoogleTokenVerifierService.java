package com.converter.auth.google;

import com.converter.common.exception.BusinessException;
import com.converter.common.exception.ErrorCode;
import com.converter.config.props.GoogleAuthProperties;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;

/**
 * Verification des jetons d'identite Google (connexion mobile "Continuer
 * avec Google") — delegue integralement a {@link GoogleIdTokenVerifier}
 * (bibliotheque officielle Google) : recuperation/rotation des cles
 * publiques (JWKS), verification de la signature RS256, de l'emetteur
 * ({@code accounts.google.com}), de l'audience (notre Client ID) et de
 * l'expiration. Reimplementer cela a la main serait un risque de securite
 * pour un gain nul.
 *
 * <p>Fonctionnalite optionnelle (voir {@link GoogleAuthProperties}) : sans
 * {@code GOOGLE_OAUTH_CLIENT_ID}, {@link #verify} refuse proprement (503)
 * plutot que de faire echouer le demarrage de toute l'application.
 */
@Service
public class GoogleTokenVerifierService {

    private static final Logger log = LoggerFactory.getLogger(GoogleTokenVerifierService.class);

    private final GoogleAuthProperties properties;
    private GoogleIdTokenVerifier verifier;

    public GoogleTokenVerifierService(GoogleAuthProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void init() {
        if (!properties.configured()) {
            log.info("Connexion Google non configuree (GOOGLE_OAUTH_CLIENT_ID absent) -- endpoint desactive.");
            return;
        }
        try {
            this.verifier = new GoogleIdTokenVerifier.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(), GsonFactory.getDefaultInstance())
                    .setAudience(Collections.singletonList(properties.clientId()))
                    .build();
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException("Impossible d'initialiser le verificateur Google Sign-In.", e);
        }
    }

    /**
     * @throws BusinessException {@code GOOGLE_SIGNIN_UNAVAILABLE} si la fonctionnalite n'est
     *                           pas configuree sur ce serveur ; {@code INVALID_CREDENTIALS} si
     *                           le jeton est invalide, expire, ou n'a pas notre Client ID pour
     *                           audience.
     */
    public GoogleIdentity verify(String idTokenString) {
        if (verifier == null) {
            throw new BusinessException(ErrorCode.GOOGLE_SIGNIN_UNAVAILABLE,
                    "La connexion Google n'est pas configuree sur ce serveur.");
        }

        GoogleIdToken idToken;
        try {
            idToken = verifier.verify(idTokenString);
        } catch (GeneralSecurityException | IOException | IllegalArgumentException e) {
            // Jeton malforme, cle introuvable, erreur reseau vers les cles publiques
            // Google... jamais distingue du cas "invalide" cote client (mission
            // section : jamais de fuite de detail technique dans un message d'erreur).
            log.debug("Jeton Google rejete lors de la verification", e);
            idToken = null;
        }

        if (idToken == null) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "Jeton Google invalide ou expire.");
        }

        GoogleIdToken.Payload payload = idToken.getPayload();
        boolean emailVerified = Boolean.TRUE.equals(payload.getEmailVerified());
        return new GoogleIdentity(
                payload.getSubject(),
                payload.getEmail(),
                emailVerified,
                (String) payload.get("given_name"),
                (String) payload.get("family_name"));
    }
}
