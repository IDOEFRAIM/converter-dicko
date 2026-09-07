package com.converter.pool.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Creation d'une Ruee collective. {@code durationMinutes} est volontairement borne (5 a 180) :
 * ni un defi instantane sans temps de rallier des amis, ni une "Ruee" qui dure des jours et perd
 * tout son caractere d'urgence collective.
 */
public record CreatePoolRequest(
        @NotNull(message = "L'objectif de volume est obligatoire")
        @DecimalMin(value = "1000", message = "L'objectif doit etre d'au moins 1000 XOF")
        @Digits(integer = 17, fraction = 2, message = "L'objectif depasse la precision autorisee")
        BigDecimal targetAmountXof,

        @NotNull(message = "La duree est obligatoire")
        @Min(value = 5, message = "La duree minimale est de 5 minutes")
        @Max(value = 180, message = "La duree maximale est de 180 minutes")
        Integer durationMinutes
) {
}
