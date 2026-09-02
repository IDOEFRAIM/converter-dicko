package com.converter.quote.dto;

import com.converter.quote.domain.QuoteDirection;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Intention du client : soit "j'envoie X XOF", soit "je veux que le
 * beneficiaire recoive Y CNY" — jamais les deux, jamais aucun taux ni
 * frais. Ces valeurs sont exclusivement calculees par le backend ;
 * fournir {@code amountXof} avec {@code direction = RECEIVE_CNY} (ou
 * l'inverse) est rejete (voir {@code QuoteService}).
 */
@Schema(description = "Demande de devis : une seule des deux valeurs de montant selon le sens choisi")
public record CreateQuoteRequest(

        @NotNull(message = "Le sens du devis est obligatoire")
        @Schema(description = "SEND_XOF : amountXof connu. RECEIVE_CNY : amountCny connu.")
        QuoteDirection direction,

        @Positive(message = "Le montant XOF doit etre strictement positif")
        @Schema(description = "Montant XOF que le client paie — requis uniquement pour SEND_XOF", example = "100000")
        BigDecimal amountXof,

        @Positive(message = "Le montant CNY doit etre strictement positif")
        @Schema(description = "Montant CNY que le beneficiaire doit recevoir — requis uniquement pour RECEIVE_CNY")
        BigDecimal amountCny
) {
}
