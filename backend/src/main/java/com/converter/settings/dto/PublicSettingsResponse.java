package com.converter.settings.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

/**
 * Parametres necessaires au frontend avant toute authentification.
 *
 * <p>Exposer les bornes permet au client d'afficher un message d'erreur
 * immediat. La verification reste integralement refaite cote serveur :
 * cette reponse est un confort d'interface, jamais un controle.
 */
@Schema(description = "Parametres metier publics")
public record PublicSettingsResponse(

        @Schema(description = "Montant minimum d'un ordre, en CFA", example = "10000")
        BigDecimal minOrderAmountCfa,

        @Schema(description = "Montant maximum d'un ordre, en CFA", example = "2000000")
        BigDecimal maxOrderAmountCfa,

        @Schema(description = "Duree du verrouillage de taux, en minutes", example = "30")
        int rateLockDurationMinutes,

        @Schema(description = "Taille maximale d'une preuve de paiement, en octets")
        long maxProofFileSizeBytes,

        @Schema(description = "Nombre maximal de preuves par paiement")
        int maxProofsPerPayment,

        @Schema(description = "Moyens de paiement actuellement actifs")
        List<String> enabledPaymentMethods,

        @Schema(description = "Vrai si une preuve de paiement est exigee")
        boolean requirePaymentProof,

        @Schema(description = "Instructions affichees au client pour savoir ou/comment envoyer son paiement",
                example = "Envoyez le montant indique par Mobile Money au +226 71 00 25 25, puis declarez votre paiement ci-dessous.")
        String paymentInstructionsText
) {
}
