package com.converter.kyc.dto;

import org.springframework.core.io.Resource;

/**
 * Un fichier d'un dossier KYC pret a etre servi, accompagne du type MIME reel
 * deduit de son contenu — pour que l'administrateur voie la piece (image / PDF)
 * dans son navigateur au lieu d'un binaire opaque telecharge de force.
 */
public record KycFileDownload(Resource resource, String contentType) {
}
