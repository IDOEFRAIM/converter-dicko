package com.converter.storage;

import com.converter.storage.exception.InvalidFileException;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;

/**
 * Valide un fichier candidat avant stockage.
 *
 * <p>Ne fait jamais confiance au seul en-tete {@code Content-Type}
 * fourni par le client : le contenu est inspecte (signature binaire,
 * "magic bytes") pour confirmer qu'il correspond reellement a un type
 * autorise. Un fichier dont l'en-tete annonce {@code image/png} mais
 * dont le contenu est autre chose est rejete.
 */
@Component
public class FileValidator {

    /** Allowlist stricte — voir docs/ARCHITECTURE.md, Partie II, section J.2 (risque 10). */
    public static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "application/pdf");

    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] PDF_MAGIC = {0x25, 0x50, 0x44, 0x46};
    private static final byte[] RIFF_MAGIC = {0x52, 0x49, 0x46, 0x46};
    private static final byte[] WEBP_MAGIC = {0x57, 0x45, 0x42, 0x50};

    /**
     * @param declaredContentType en-tete {@code Content-Type} annonce par le client
     * @param content             contenu integral du fichier
     * @param maxSizeBytes        taille maximale autorisee
     * @return le type MIME reellement detecte (peut differer du declare si l'appelant l'ignore)
     * @throws InvalidFileException si le type n'est pas autorise, si la signature ne correspond
     *                              pas au type declare, ou si la taille depasse la limite
     */
    public String validate(String declaredContentType, byte[] content, long maxSizeBytes) {
        if (content == null || content.length == 0) {
            throw new InvalidFileException("Le fichier est vide.");
        }
        if (content.length > maxSizeBytes) {
            throw new InvalidFileException("Le fichier depasse la taille maximale autorisee ("
                    + maxSizeBytes + " octets).");
        }
        if (declaredContentType == null || !ALLOWED_CONTENT_TYPES.contains(declaredContentType)) {
            throw new InvalidFileException("Type de fichier non autorise : " + declaredContentType
                    + ". Formats acceptes : " + ALLOWED_CONTENT_TYPES);
        }

        String detected = detectContentType(content);
        if (detected == null || !detected.equals(declaredContentType)) {
            // Le Content-Type annonce ne correspond pas au contenu reel :
            // rejet systematique, sans tenter de "deviner" une intention.
            throw new InvalidFileException(
                    "Le contenu du fichier ne correspond pas au type annonce (" + declaredContentType + ").");
        }
        return detected;
    }

    /**
     * Type MIME deduit des seules signatures binaires (magic bytes), ou {@code null}
     * si aucune ne correspond. Ne valide rien, ne leve pas : sert a re-etiqueter un
     * fichier deja stocke au moment de le servir (ex. pieces KYC), pour qu'un
     * navigateur puisse l'afficher plutot que de le traiter comme un binaire opaque.
     */
    public String sniffContentType(byte[] header) {
        return detectContentType(header);
    }

    private String detectContentType(byte[] content) {
        if (startsWith(content, JPEG_MAGIC)) {
            return "image/jpeg";
        }
        if (startsWith(content, PNG_MAGIC)) {
            return "image/png";
        }
        if (startsWith(content, PDF_MAGIC)) {
            return "application/pdf";
        }
        if (startsWith(content, RIFF_MAGIC) && content.length >= 12
                && matchesAt(content, 8, WEBP_MAGIC)) {
            return "image/webp";
        }
        return null;
    }

    private boolean startsWith(byte[] content, byte[] magic) {
        return matchesAt(content, 0, magic);
    }

    private boolean matchesAt(byte[] content, int offset, byte[] magic) {
        if (content.length < offset + magic.length) {
            return false;
        }
        return Arrays.equals(content, offset, offset + magic.length, magic, 0, magic.length);
    }
}
