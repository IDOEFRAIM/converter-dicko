package com.converter.storage;

import org.springframework.core.io.Resource;

/**
 * Contenu d'un fichier de preuve (paiement ou reglement) pret a etre servi, accompagne de son
 * type MIME reel — verifie par signature binaire a l'upload ({@link FileValidator}, allowlist
 * stricte image/PDF), jamais le seul en-tete declare par le client.
 */
public record ProofDownload(Resource resource, String contentType, String fileName) {
}
