package com.converter.order.proforma.model;

import com.converter.order.domain.OrderStatus;
import com.converter.order.receipt.model.ReceiptBeneficiary;
import com.converter.supplier.domain.Purpose;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Modele interne d'une facture proforma (remarque produit #3) — jamais expose en JSON, uniquement
 * consomme par {@code ProformaPdfGenerator}. Meme garantie structurelle que
 * {@code com.converter.order.receipt} : chaque valeur financiere est une copie directe d'une
 * colonne deja figee d'{@code Order} (snapshot du {@code Quote}), <b>jamais</b> un recalcul de
 * pricing. Le beneficiaire vient du snapshot {@code Beneficiary} de l'ordre, jamais du
 * {@code Supplier} actuel.
 *
 * <p>Une proforma n'a <b>aucune valeur d'acquittement</b> : c'est une piece descriptive emise
 * avant paiement (justification bancaire / dedouanement pour l'acheteur). Disponible des la
 * creation de l'ordre, contrairement au justificatif reserve a {@code COMPLETED}.
 */
public record ProformaInvoiceModel(
        String invoiceNumber,
        Instant issuedAt,
        String orderReference,
        OrderStatus orderStatus,
        String buyerName,
        /** Raison sociale de l'acheteur si un {@code BusinessProfile} existe, sinon {@code null}. */
        String buyerBusinessName,
        String buyerRegistrationNumber,
        String buyerAddress,
        BigDecimal amountXof,
        BigDecimal feeXof,
        BigDecimal netAmountXof,
        BigDecimal customerRate,
        BigDecimal amountCny,
        String currencyPair,
        ReceiptBeneficiary beneficiary,
        Purpose purpose,
        String purposeDetails
) {
}
