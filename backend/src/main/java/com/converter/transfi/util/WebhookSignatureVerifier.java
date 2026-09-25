package com.converter.transfi.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Verification HMAC-SHA256 d'une signature de webhook TransFi — comparaison en temps constant
 * ({@link java.security.MessageDigest#isEqual}) pour ne jamais laisser fuiter, par le temps de
 * reponse, la position du premier octet divergent (attaque par canal auxiliaire classique sur une
 * comparaison de signature naive).
 *
 * <p><b>En-tete et encodage provisoires</b> ({@code X-Transfi-Signature}, hex) : a confirmer
 * contre la documentation officielle TransFi (peut-etre base64, peut-etre un autre nom d'en-tete,
 * peut-etre un prefixe de type {@code sha256=...} comme chez Stripe/GitHub) avant activation en
 * production — voir {@code docs/TRANSFI_INTEGRATION.md}.
 */
public final class WebhookSignatureVerifier {

    private static final String ALGORITHM = "HmacSHA256";

    private WebhookSignatureVerifier() {
    }

    /**
     * @param rawBody          corps brut de la requete, EXACTEMENT tel que recu (avant tout
     *                         parsing JSON — une re-serialisation change potentiellement l'ordre
     *                         des champs ou les espaces, et donc la signature)
     * @param providedSignature valeur de l'en-tete de signature, en hexadecimal
     * @param secret           secret partage (voir {@code TransFiProperties.webhookSecret})
     */
    public static boolean isValid(byte[] rawBody, String providedSignature, String secret) {
        if (rawBody == null || providedSignature == null || providedSignature.isBlank()
                || secret == null || secret.isBlank()) {
            return false;
        }
        byte[] expected;
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            expected = mac.doFinal(rawBody);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HmacSHA256 indisponible sur cette JVM.", e);
        }
        byte[] provided;
        try {
            provided = HexFormat.of().parseHex(providedSignature.trim());
        } catch (IllegalArgumentException e) {
            // Signature fournie non-hexadecimale : jamais valide, jamais une exception qui
            // ferait echouer la requete autrement qu'en "signature invalide".
            return false;
        }
        return java.security.MessageDigest.isEqual(expected, provided);
    }
}
