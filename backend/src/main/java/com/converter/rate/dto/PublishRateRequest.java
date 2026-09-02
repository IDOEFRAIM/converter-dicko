package com.converter.rate.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Publication d'un nouveau taux manuel.
 *
 * <p>La paire de devises n'est volontairement pas un champ de cette
 * requete : le MVP ne supporte que XOF/CNY, fixe cote serveur. L'ajout
 * d'autres paires n'exigera pas de changement de contrat sur cet
 * endpoint (juste un champ optionnel a ajouter plus tard).
 */
@Schema(description = "Publication d'un nouveau taux manuel courant")
public record PublishRateRequest(

        @NotNull(message = "Le taux est obligatoire")
        @DecimalMin(value = "0.000001", message = "Le taux doit etre strictement positif")
        @Schema(description = "Taux de marche, convention 1 CNY = X XOF", example = "85.000000")
        BigDecimal cfaPerCny,

        @Size(max = 500, message = "La note ne peut depasser 500 caracteres")
        @Schema(description = "Note libre, ex. source de la cotation")
        String note
) {
}
