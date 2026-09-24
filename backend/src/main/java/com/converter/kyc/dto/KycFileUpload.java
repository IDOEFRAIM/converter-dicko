package com.converter.kyc.dto;

/**
 * Fichier deja lu en memoire, transmis par le controleur au service (le service ne connait jamais
 * {@code MultipartFile} ni {@code IOException}).
 */
public record KycFileUpload(String originalName, String contentType, byte[] content) {
}
