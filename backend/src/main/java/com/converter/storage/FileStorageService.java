package com.converter.storage;

import org.springframework.core.io.Resource;

/**
 * Port de stockage de fichiers (preuves de paiement, preuves de
 * reglement). Le domaine ne depend jamais d'une implementation
 * concrete.
 *
 * <pre>
 * FileStorageService
 *     |-- LocalFileStorageService   (MVP)
 *     `-- S3FileStorageService      (futur — meme contrat, aucun changement d'appelant)
 * </pre>
 */
public interface FileStorageService {

    /**
     * Stocke le contenu sous un chemin logique genere par
     * l'implementation (jamais a partir du nom fourni par le client).
     *
     * @param directory        sous-repertoire logique, ex. {@code "payment-proofs"}
     * @param originalFileName nom fourni par le client — utilise uniquement pour en deriver
     *                         une extension, jamais comme chemin
     */
    StoredFile store(String directory, String originalFileName, String contentType, byte[] content);

    Resource load(String storageKey);

    void delete(String storageKey);
}
