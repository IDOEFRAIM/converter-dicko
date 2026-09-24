package com.converter.quote.dto;

import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verrou de confidentialite structurel : {@link QuoteResponse} est le
 * seul DTO que {@code QuoteController} renvoie a un utilisateur
 * standard. Ce test echoue si un futur commit y ajoute un champ dont le
 * nom laisse deviner {@code breakEvenRate} ou {@code marginPercentage}
 * — la protection ne repose alors plus sur "personne n'y a pense", mais
 * sur un test qui casse a la compilation logique du build.
 */
class QuoteResponseTest {

    private static final List<String> FORBIDDEN_SUBSTRINGS = List.of(
            "breakeven", "basereate", "baserate", "marketrate", "marginpercentage", "costconfiguration");

    @Test
    void neverExposesInternalCostOrMarginFields() {
        RecordComponent[] components = QuoteResponse.class.getRecordComponents();

        List<String> componentNames = Arrays.stream(components)
                .map(RecordComponent::getName)
                .toList();

        for (String name : componentNames) {
            String lower = name.toLowerCase(Locale.ROOT);
            assertThat(FORBIDDEN_SUBSTRINGS.stream().anyMatch(lower::contains))
                    .as("QuoteResponse.%s ne doit jamais exposer une donnee de cout/marge interne", name)
                    .isFalse();
        }

        // Verifie explicitement que seuls les champs commercialement publics attendus sont presents.
        // poolRewardApplied (Lot 3) est un benefice du client lui-meme (reduction de Ruee collective
        // appliquee a SON devis), jamais une donnee de cout/marge interne : legitimement expose.
        assertThat(componentNames).containsExactly(
                "id", "direction", "amountXof", "amountCny", "customerRate",
                "feeXof", "netAmountXof", "status", "createdAt", "expiresAt",
                "poolRewardApplied");
    }
}
