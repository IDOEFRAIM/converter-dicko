package com.converter.common.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

import java.io.IOException;
import java.io.InputStream;

/**
 * Polices et couleurs communes a tous les documents PDF telechargeables (justificatif, proforma).
 *
 * <p>Noto Sans SC (Latin + CJK) remplace les polices standard PDF (Helvetica...), qui ne couvrent
 * que {@code WinAnsiEncoding} : le nom d'un beneficiaire chinois saisi en caracteres CJK faisait
 * jusque-la echouer {@code PDPageContentStream.showText} avec une {@code IllegalArgumentException}
 * -- jamais rattrapee (le code n'attrapait que {@code IOException}) -- sur CHAQUE generation de
 * document concernant ce beneficiaire (justificatif, proforma). Un seul document a un beneficiaire
 * chinois cassait donc systematiquement le telechargement, jamais un cas rare.
 *
 * <p>{@link PDFont} est lie a un {@link PDDocument} precis (glyphes subsetes par document) : ne
 * jamais mettre en cache une instance au niveau classe, recharger a chaque generation via
 * {@link #regular(PDDocument)}/{@link #bold(PDDocument)}.
 */
public final class PdfBrand {

    private PdfBrand() {
    }

    public static PDFont regular(PDDocument document) throws IOException {
        return load(document, "/fonts/NotoSansSC-Regular.ttf");
    }

    public static PDFont bold(PDDocument document) throws IOException {
        return load(document, "/fonts/NotoSansSC-Bold.ttf");
    }

    /**
     * TrueType a contours {@code glyf} (pas OpenType/CFF) : instances statiques generees par
     * {@code fonttools varLib.instancer} (wght=400/700) a partir du variable font Noto Sans SC de
     * Google Fonts -- la distribution CFF habituelle de Noto Sans SC ({@code SubsetOTF/...}) fait
     * echouer le subsetting de PDFBox 3.0.3 ({@code TTFSubsetter} ne gere que {@code glyf}, pas
     * CFF : {@code UnsupportedOperationException: OTF fonts do not have a glyf table}), ce qui
     * forcerait a embarquer la police entiere (~8 Mo) dans chaque PDF au lieu d'un sous-ensemble
     * de quelques Ko.
     */
    private static PDFont load(PDDocument document, String resourcePath) throws IOException {
        try (InputStream in = PdfBrand.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IOException("Police introuvable sur le classpath : " + resourcePath);
            }
            return PDType0Font.load(document, in, true);
        }
    }

    // Meme identite que --brand-navy/--brand-gold/--brand-red (frontend Angular, styles.scss) et
    // AppColors.lacquer (mobile) -- les documents telechargeables partagent la palette du produit
    // plutot qu'un gris/noir generique.
    public static final float[] NAVY = rgb(0x1B, 0x32, 0x5E);
    public static final float[] NAVY_DARK = rgb(0x0F, 0x1E, 0x3B);
    public static final float[] GOLD = rgb(0xD3, 0xAF, 0x37);
    public static final float[] INK = rgb(0x22, 0x26, 0x2E);
    public static final float[] MUTED = rgb(0x6B, 0x72, 0x80);
    public static final float[] MUTED_ON_DARK = rgb(0xB9, 0xC4, 0xDC);
    public static final float[] DIVIDER = rgb(0xE3, 0xE6, 0xEC);
    public static final float[] ROW_ALT = rgb(0xF6, 0xF7, 0xFA);
    public static final float[] WHITE = rgb(0xFF, 0xFF, 0xFF);

    private static float[] rgb(int r, int g, int b) {
        return new float[] {r / 255f, g / 255f, b / 255f};
    }
}
