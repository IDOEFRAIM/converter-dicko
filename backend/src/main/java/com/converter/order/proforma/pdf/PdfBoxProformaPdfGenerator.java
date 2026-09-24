package com.converter.order.proforma.pdf;

import com.converter.common.exception.BusinessException;
import com.converter.common.exception.ErrorCode;
import com.converter.common.pdf.PdfDocumentWriter;
import com.converter.order.domain.OrderStatus;
import com.converter.order.proforma.model.ProformaInvoiceModel;
import com.converter.order.receipt.model.ReceiptBeneficiary;
import com.converter.order.receipt.pdf.PdfBoxReceiptPdfGenerator;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Rendu de la facture proforma via {@link PdfDocumentWriter} — meme moteur de mise en page que
 * {@link PdfBoxReceiptPdfGenerator} (bandeau navy/or, sections, police Unicode Noto Sans SC),
 * pour une identite visuelle identique sur tous les documents telechargeables. Le formatage des
 * montants et les libelles partages (type de beneficiaire) restent delegues a
 * {@link PdfBoxReceiptPdfGenerator} pour rester strictement identiques au justificatif.
 */
@Component
public class PdfBoxProformaPdfGenerator implements ProformaPdfGenerator {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    @Override
    public byte[] generate(ProformaInvoiceModel model) {
        try (PDDocument document = new PDDocument()) {
            try (PdfDocumentWriter writer = new PdfDocumentWriter(document)) {
                writer.header("YUAN PAY BF", "Facture proforma", "N. " + model.invoiceNumber(), format(model.issuedAt()));

                writer.highlight("Montant a recevoir",
                        amount(model.amountCny()) + " CNY",
                        "au taux de " + amount(model.customerRate()) + " " + model.currencyPair());
                writer.blank();

                writer.section("Emetteur");
                writer.row("Prestataire", "YUAN PAY BF");
                writer.row("Objet", "Paiement transfrontalier de fournisseur (corridor " + model.currencyPair() + ")");

                writer.section("Acheteur");
                writer.row("Nom", model.buyerName());
                writer.row("Raison sociale", model.buyerBusinessName());
                writer.row("Immatriculation", model.buyerRegistrationNumber());
                writer.row("Adresse", model.buyerAddress());

                writer.section("Reference");
                writer.row("Ordre", model.orderReference());
                writer.row("Statut", statusLabel(model.orderStatus()));
                if (model.purpose() != null) {
                    writer.row("Motif", PdfBoxReceiptPdfGenerator.purposeLabel(model.purpose()));
                }
                writer.row("Details", model.purposeDetails());

                writer.section("Detail");
                writer.row("Montant envoye", amount(model.amountXof()) + " XOF");
                writer.row("Frais de service", amount(model.feeXof()) + " XOF");
                writer.row("Net converti", amount(model.netAmountXof()) + " XOF");
                writer.row("Taux applique", amount(model.customerRate()) + " " + model.currencyPair());
                writer.row("Montant a recevoir", amount(model.amountCny()) + " CNY");

                writer.section("Beneficiaire");
                writeBeneficiary(writer, model.beneficiary());

                writer.footer("Document proforma -- sans valeur d'acquittement -- YUAN PAY BF -- " + format(Instant.now()));
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Echec de generation de la facture proforma.");
        }
    }

    private void writeBeneficiary(PdfDocumentWriter writer, ReceiptBeneficiary beneficiary) throws IOException {
        writer.row("Nom", beneficiary.fullName());
        writer.row("Type", PdfBoxReceiptPdfGenerator.beneficiaryTypeLabel(beneficiary.type()));
        if (beneficiary.bankName() != null) {
            writer.row("Banque", beneficiary.bankName());
        }
        if (beneficiary.bankBranch() != null) {
            writer.row("Agence", beneficiary.bankBranch());
        }
        writer.row("Compte", beneficiary.maskedIdentifier());
    }

    private static String amount(BigDecimal value) {
        return PdfBoxReceiptPdfGenerator.formatAmount(value);
    }

    private static String format(Instant instant) {
        return DATE_FORMAT.format(instant);
    }

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
}
