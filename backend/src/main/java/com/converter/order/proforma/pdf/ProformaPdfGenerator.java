package com.converter.order.proforma.pdf;

import com.converter.order.proforma.model.ProformaInvoiceModel;

/**
 * Rendu documentaire pur : transforme un {@link ProformaInvoiceModel} deja construit en bytes PDF.
 * Ne connait aucun repository ni service financier — uniquement le modele qu'on lui passe (voir
 * {@code OrderProformaService}, seul appelant). Aucun recalcul possible ici par construction.
 */
public interface ProformaPdfGenerator {

    byte[] generate(ProformaInvoiceModel model);
}
