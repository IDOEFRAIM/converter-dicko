package com.converter.order.receipt.pdf;

import com.converter.common.exception.BusinessException;
import com.converter.common.exception.ErrorCode;
import com.converter.common.pdf.PdfDocumentWriter;
import com.converter.order.domain.BeneficiaryType;
import com.converter.order.domain.OrderStatus;
import com.converter.order.receipt.model.ReceiptBeneficiary;
import com.converter.order.receipt.model.ReceiptRefund;
import com.converter.order.receipt.model.TransferReceiptModel;
import com.converter.refund.domain.RefundStatus;
import com.converter.supplier.domain.Purpose;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Rendu du justificatif via {@link PdfDocumentWriter} (bandeau navy/or, sections, encart montant
 * -- voir {@link ReceiptPdfGenerator} pour la garantie "aucun recalcul"). Un seul document A4,
 * pagine automatiquement si un champ optionnel deborde (remboursement, motif detaille).
 *
 * <p>Police embarquee Unicode (Noto Sans SC, {@code PdfBrand}) plutot que Helvetica/WinAnsi :
 * un nom de beneficiaire chinois saisi en caracteres CJK faisait echouer la generation de TOUT
 * justificatif le concernant (voir {@code PdfBrand} pour le detail du bug).
 *
 * <p>Arrondi d'affichage a 2 decimales pour tous les montants/taux (ex. {@code customerRate}
 * persiste avec 6 decimales s'affiche {@code 84.20}) : concerne <b>uniquement</b> le rendu de
 * cette classe, jamais la valeur elle-meme, qui reste celle du modele, inchangee.
 */
@Component
public class PdfBoxReceiptPdfGenerator implements ReceiptPdfGenerator {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    @Override
    public byte[] generate(TransferReceiptModel receipt) {
        try (PDDocument document = new PDDocument()) {
            try (PdfDocumentWriter writer = new PdfDocumentWriter(document)) {
                writer.header("YUAN PAY BF", "Justificatif de transfert", "Ref. " + receipt.transactionReference(),
                        format(receipt.createdAt()));

                writer.highlight("Montant recu par le beneficiaire",
                        formatAmount(receipt.amountCny()) + " CNY",
                        "au taux de " + formatAmount(receipt.customerRate()) + " " + receipt.currencyPair());
                writer.blank();

                writer.section("Transaction");
                writer.row("Numero d'ordre", receipt.orderId().toString());
                writer.row("Reference", receipt.transactionReference());
                writer.row("Date", format(receipt.createdAt()));
                writer.row("Statut", statusLabel(receipt.orderStatus()));

                writer.section("Client");
                writer.row("Client", receipt.customerName());

                writer.section("Transfert");
                writer.row("Envoye", formatAmount(receipt.amountXof()) + " XOF");
                writer.row("Frais", formatAmount(receipt.feeXof()) + " XOF");
                writer.row("Net", formatAmount(receipt.netAmountXof()) + " XOF");
                writer.row("Taux client", formatAmount(receipt.customerRate()) + " " + receipt.currencyPair());
                writer.row("Recu", formatAmount(receipt.amountCny()) + " CNY");

                writer.section("Beneficiaire");
                writeBeneficiary(writer, receipt.beneficiary());

                if (receipt.purpose() != null || receipt.purposeDetails() != null) {
                    writer.section("Motif");
                    if (receipt.purpose() != null) {
                        writer.row("Motif", purposeLabel(receipt.purpose()));
                    }
                    if (receipt.purposeDetails() != null && !receipt.purposeDetails().isBlank()) {
                        writer.row("Details", receipt.purposeDetails());
                    }
                }

                writer.section("Paiement");
                writer.row("Verifie le", receipt.paymentVerifiedAt() == null ? null : format(receipt.paymentVerifiedAt()));

                writer.section("Reglement");
                writer.row("Reference", receipt.settlementReference());
                writer.row("Execute le", receipt.settlementExecutedAt() == null ? null : format(receipt.settlementExecutedAt()));

                if (receipt.refund() != null) {
                    writer.section("Remboursement");
                    writeRefund(writer, receipt.refund());
                }

                writer.footer("Document genere automatiquement -- YUAN PAY BF -- " + format(Instant.now()));
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Echec de generation du justificatif PDF.");
        }
    }

    private void writeBeneficiary(PdfDocumentWriter writer, ReceiptBeneficiary beneficiary) throws IOException {
        writer.row("Nom", beneficiary.fullName());
        writer.row("Type", beneficiaryTypeLabel(beneficiary.type()));
        if (beneficiary.bankName() != null) {
            writer.row("Banque", beneficiary.bankName());
        }
        if (beneficiary.bankBranch() != null) {
            writer.row("Agence", beneficiary.bankBranch());
        }
        writer.row("Compte", beneficiary.maskedIdentifier());
    }

    private void writeRefund(PdfDocumentWriter writer, ReceiptRefund refund) throws IOException {
        writer.row("Statut", refundStatusLabel(refund.status()));
        writer.row("Montant", formatAmount(refund.amountXof()) + " XOF");
        writer.row("Date", format(refund.date()));
        writer.row("Reference", refund.reference());
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

    public static String beneficiaryTypeLabel(BeneficiaryType type) {
        return switch (type) {
            case ALIPAY -> "Alipay";
            case WECHAT_PAY -> "WeChat Pay";
            case CHINESE_BANK_ACCOUNT -> "Compte bancaire chinois";
        };
    }

    public static String purposeLabel(Purpose purpose) {
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
}
