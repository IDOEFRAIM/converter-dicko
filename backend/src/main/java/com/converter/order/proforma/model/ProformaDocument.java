package com.converter.order.proforma.model;

/** Bytes PDF d'une facture proforma, generes a la demande (aucune persistance), + nom de fichier suggere. */
public record ProformaDocument(byte[] content, String fileName) {
}
