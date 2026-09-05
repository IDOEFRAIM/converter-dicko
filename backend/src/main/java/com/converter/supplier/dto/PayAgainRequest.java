package com.converter.supplier.dto;

import com.converter.supplier.domain.Purpose;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Nouveau paiement vers un fournisseur deja enregistre.
 *
 * <p><b>Le montant n'est jamais deduit du fournisseur ni d'une transaction passee</b> : le
 * fournisseur ne fournit que l'identite du beneficiaire et ses coordonnees de reglement,
 * jamais une verite financiere. {@code amountXof} doit toujours etre explicitement soumis par
 * le client — voir {@code RepeatPaymentService}, qui construit un {@code CreateQuoteRequest}
 * ordinaire a partir de ce montant et du pricing courant, exactement comme un premier paiement.
 */
@Schema(description = "Nouveau paiement vers un fournisseur deja enregistre — nouveau devis, nouvel ordre, jamais une copie d'une transaction passee")
public record PayAgainRequest(

        @NotNull(message = "Le montant XOF est obligatoire")
        @Positive(message = "Le montant XOF doit etre strictement positif")
        @Digits(integer = 17, fraction = 2, message = "Le montant XOF depasse la precision autorisee")
        @Schema(description = "Montant XOF a envoyer, explicitement valide par le client — jamais repris d'un ordre precedent")
        BigDecimal amountXof,

        @Schema(description = "Motif du transfert. Si omis, le motif par defaut du fournisseur (s'il en a un) est utilise.")
        Purpose purpose,

        @Size(max = 500)
        String purposeDetails
) {
}
