package com.converter.order.receipt.model;

import com.converter.refund.domain.RefundStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Section optionnelle du recu (absente si aucun {@code Refund} n'existe pour l'ordre). Represente
 * fidelement le remboursement tel qu'enregistre, y compris {@code REJECTED} : ne transforme jamais
 * "transfert termine" en "transfert annule" — le transfert initial reste un fait historique
 * distinct, jamais reecrit par cette section (voir {@code Refund}, section "Ce qu'un Refund n'est
 * PAS").
 */
public record ReceiptRefund(
        RefundStatus status,
        BigDecimal amountXof,
        Instant date,
        String reference
) {
}
