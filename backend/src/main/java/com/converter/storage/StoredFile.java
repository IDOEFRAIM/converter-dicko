package com.converter.storage;

/**
 * Metadonnees d'un fichier apres stockage — jamais le contenu binaire
 * lui-meme, qui ne transite jamais par la base de donnees.
 */
public record StoredFile(
        String storageKey,
        String fileName,
        String contentType,
        long sizeBytes,
        String checksumSha256
) {
}
