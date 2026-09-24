package com.converter.refund.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Le montant n'est volontairement pas un champ de cette requete : il est toujours exactement
 * {@code Payment.receivedAmountXof} (voir {@code RefundService}) — le modele actuel ne connait
 * ni paiement partiel ni remboursement partiel, un admin ne doit donc jamais pouvoir saisir un
 * montant different de celui reellement recu.
 */
@Schema(description = "Creation d'un remboursement pour un paiement confirme")
public record CreateRefundRequest(

        @NotBlank(message = "Le motif du remboursement est obligatoire")
        @Size(max = 500)
        String reason
) {
}
