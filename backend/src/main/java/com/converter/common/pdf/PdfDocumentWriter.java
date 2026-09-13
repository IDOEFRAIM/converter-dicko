package com.converter.common.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;

import java.io.Closeable;
import java.io.IOException;

/**
 * Moteur de mise en page partage par tous les documents PDF telechargeables (justificatif,
 * proforma) : un seul style visuel (bandeau d'en-tete navy/or, sections, lignes label/valeur
 * alignees en colonnes, encart montant) au lieu d'un "Writer" texte brut duplique par generateur
 * (chacun ecrivait "Label : valeur" en texte libre, sans alignement -- l'origine du rendu juge
 * "vilain").
 *
 * <p>Pagine automatiquement si le contenu deborde ({@link #ensureSpace}) : aucun document n'est
 * cense depasser une page (recu court), mais un champ optionnel plus long (adresse, details de
 * motif, remboursement) ne doit jamais faire chevaucher ou couper du texte en silence -- c'etait
 * le cas avant (curseur Y géré manuellement, aucune verification de debordement).
 */
public final class PdfDocumentWriter implements Closeable {

    private static final float MARGIN = 50f;
    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
    private static final float CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN;
    private static final float LABEL_COLUMN = 165f;
    private static final float HEADER_HEIGHT = 92f;
    private static final float BOTTOM_LIMIT = 70f;
    private static final float LINE_HEIGHT = 16f;

    private final PDDocument document;
    private final PDFont regular;
    private final PDFont bold;
    private PDPageContentStream stream;
    private float y;
    private boolean rowStripe;

    public PdfDocumentWriter(PDDocument document) throws IOException {
        this.document = document;
        this.regular = PdfBrand.regular(document);
        this.bold = PdfBrand.bold(document);
        newPage();
    }

    /** Bandeau d'en-tete navy pleine largeur : titre du document, reference et date a droite. */
    public void header(String kicker, String title, String reference, String date) throws IOException {
        fillRect(0, PAGE_HEIGHT - HEADER_HEIGHT, PAGE_WIDTH, HEADER_HEIGHT, PdfBrand.NAVY_DARK);
        drawText(bold, 9, MARGIN, PAGE_HEIGHT - 26, PdfBrand.GOLD, kicker.toUpperCase());
        drawText(bold, 18, MARGIN, PAGE_HEIGHT - 52, PdfBrand.WHITE, title);
        if (reference != null && !reference.isBlank()) {
            drawTextRightAligned(regular, 10, PAGE_WIDTH - MARGIN, PAGE_HEIGHT - 30, PdfBrand.WHITE, reference);
        }
        if (date != null && !date.isBlank()) {
            drawTextRightAligned(regular, 9, PAGE_WIDTH - MARGIN, PAGE_HEIGHT - 46, PdfBrand.MUTED_ON_DARK, date);
        }
        fillRect(0, PAGE_HEIGHT - HEADER_HEIGHT - 3, PAGE_WIDTH, 3, PdfBrand.GOLD);
        y = PAGE_HEIGHT - HEADER_HEIGHT - 30;
    }

    /** Titre de section en navy avec une regle fine en dessous. */
    public void section(String label) throws IOException {
        ensureSpace(LINE_HEIGHT * 2f);
        y -= 6;
        drawText(bold, 11, MARGIN, y, PdfBrand.NAVY, label.toUpperCase());
        y -= 5;
        strokeLine(MARGIN, y, MARGIN + CONTENT_WIDTH, y, PdfBrand.DIVIDER, 0.75f);
        y -= 12;
        rowStripe = false;
    }

    /** Ligne label/valeur alignee en deux colonnes, avec bande alternee pour la lisibilite. */
    public void row(String label, String value) throws IOException {
        ensureSpace(LINE_HEIGHT);
        if (rowStripe) {
            fillRect(MARGIN - 6, y - 4, CONTENT_WIDTH + 12, LINE_HEIGHT, PdfBrand.ROW_ALT);
        }
        rowStripe = !rowStripe;
        drawText(regular, 10, MARGIN, y, PdfBrand.MUTED, label);
        drawText(regular, 10, MARGIN + LABEL_COLUMN, y, PdfBrand.INK, blankSafe(value));
        y -= LINE_HEIGHT;
    }

    /** Encart mis en avant (le montant recu par exemple) : cadre or, valeur en grand. */
    public void highlight(String label, String value, String sub) throws IOException {
        float boxHeight = 46f;
        ensureSpace(boxHeight + 14);
        strokeRect(MARGIN, y - boxHeight, CONTENT_WIDTH, boxHeight, PdfBrand.GOLD, 1.2f);
        drawText(bold, 9, MARGIN + 14, y - 17, PdfBrand.MUTED, label.toUpperCase());
        drawText(bold, 20, MARGIN + 14, y - 37, PdfBrand.NAVY, value);
        if (sub != null && !sub.isBlank()) {
            drawTextRightAligned(regular, 9, MARGIN + CONTENT_WIDTH - 14, y - 37, PdfBrand.MUTED, sub);
        }
        y -= boxHeight + 16;
    }

    public void blank() {
        y -= LINE_HEIGHT / 2f;
    }

    /** Pied de page discret, ancre pres du bas -- pas de superposition possible avec le contenu. */
    public void footer(String text) throws IOException {
        float footerY = BOTTOM_LIMIT - 14;
        strokeLine(MARGIN, footerY + 16, MARGIN + CONTENT_WIDTH, footerY + 16, PdfBrand.DIVIDER, 0.75f);
        drawText(regular, 8, MARGIN, footerY, PdfBrand.MUTED, text);
    }

    private void ensureSpace(float needed) throws IOException {
        if (y - needed < BOTTOM_LIMIT) {
            newPage();
        }
    }

    private void newPage() throws IOException {
        if (stream != null) {
            stream.close();
        }
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);
        stream = new PDPageContentStream(document, page);
        y = PAGE_HEIGHT - MARGIN;
        rowStripe = false;
    }

    private static String blankSafe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private void drawText(PDFont font, float size, float x, float yPos, float[] color, String value) throws IOException {
        stream.beginText();
        stream.setFont(font, size);
        stream.setNonStrokingColor(color[0], color[1], color[2]);
        stream.newLineAtOffset(x, yPos);
        stream.showText(value);
        stream.endText();
    }

    private void drawTextRightAligned(PDFont font, float size, float rightX, float yPos, float[] color, String value)
            throws IOException {
        float textWidth = font.getStringWidth(value) / 1000f * size;
        drawText(font, size, rightX - textWidth, yPos, color, value);
    }

    private void fillRect(float x, float yPos, float w, float h, float[] color) throws IOException {
        stream.setNonStrokingColor(color[0], color[1], color[2]);
        stream.addRect(x, yPos, w, h);
        stream.fill();
    }

    private void strokeRect(float x, float yPos, float w, float h, float[] color, float lineWidth) throws IOException {
        stream.setStrokingColor(color[0], color[1], color[2]);
        stream.setLineWidth(lineWidth);
        stream.addRect(x, yPos, w, h);
        stream.stroke();
    }

    private void strokeLine(float x1, float y1, float x2, float y2, float[] color, float lineWidth) throws IOException {
        stream.setStrokingColor(color[0], color[1], color[2]);
        stream.setLineWidth(lineWidth);
        stream.moveTo(x1, y1);
        stream.lineTo(x2, y2);
        stream.stroke();
    }

    @Override
    public void close() throws IOException {
        stream.close();
    }
}
