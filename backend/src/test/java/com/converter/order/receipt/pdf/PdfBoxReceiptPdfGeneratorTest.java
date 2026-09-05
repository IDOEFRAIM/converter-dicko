package com.converter.order.receipt.pdf;

import com.converter.order.domain.BeneficiaryType;
import com.converter.order.domain.OrderStatus;
import com.converter.order.receipt.model.ReceiptBeneficiary;
import com.converter.order.receipt.model.ReceiptRefund;
import com.converter.order.receipt.model.TransferReceiptModel;
import com.converter.refund.domain.RefundStatus;
import com.converter.supplier.domain.Purpose;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Section 37 : test de contenu texte du PDF (extraction via {@link PDFTextStripper}), pas
 * seulement une verification de taille/type — sans dependance a une position visuelle exacte.
 * Aucune base de donnees/Spring : ce generateur ne connait que le {@link TransferReceiptModel}
 * qu'on lui passe.
 */
class PdfBoxReceiptPdfGeneratorTest {

    private final PdfBoxReceiptPdfGenerator generator = new PdfBoxReceiptPdfGenerator();

    private String extractText(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private TransferReceiptModel fullModel(ReceiptRefund refund) {
        UUID orderId = UUID.randomUUID();
        ReceiptBeneficiary beneficiary = new ReceiptBeneficiary("Zhang San", BeneficiaryType.CHINESE_BANK_ACCOUNT,
                "******1111", "Bank of China", "Shanghai Branch");
        return new TransferReceiptModel(
                orderId, "ORD-2026-000123",
                Instant.parse("2026-09-01T10:00:00Z"), Instant.parse("2026-09-02T15:30:00Z"),
                OrderStatus.COMPLETED, "Ido Efraim",
                new BigDecimal("1000000.00"), new BigDecimal("12500.00"), new BigDecimal("987500.00"),
                new BigDecimal("84.200000"), new BigDecimal("11734.56"), "XOF/CNY",
                beneficiary, Purpose.IMPORT_GOODS, "Achat de materiel electronique",
                "MM-PAY-REF-42", Instant.parse("2026-09-01T12:00:00Z"),
                "CNY-PAYOUT-99", Instant.parse("2026-09-02T15:00:00Z"),
                refund);
    }

    @Test
    void generate_producesAValidPdf_startingWithPdfHeader() {
        byte[] pdf = generator.generate(fullModel(null));

        assertThat(pdf.length).isGreaterThan(4);
        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    void generate_containsOrderIdAmountsRateAndBeneficiary() throws IOException {
        TransferReceiptModel model = fullModel(null);
        String text = extractText(generator.generate(model));

        assertThat(text).contains(model.orderId().toString());
        assertThat(text).contains("ORD-2026-000123");
        assertThat(text).contains("1 000 000.00");
        assertThat(text).contains("12 500.00");
        assertThat(text).contains("987 500.00");
        assertThat(text).contains("84.20");
        assertThat(text).contains("11 734.56");
        assertThat(text).contains("Zhang San");
        assertThat(text).contains("******1111");
        assertThat(text).contains("Bank of China");
        assertThat(text).contains("Importation de marchandises");
        assertThat(text).contains("Achat de materiel electronique");
        assertThat(text).contains("MM-PAY-REF-42");
        assertThat(text).contains("CNY-PAYOUT-99");
    }

    @Test
    void generate_neverExposesUnmaskedAccountNumber() throws IOException {
        String text = extractText(generator.generate(fullModel(null)));

        // Le modele ne recoit deja qu'un identifiant masque (section 8) -- verifie ici que le
        // rendu ne fabrique jamais, meme par erreur, une version en clair a partir d'ailleurs.
        assertThat(text).doesNotContain("6222000000001111");
    }

    @Test
    void generate_withoutRefund_omitsRefundSection() throws IOException {
        String text = extractText(generator.generate(fullModel(null)));

        assertThat(text).doesNotContain("REMBOURSEMENT");
    }

    @Test
    void generate_withProcessedRefund_includesRefundSection() throws IOException {
        ReceiptRefund refund = new ReceiptRefund(RefundStatus.PROCESSED, new BigDecimal("1000000.00"),
                Instant.parse("2026-09-05T09:00:00Z"), "XOF-REFUND-77");
        String text = extractText(generator.generate(fullModel(refund)));

        assertThat(text).contains("REMBOURSEMENT");
        assertThat(text).contains("Traite");
        assertThat(text).contains("XOF-REFUND-77");
    }

    @Test
    void generate_withRejectedRefund_representsRejectionFaithfully() throws IOException {
        ReceiptRefund refund = new ReceiptRefund(RefundStatus.REJECTED, new BigDecimal("1000000.00"),
                Instant.parse("2026-09-05T09:00:00Z"), null);
        String text = extractText(generator.generate(fullModel(refund)));

        assertThat(text).contains("REMBOURSEMENT");
        assertThat(text).contains("Rejete");
        // La section TRANSFERT reste presente et identique -- le remboursement rejete ne doit
        // jamais laisser croire que le transfert initial a ete annule.
        assertThat(text).contains("987 500.00");
    }

    @Test
    void formatAmount_appliesAsciiThousandsGroupingAndTwoDecimals() {
        assertThat(PdfBoxReceiptPdfGenerator.formatAmount(new BigDecimal("1000000")))
                .isEqualTo("1 000 000.00");
        assertThat(PdfBoxReceiptPdfGenerator.formatAmount(new BigDecimal("84.2")))
                .isEqualTo("84.20");
        assertThat(PdfBoxReceiptPdfGenerator.formatAmount(new BigDecimal("999")))
                .isEqualTo("999.00");
        assertThat(PdfBoxReceiptPdfGenerator.formatAmount(new BigDecimal("-1234.5")))
                .isEqualTo("-1 234.50");
    }
}
