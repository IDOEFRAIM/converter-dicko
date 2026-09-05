package com.converter.rate.alert.dto;

import com.converter.preferredrate.domain.PreferredRateDirection;
import com.converter.rate.alert.domain.RateComparison;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * {@code currencyPair}, {@code direction} et {@code comparison} sont optionnels : ils se
 * deduisent aujourd'hui sans ambiguite de la configuration actuelle du produit (une seule paire,
 * un seul sens, un seul type de comparaison reellement utilise — voir {@code RateAlertService}),
 * et sont donc defaults plutot qu'imposes au client. {@code targetRate} reste, lui, une decision
 * strictement utilisateur : jamais deduit. {@code expiresAt} est optionnel — {@code null} =
 * aucune expiration ; si fourni, doit etre strictement future (verifie en service, pas ici : la
 * comparaison depend de l'horloge applicative, jamais de l'horloge du serveur de validation).
 */
public record CreateRateAlertRequest(
        String currencyPair,
        PreferredRateDirection direction,
        @NotNull @DecimalMin(value = "0.000001", message = "Le taux cible doit etre strictement positif.")
        BigDecimal targetRate,
        RateComparison comparison,
        Instant expiresAt
) {
}
