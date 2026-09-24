package com.converter.rate.cost.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Publication des parametres de cout du jour pour la chaine
 * XOF -&gt; USD -&gt; CNY, et du montant de reference utilise pour en
 * deriver le {@code breakEvenRate}.
 */
@Schema(description = "Parametres de cout de revient XOF -> USD -> CNY pour une journee donnee")
public record PublishCostRateConfigurationRequest(

        @NotNull(message = "La date metier est obligatoire")
        @PastOrPresent(message = "La date metier ne peut pas etre future")
        @Schema(description = "Journee a laquelle ces parametres s'appliquent", example = "2026-09-02")
        LocalDate businessDate,

        @NotNull(message = "rateXofUsd est obligatoire")
        @DecimalMin(value = "0.000001", message = "rateXofUsd doit etre strictement positif")
        @Schema(description = "Taux XOF pour 1 USD", example = "583")
        BigDecimal rateXofUsd,

        @NotNull(message = "rateUsdCny est obligatoire")
        @DecimalMin(value = "0.000001", message = "rateUsdCny doit etre strictement positif")
        @Schema(description = "Taux CNY pour 1 USD", example = "6.70")
        BigDecimal rateUsdCny,

        @NotNull(message = "feeXofUsdPercent est obligatoire")
        @DecimalMin(value = "0", message = "feeXofUsdPercent ne peut pas etre negatif")
        @DecimalMax(value = "1", inclusive = false, message = "feeXofUsdPercent doit rester strictement inferieur a 1 (fraction, 0.01 = 1 %)")
        @Schema(description = "Frais proportionnels XOF -> USD, en fraction decimale (0.01 = 1 %)", example = "0.01")
        BigDecimal feeXofUsdPercent,

        @NotNull(message = "feeUsdCnyFixedUsd est obligatoire")
        @DecimalMin(value = "0", message = "feeUsdCnyFixedUsd ne peut pas etre negatif")
        @Schema(description = "Frais fixe USD -> CNY, en USD", example = "1.50")
        BigDecimal feeUsdCnyFixedUsd,

        @NotNull(message = "referenceAmountXof est obligatoire")
        @DecimalMin(value = "0.01", message = "referenceAmountXof doit etre strictement positif")
        @Schema(description = "Montant XOF de reference utilise pour calculer le breakEvenRate", example = "1000000")
        BigDecimal referenceAmountXof,

        @Size(max = 500, message = "La note ne peut depasser 500 caracteres")
        @Schema(description = "Note libre, ex. source des taux XOF/USD et USD/CNY")
        String note
) {
}
