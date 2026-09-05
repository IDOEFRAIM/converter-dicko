package com.converter.order.receipt.model;

import com.converter.order.domain.OrderStatus;
import com.converter.supplier.domain.Purpose;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Modele interne dedie au justificatif de transaction — jamais expose tel quel via une API JSON,
 * uniquement consomme par {@code ReceiptPdfGenerator}. Chaque champ financier est une copie directe
 * d'une valeur deja figee ({@code Order}/{@code Payment}/{@code Settlement}), <b>jamais</b> un
 * recalcul : voir la Javadoc de {@code OrderReceiptService} pour la garantie structurelle
 * (aucune dependance vers {@code RateEngine}/{@code SettingsService}/tout pricing courant).
 */
public record TransferReceiptModel(
        UUID orderId,
        String transactionReference,
        Instant createdAt,
        Instant completedAt,
        OrderStatus orderStatus,
        String customerName,
        BigDecimal amountXof,
        BigDecimal feeXof,
        BigDecimal netAmountXof,
        BigDecimal customerRate,
        BigDecimal amountCny,
        String currencyPair,
        ReceiptBeneficiary beneficiary,
        Purpose purpose,
        String purposeDetails,
        String paymentReference,
        Instant paymentVerifiedAt,
        String settlementReference,
        Instant settlementExecutedAt,
        /** {@code null} si aucun {@code Refund} n'existe pour cet ordre. */
        ReceiptRefund refund
) {
}
