package com.converter.order.dto;

import com.converter.supplier.domain.Purpose;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Creation d'un ordre a partir d'un devis deja accepte.
 *
 * <p>Exactement l'un de {@code beneficiary} ou {@code supplierId} doit etre fourni (verifie par
 * {@code OrderService}, pas par une annotation — meme principe que {@code CreateQuoteRequest}
 * pour le choix XOF/CNY) : soit un beneficiaire saisi directement, soit un fournisseur deja
 * enregistre (voir {@code com.converter.supplier}), dont les coordonnees sont alors copiees dans
 * le meme snapshot immuable {@code Beneficiary} qu'auparavant — {@code supplierId} lui-meme n'est
 * jamais qu'une reference tracable sur l'ordre, jamais relu pour reconstruire ce snapshot.
 */
@Schema(description = "Creation d'un ordre a partir d'un devis deja accepte")
public record CreateOrderRequest(

        @NotNull(message = "L'identifiant du devis est obligatoire")
        UUID quoteId,

        @Valid
        @Schema(description = "Beneficiaire saisi directement — omis si supplierId est fourni")
        BeneficiaryRequest beneficiary,

        @Size(max = 500)
        String note,

        @Schema(description = "Fournisseur deja enregistre a utiliser — omis si beneficiary est fourni")
        UUID supplierId,

        @Schema(description = "Motif du transfert, optionnel")
        Purpose purpose,

        @Size(max = 500)
        String purposeDetails
) {
}
