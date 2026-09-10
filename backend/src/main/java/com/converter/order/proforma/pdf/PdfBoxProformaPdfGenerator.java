package com.converter.order.proforma.pdf;

import com.converter.common.exception.BusinessException;
import com.converter.common.exception.ErrorCode;
import com.converter.order.domain.BeneficiaryType;
import com.converter.order.proforma.model.ProformaInvoiceModel;
import com.converter.order.receipt.model.ReceiptBeneficiary;
import com.converter.order.receipt.pdf.PdfBoxReceiptPdfGenerator;
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
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Rendu texte simple (pas de HTML/CSS) via Apache PDFBox — meme approche que
 * {@link PdfBoxReceiptPdfGenerator}. Un seul document A4, sections separees, aucun recalcul (le
 * modele est deja construit). Le formatage des montants est delegue a
 * {@link PdfBoxReceiptPdfGenerator#formatAmount} (2 decimales, separateur ASCII) pour rester
 * strictement identique au justificatif.
 */
@Component
public class PdfBoxProformaPdfGenerator implements ProformaPdfGenerator {

    private static final float MARGIN = 50f;
    private static final float LINE_HEIGHT = 16f;
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    @Override
    public byte[] generate(ProformaInvoiceModel model) {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                Writer writer = new Writer(stream, regular, bold, page.getMediaBox().getHeight() - MARGIN);

                writer.title("FACTURE PROFORMA");
                writer.line("Numero", model.invoiceNumber());
                writer.line("Emise le", format(model.issuedAt()));
                writer.blank();

                writer.section("EMETTEUR");
                writer.line("Prestataire", "Converter");
                writer.line("Objet", "Paiement transfrontalier de fournisseur (corridor " + model.currencyPair() + ")");

                writer.section("ACHETEUR");
                writer.line("Nom", model.buyerName());
                if (notBlank(model.buyerBusinessName())) {
                    writer.line("Raison sociale", model.buyerBusinessName());
                }
                if (notBlank(model.buyerRegistrationNumber())) {
                    writer.line("Immatriculation", model.buyerRegistrationNumber());
                }
                if (notBlank(model.buyerAddress())) {
                    writer.line("Adresse", model.buyerAddress());
                }

                writer.section("REFERENCE");
                writer.line("Ordre", model.orderReference());
                writer.line("Statut", statusLabel(model.orderStatus()));
                if (model.purpose() != null) {
                    writer.line("Motif", purposeLabel(model.purpose()));
                }
                if (notBlank(model.purposeDetails())) {
                    writer.line("Details", model.purposeDetails());
                }

                writer.section("DETAIL");
                writer.line("Montant envoye", amount(model.amountXof()) + " XOF");
                writer.line("Frais de service", amount(model.feeXof()) + " XOF");
                writer.line("Net converti", amount(model.netAmountXof()) + " XOF");
                writer.line("Taux applique", amount(model.customerRate()) + " " + model.currencyPair());
                writer.line("Montant a recevoir", amount(model.amountCny()) + " CNY");

                writer.section("BENEFICIAIRE");
                writeBeneficiary(writer, model.beneficiary());

                writer.blank();
                writer.footer("Document proforma -- sans valeur d'acquittement. Emis automatiquement par Converter.");
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Echec de generation de la facture proforma.");
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

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String amount(java.math.BigDecimal value) {
        return PdfBoxReceiptPdfGenerator.formatAmount(value);
    }

    private static String format(Instant instant) {
        return DATE_FORMAT.format(instant);
    }

    private static String statusLabel(com.converter.order.domain.OrderStatus status) {
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

    /** Curseur d'ecriture texte simple : position Y geree manuellement, aucune pagination. */
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
