package com.converter.order.receipt.pdf;

import com.converter.order.receipt.model.TransferReceiptModel;

/**
 * Rendu documentaire pur : transforme un {@link TransferReceiptModel} deja construit en bytes PDF.
 * Ne connait <b>aucun</b> repository ni service financier — uniquement le modele qu'on lui passe
 * (voir {@code OrderReceiptService}, seul appelant). Aucun recalcul possible ici par construction :
 * cette classe n'a tout simplement pas acces aux donnees necessaires pour en faire un.
 */
public interface ReceiptPdfGenerator {

    byte[] generate(TransferReceiptModel receipt);
}
