package com.converter.preferredrate.dto;

import com.converter.preferredrate.domain.PreferredRateDirection;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreatePreferredRateRequest(
        @NotNull PreferredRateDirection direction,
        @NotNull @DecimalMin(value = "0.01", message = "Le montant doit etre strictement positif.")
        @Digits(integer = 17, fraction = 2, message = "Le montant depasse la precision autorisee.")
        BigDecimal amountXof,
        @NotNull @DecimalMin(value = "0.000001", message = "Le taux cible doit etre strictement positif.") BigDecimal targetRate
) {
}
