package com.converter.order.receipt.pdf;

import com.converter.common.exception.BusinessException;
import com.converter.common.exception.ErrorCode;
import com.converter.order.domain.BeneficiaryType;
import com.converter.order.domain.OrderStatus;
import com.converter.order.receipt.model.ReceiptBeneficiary;
import com.converter.order.receipt.model.ReceiptRefund;
import com.converter.order.receipt.model.TransferReceiptModel;
import com.converter.refund.domain.RefundStatus;
import com.converter.supplier.domain.Purpose;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Rendu texte simple (pas de HTML/CSS) via Apache PDFBox — voir {@link ReceiptPdfGenerator} pour la
 * garantie "aucun recalcul". Un seul document A4, sections separees par des lignes horizontales,
 * fidele a la structure minimale de la specification (section 18).
 *
 * <p>Arrondi d'affichage a 2 decimales pour tous les montants/taux (ex. {@code customerRate}
 * persiste avec 6 decimales s'affiche {@code 84.20}) : concerne <b>uniquement</b> le rendu texte de
 * cette classe, jamais la valeur elle-meme, qui reste celle du modele, inchangee.
 */
@Component
public class PdfBoxReceiptPdfGenerator implements ReceiptPdfGenerator {

    private static final float MARGIN = 50f;
    private static final float LINE_HEIGHT = 16f;
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    @Override
    public byte[] generate(TransferReceiptModel receipt) {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                Writer writer = new Writer(stream, regular, bold, page.getMediaBox().getHeight() - MARGIN);

                writer.title("JUSTIFICATIF DE TRANSFERT");
                writer.blank();

                writer.section("TRANSACTION");
                writer.line("Numero d'ordre", receipt.orderId().toString());
                writer.line("Reference", receipt.transactionReference());
                writer.line("Date", format(receipt.createdAt()));
                writer.line("Statut", statusLabel(receipt.orderStatus()));

                writer.section("CLIENT");
                writer.line("Client", receipt.customerName());

                writer.section("TRANSFERT");
                writer.line("Envoye", formatAmount(receipt.amountXof()) + " XOF");
                writer.line("Frais", formatAmount(receipt.feeXof()) + " XOF");
                writer.line("Net", formatAmount(receipt.netAmountXof()) + " XOF");
                writer.line("Taux client", formatAmount(receipt.customerRate()) + " " + receipt.currencyPair());
                writer.line("Recu", formatAmount(receipt.amountCny()) + " CNY");

                writer.section("BENEFICIAIRE");
                writeBeneficiary(writer, receipt.beneficiary());

                if (receipt.purpose() != null || receipt.purposeDetails() != null) {
                    writer.section("MOTIF");
                    if (receipt.purpose() != null) {
                        writer.line("Motif", purposeLabel(receipt.purpose()));
                    }
                    if (receipt.purposeDetails() != null && !receipt.purposeDetails().isBlank()) {
                        writer.line("Details", receipt.purposeDetails());
                    }
                }

                writer.section("PAIEMENT");
                writer.line("Reference", nullSafe(receipt.paymentReference()));
                writer.line("Verifie le", receipt.paymentVerifiedAt() == null ? "-" : format(receipt.paymentVerifiedAt()));

                writer.section("REGLEMENT");
                writer.line("Reference", nullSafe(receipt.settlementReference()));
                writer.line("Execute le", receipt.settlementExecutedAt() == null ? "-" : format(receipt.settlementExecutedAt()));

                if (receipt.refund() != null) {
                    writer.section("REMBOURSEMENT");
                    writeRefund(writer, receipt.refund());
                }

                writer.blank();
                writer.footer("Document genere automatiquement -- Converter");
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Echec de generation du justificatif PDF.");
        }
    }

    private void writeBeneficiary(Writer writer, ReceiptBeneficiary beneficiary) throws IOException {
        writer.line("Nom", beneficiary.fullName());
        writer.line("Type", beneficiaryTypeLabel(beneficiary.type()));
        if (beneficiary.bankName() != null) {
            writer.line("Banque", beneficiary.bankName());
        }
        if (beneficiary.bankBranch() != null) {
            writer.line("Agence", beneficiary.bankBranch());
        }
        writer.line("Compte", beneficiary.maskedIdentifier());
    }

    private void writeRefund(Writer writer, ReceiptRefund refund) throws IOException {
        writer.line("Statut", refundStatusLabel(refund.status()));
        writer.line("Montant", formatAmount(refund.amountXof()) + " XOF");
        writer.line("Date", format(refund.date()));
        writer.line("Reference", nullSafe(refund.reference()));
    }

    private static String nullSafe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static String format(Instant instant) {
        return DATE_FORMAT.format(instant);
    }

    /**
     * Arrondi d'affichage a 2 decimales, separateur de milliers ASCII (espace) — jamais un
     * caractere Unicode (ex. espace insecable fine) : {@code WinAnsiEncoding} (police standard
     * PDF) ne le supporte pas et ferait echouer le rendu.
     */
    public static String formatAmount(BigDecimal value) {
        BigDecimal scaled = value.setScale(2, RoundingMode.HALF_UP);
        String plain = scaled.toPlainString();
        int dot = plain.indexOf('.');
        String intPart = plain.substring(0, dot);
        String decimalPart = plain.substring(dot + 1);
        boolean negative = intPart.startsWith("-");
        if (negative) {
            intPart = intPart.substring(1);
        }
        StringBuilder grouped = new StringBuilder();
        int count = 0;
        for (int i = intPart.length() - 1; i >= 0; i--) {
            grouped.append(intPart.charAt(i));
            count++;
            if (count % 3 == 0 && i != 0) {
                grouped.append(' ');
            }
        }
        return (negative ? "-" : "") + grouped.reverse() + "." + decimalPart;
    }

    // Les codes metier restent stables (enum) ; seuls ces libelles peuvent evoluer (section 19).

    private static String statusLabel(OrderStatus status) {
        return switch (status) {
            case AWAITING_PAYMENT -> "En attente de paiement";
            case PAYMENT_SUBMITTED -> "Paiement declare";
            case PAYMENT_VERIFIED -> "Paiement verifie";
            case PROCESSING -> "En cours de traitement";
            case COMPLETED -> "Termine";
            case CANCELLED -> "Annule";
            case REJECTED -> "Rejete";
            case EXPIRED -> "Expire";
        };
    }

    private static String refundStatusLabel(RefundStatus status) {
        return switch (status) {
            case PENDING -> "En attente";
            case PROCESSED -> "Traite";
            case REJECTED -> "Rejete";
        };
    }

    private static String beneficiaryTypeLabel(BeneficiaryType type) {
        return switch (type) {
            case ALIPAY -> "Alipay";
            case WECHAT_PAY -> "WeChat Pay";
            case CHINESE_BANK_ACCOUNT -> "Compte bancaire chinois";
        };
    }

    private static String purposeLabel(Purpose purpose) {
        return switch (purpose) {
            case PERSONAL -> "Personnel";
            case EDUCATION -> "Education";
            case FAMILY_SUPPORT -> "Soutien familial";
            case IMPORT_GOODS -> "Importation de marchandises";
            case SERVICES -> "Services";
            case BUSINESS -> "Affaires";
            case OTHER -> "Autre";
        };
    }

    /** Curseur d'ecriture texte simple : position Y geree manuellement, aucune pagination (recu court, une page). */
    private static final class Writer {
        private final PDPageContentStream stream;
        private final PDFont regular;
        private final PDFont bold;
        private float y;

        Writer(PDPageContentStream stream, PDFont regular, PDFont bold, float startY) {
            this.stream = stream;
            this.regular = regular;
            this.bold = bold;
            this.y = startY;
        }

        void title(String text) throws IOException {
            write(bold, 16, text);
            y -= LINE_HEIGHT;
        }

        void section(String text) throws IOException {
            y -= LINE_HEIGHT / 2;
            write(bold, 11, text);
            y -= 2;
        }

        void line(String label, String value) throws IOException {
            write(regular, 10, label + " : " + value);
        }

        void footer(String text) throws IOException {
            write(regular, 8, text);
        }

        void blank() {
            y -= LINE_HEIGHT / 2;
        }

        private void write(PDFont font, int size, String text) throws IOException {
            stream.beginText();
            stream.setFont(font, size);
            stream.newLineAtOffset(MARGIN, y);
            stream.showText(text);
            stream.endText();
            y -= LINE_HEIGHT;
        }
    }
}
