package com.converter.order.receipt.model;

/** Bytes PDF generes a la demande (section 20, option A — aucune persistance) et nom de fichier suggere. */
public record ReceiptDocument(byte[] content, String fileName) {
}
