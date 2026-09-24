package com.converter.storage;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Derive une extension sure a partir d'un type MIME deja valide, sans
 * jamais faire confiance au nom de fichier fourni par le client.
 *
 * <p>Le nom original n'est conserve que pour l'affichage (colonne
 * {@code file_name}), assaini de tout caractere de controle ou de
 * separateur de chemin — il n'entre jamais dans la construction du
 * {@code storageKey} (voir {@link LocalFileStorageService}).
 */
final class FileNameSanitizer {

    private static final Map<String, String> EXTENSION_BY_CONTENT_TYPE = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "application/pdf", "pdf");

    private static final Pattern UNSAFE_CHARACTERS = Pattern.compile("[^a-zA-Z0-9._-]");

    private FileNameSanitizer() {
    }

    static String extensionFor(String contentType) {
        return EXTENSION_BY_CONTENT_TYPE.getOrDefault(contentType, "bin");
    }

    /** Nom affichable, sans separateur de chemin ni caractere de controle. */
    static String sanitizeDisplayName(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            return "fichier";
        }
        String baseName = originalFileName.replace("\\", "/");
        int lastSlash = baseName.lastIndexOf('/');
        if (lastSlash >= 0) {
            baseName = baseName.substring(lastSlash + 1);
        }
        String cleaned = UNSAFE_CHARACTERS.matcher(baseName).replaceAll("_");
        return cleaned.length() > 200 ? cleaned.substring(0, 200) : cleaned;
    }
}
